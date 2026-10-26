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
        <div class="card-head">
          <h3 class="card-title">按项目实收金额</h3>
          <span class="card-hint">按金额降序，取前 10 项</span>
        </div>
        <div ref="itemRef" class="chart"></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import * as echarts from 'echarts'
import * as statsApi from '@/api/stats'
import { formatAmount } from '@/utils/format'
import type { ItemStat, OverviewStat, TrendPoint } from '@/types'

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

/**
 * 横向条形图：项目名称放在纵轴，长名称不会互相挤压；
 * 金额差异大时（如个别项目上千、其余几十），柱尾直接标数值，避免小柱看不清。
 */
function renderItems(items: ItemStat[]): void {
  if (!itemRef.value) {
    return
  }
  itemChart = itemChart ?? echarts.init(itemRef.value)
  const sorted = [...items].sort((a, b) => Number(b.received) - Number(a.received))
  itemChart.setOption({
    grid: { left: 8, right: 74, top: 8, bottom: 8, containLabel: true },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: any) => {
        const p = Array.isArray(params) ? params[0] : params
        return `${p.name}<br/>实收金额：¥${formatAmount(p.value)}`
      }
    },
    xAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#f0f2f5' } },
      axisLabel: { color: '#909399' }
    },
    yAxis: {
      type: 'category',
      data: sorted.map((i) => i.itemName),
      inverse: true,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#606266', fontSize: 11, width: 148, overflow: 'truncate' }
    },
    series: [
      {
        name: '实收金额',
        type: 'bar',
        barMaxWidth: 18,
        data: sorted.map((i) => Number(i.received)),
        itemStyle: { color: '#5b9bff', borderRadius: [0, 4, 4, 0] },
        label: {
          show: true,
          position: 'right',
          color: '#606266',
          fontSize: 12,
          formatter: (p: any) => formatAmount(p.value)
        }
      }
    ]
  })
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const [ov, points, items] = await Promise.all([statsApi.overview(), statsApi.trend(), statsApi.byItem()])
    overview.value = ov
    await nextTick()
    renderTrend(points)
    renderItems(items.slice(0, 10))
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
  align-items: baseline;
  justify-content: space-between;
}

.card-hint {
  font-size: 12px;
  color: #909399;
}
</style>
