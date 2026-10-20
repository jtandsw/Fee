<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <div>
          <h3 class="card-title">统计报表</h3>
          <p class="muted">应收 = 未关闭订单应缴金额；实收 = 已支付/部分退款订单实缴金额</p>
        </div>
        <div class="toolbar-right">
          <el-button :icon="Download" :loading="exporting" @click="exportExcel">导出 Excel</el-button>
          <el-button :icon="Refresh" @click="loadAll">刷新</el-button>
        </div>
      </div>
    </div>

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
        <div class="stat-label">订单数 / 已支付</div>
        <div class="stat-value">{{ overview.orderCount }} / {{ overview.paidCount }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">涉及缴费学生</div>
        <div class="stat-value">{{ overview.studentCount }}</div>
      </div>
    </div>

    <div class="card">
      <div class="card-head">
        <h3 class="card-title">缴费趋势</h3>
        <div class="toolbar">
          <el-radio-group v-model="granularity" size="small" @change="loadTrend">
            <el-radio-button label="day">按日</el-radio-button>
            <el-radio-button label="month">按月</el-radio-button>
          </el-radio-group>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            size="small"
            style="width: 240px"
            @change="loadTrend"
          />
        </div>
      </div>
      <div ref="trendRef" class="chart"></div>
    </div>

    <div class="grid-two">
      <div class="card">
        <h3 class="card-title">按项目统计</h3>
        <el-table :data="itemStats" size="small" empty-text="暂无数据" max-height="360">
          <el-table-column prop="itemName" label="项目" min-width="160" show-overflow-tooltip />
          <el-table-column label="应收" width="110" align="right">
            <template #default="{ row }">{{ formatAmount(row.receivable) }}</template>
          </el-table-column>
          <el-table-column label="实收" width="110" align="right">
            <template #default="{ row }">{{ formatAmount(row.received) }}</template>
          </el-table-column>
          <el-table-column prop="paidCount" label="已付/订单" width="100" align="right">
            <template #default="{ row }">{{ row.paidCount }} / {{ row.orderCount }}</template>
          </el-table-column>
        </el-table>
      </div>

      <div class="card">
        <h3 class="card-title">按学院实收</h3>
        <div ref="collegeRef" class="chart small"></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Refresh } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import * as statsApi from '@/api/stats'
import { downloadBlob, formatAmount } from '@/utils/format'
import type { CollegeStat, ItemStat, OverviewStat, TrendPoint } from '@/types'

const loading = ref(false)
const exporting = ref(false)
const granularity = ref<'day' | 'month'>('day')
const dateRange = ref<[string, string] | null>(null)
const overview = ref<OverviewStat>({
  receivable: '0.00',
  received: '0.00',
  outstanding: '0.00',
  payRate: '0.00',
  orderCount: 0,
  paidCount: 0,
  studentCount: 0
})
const itemStats = ref<ItemStat[]>([])
const collegeStats = ref<CollegeStat[]>([])
const trendRef = ref<HTMLDivElement>()
const collegeRef = ref<HTMLDivElement>()
let trendChart: echarts.ECharts | null = null
let collegeChart: echarts.ECharts | null = null

function renderTrend(points: TrendPoint[]): void {
  if (!trendRef.value) {
    return
  }
  trendChart = trendChart ?? echarts.init(trendRef.value)
  trendChart.setOption(
    {
      grid: { left: 55, right: 24, top: 30, bottom: 40 },
      tooltip: { trigger: 'axis' },
      legend: { data: ['实收金额', '订单数'], right: 10, textStyle: { color: '#606266' } },
      xAxis: {
        type: 'category',
        data: points.map((p) => p.statDate),
        axisLine: { lineStyle: { color: '#dcdfe6' } },
        axisLabel: { color: '#909399' }
      },
      yAxis: [
        {
          type: 'value',
          name: '金额',
          splitLine: { lineStyle: { color: '#f0f2f5' } },
          axisLabel: { color: '#909399' }
        },
        {
          type: 'value',
          name: '笔数',
          splitLine: { show: false },
          axisLabel: { color: '#909399' }
        }
      ],
      series: [
        {
          name: '实收金额',
          type: 'bar',
          barMaxWidth: 28,
          data: points.map((p) => Number(p.amount)),
          itemStyle: { color: '#5b9bff', borderRadius: [6, 6, 0, 0] }
        },
        {
          name: '订单数',
          type: 'line',
          yAxisIndex: 1,
          smooth: true,
          symbolSize: 6,
          data: points.map((p) => p.count),
          itemStyle: { color: '#2e9e5b' }
        }
      ]
    },
    true
  )
}

function renderCollege(): void {
  if (!collegeRef.value) {
    return
  }
  collegeChart = collegeChart ?? echarts.init(collegeRef.value)
  collegeChart.setOption(
    {
      grid: { left: 100, right: 30, top: 20, bottom: 30 },
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: '#f0f2f5' } },
        axisLabel: { color: '#909399' }
      },
      yAxis: {
        type: 'category',
        data: collegeStats.value.map((c) => c.college),
        axisLine: { lineStyle: { color: '#dcdfe6' } },
        axisLabel: { color: '#606266' }
      },
      series: [
        {
          name: '实收金额',
          type: 'bar',
          barMaxWidth: 22,
          data: collegeStats.value.map((c) => Number(c.received)),
          itemStyle: { color: '#2f6fed', borderRadius: [0, 6, 6, 0] }
        }
      ]
    },
    true
  )
}

async function loadTrend(): Promise<void> {
  const [start, end] = dateRange.value ?? [undefined, undefined]
  const points = await statsApi.trend({
    start: start || undefined,
    end: end || undefined,
    granularity: granularity.value
  })
  await nextTick()
  renderTrend(points)
}

async function loadAll(): Promise<void> {
  loading.value = true
  try {
    const [ov, items, colleges] = await Promise.all([
      statsApi.overview(),
      statsApi.byItem(),
      statsApi.byCollege()
    ])
    overview.value = ov
    itemStats.value = items
    collegeStats.value = colleges
    await loadTrend()
    renderCollege()
  } finally {
    loading.value = false
  }
}

async function exportExcel(): Promise<void> {
  exporting.value = true
  try {
    const blob = await statsApi.exportStats()
    downloadBlob(blob, `缴费统计-${new Date().toISOString().slice(0, 10)}.xlsx`)
    ElMessage.success('导出成功')
  } finally {
    exporting.value = false
  }
}

function resize(): void {
  trendChart?.resize()
  collegeChart?.resize()
}

onMounted(() => {
  loadAll()
  window.addEventListener('resize', resize)
})

onUnmounted(() => {
  window.removeEventListener('resize', resize)
  trendChart?.dispose()
  collegeChart?.dispose()
})
</script>

<style scoped>
.chart {
  height: 320px;
}

.chart.small {
  height: 300px;
}

.grid-two {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

@media (max-width: 1100px) {
  .grid-two {
    grid-template-columns: 1fr;
  }
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
</style>
