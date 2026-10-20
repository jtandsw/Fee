<template>
  <div class="page">
    <div class="stat-grid" v-loading="loading">
      <div class="stat-card">
        <div class="stat-label">应收总额（元）</div>
        <div class="stat-value">{{ formatAmount(overview.receivable) }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">实收总额（元）</div>
        <div class="stat-value success">{{ formatAmount(overview.received) }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">欠费金额（元）</div>
        <div class="stat-value danger">{{ formatAmount(overview.outstanding) }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">缴费完成率</div>
        <div class="stat-value primary">{{ overview.payRate }}%</div>
        <el-progress :percentage="Math.min(Number(overview.payRate) || 0, 100)" :show-text="false" class="rate" />
      </div>
    </div>

    <div class="charts">
      <div class="card">
        <h3 class="card-title">近 30 天缴费趋势</h3>
        <div ref="trendRef" class="chart"></div>
      </div>
      <div class="card">
        <h3 class="card-title">按项目实收金额</h3>
        <div ref="itemRef" class="chart"></div>
      </div>
    </div>

    <div class="card">
      <div class="card-head">
        <h3 class="card-title">最近订单</h3>
        <el-link type="primary" @click="router.push('/finance/orders')">查看全部</el-link>
      </div>
      <el-table :data="orders" size="small" empty-text="暂无订单">
        <el-table-column prop="orderNo" label="订单号" min-width="180" />
        <el-table-column prop="studentName" label="学生" width="100" />
        <el-table-column prop="itemName" label="缴费项目" min-width="180" show-overflow-tooltip />
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }"><MoneyText :amount="row.amount" /></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><StatusTag :status="row.status" /></template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import MoneyText from '@/components/MoneyText.vue'
import StatusTag from '@/components/StatusTag.vue'
import * as statsApi from '@/api/stats'
import * as orderApi from '@/api/order'
import { formatAmount, formatDateTime } from '@/utils/format'
import type { ItemStat, Order, OverviewStat, TrendPoint } from '@/types'

const router = useRouter()
const loading = ref(false)
const overview = ref<OverviewStat>({
  receivable: '0.00',
  received: '0.00',
  outstanding: '0.00',
  payRate: '0.00',
  orderCount: 0,
  paidCount: 0,
  studentCount: 0
})
const orders = ref<Order[]>([])
const trendRef = ref<HTMLDivElement>()
const itemRef = ref<HTMLDivElement>()
let trendChart: echarts.ECharts | null = null
let itemChart: echarts.ECharts | null = null

function renderTrend(points: TrendPoint[]): void {
  if (!trendRef.value) {
    return
  }
  trendChart = trendChart ?? echarts.init(trendRef.value)
  trendChart.setOption({
    grid: { left: 50, right: 20, top: 30, bottom: 40 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: points.map((p) => p.statDate),
      axisLine: { lineStyle: { color: '#dcdfe6' } },
      axisLabel: { color: '#909399' }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#f0f2f5' } },
      axisLabel: { color: '#909399' }
    },
    series: [
      {
        name: '实收金额',
        type: 'line',
        smooth: true,
        areaStyle: { opacity: 0.12 },
        symbolSize: 6,
        data: points.map((p) => Number(p.amount)),
        itemStyle: { color: '#2f6fed' }
      }
    ]
  })
}

function renderItems(items: ItemStat[]): void {
  if (!itemRef.value) {
    return
  }
  itemChart = itemChart ?? echarts.init(itemRef.value)
  itemChart.setOption({
    grid: { left: 50, right: 20, top: 30, bottom: 60 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: items.map((i) => i.itemName),
      axisLine: { lineStyle: { color: '#dcdfe6' } },
      axisLabel: { color: '#909399', interval: 0, width: 90, overflow: 'truncate' }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#f0f2f5' } },
      axisLabel: { color: '#909399' }
    },
    series: [
      {
        name: '实收金额',
        type: 'bar',
        barMaxWidth: 36,
        data: items.map((i) => Number(i.received)),
        itemStyle: { color: '#5b9bff', borderRadius: [6, 6, 0, 0] }
      }
    ]
  })
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const [ov, points, items, page] = await Promise.all([
      statsApi.overview(),
      statsApi.trend(),
      statsApi.byItem(),
      orderApi.pageOrders({ pageNum: 1, pageSize: 8 })
    ])
    overview.value = ov
    orders.value = page.list
    await nextTick()
    renderTrend(points)
    renderItems(items.slice(0, 8))
  } finally {
    loading.value = false
  }
}

function resize(): void {
  trendChart?.resize()
  itemChart?.resize()
}

onMounted(() => {
  load()
  window.addEventListener('resize', resize)
})

onUnmounted(() => {
  window.removeEventListener('resize', resize)
  trendChart?.dispose()
  itemChart?.dispose()
})
</script>

<style scoped>
.rate {
  margin-top: 10px;
}

.charts {
  display: grid;
  grid-template-columns: 1.3fr 1fr;
  gap: 16px;
}

@media (max-width: 1100px) {
  .charts {
    grid-template-columns: 1fr;
  }
}

.chart {
  height: 300px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
