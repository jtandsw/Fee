<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 150px" @change="search">
          <el-option v-for="(meta, key) in ORDER_STATUS" :key="key" :label="meta.label" :value="key" />
        </el-select>
        <el-input
          v-model="query.keyword"
          placeholder="订单号 / 项目名称"
          clearable
          style="width: 240px"
          @keyup.enter="search"
        />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>
    </div>

    <div class="card">
      <el-table v-loading="loading" :data="list" empty-text="暂无订单" stripe>
        <el-table-column prop="orderNo" label="订单号" min-width="190" />
        <el-table-column prop="itemName" label="缴费项目" min-width="200" show-overflow-tooltip />
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }"><MoneyText :amount="row.amount" /></template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StatusTag :status="row.status" /></template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" link type="primary" @click="goPay(row as Order)">去支付</el-button>
            <el-button v-if="row.status === 'PENDING'" link type="danger" @click="close(row as Order)">关闭</el-button>
            <el-button v-if="row.status === 'PAID' || row.status === 'PARTIAL_REFUND'" link type="primary" @click="voucher(row as Order)">
              下载凭证
            </el-button>
            <el-button link @click="detail(row as Order)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="load"
          @size-change="search"
        />
      </div>
    </div>

    <el-drawer v-model="detailVisible" title="订单详情" size="480px">
      <el-descriptions v-if="activeOrder" :column="1" border>
        <el-descriptions-item label="订单号">{{ activeOrder.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="缴费项目">{{ activeOrder.itemName }}</el-descriptions-item>
        <el-descriptions-item label="金额"><MoneyText :amount="activeOrder.amount" /></el-descriptions-item>
        <el-descriptions-item label="实缴金额"><MoneyText :amount="activeOrder.paidAmount" /></el-descriptions-item>
        <el-descriptions-item label="状态"><StatusTag :status="activeOrder.status" /></el-descriptions-item>
        <el-descriptions-item label="渠道">{{ CHANNEL_LABEL[activeOrder.channel] || activeOrder.channel }}</el-descriptions-item>
        <el-descriptions-item label="渠道流水号">{{ activeOrder.transactionNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateTime(activeOrder.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="支付时间">{{ formatDateTime(activeOrder.paidAt) }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import StatusTag from '@/components/StatusTag.vue'
import MoneyText from '@/components/MoneyText.vue'
import * as orderApi from '@/api/order'
import { downloadBlob, formatDateTime } from '@/utils/format'
import { CHANNEL_LABEL, ORDER_STATUS } from '@/utils/status'
import type { Order } from '@/types'

const router = useRouter()
const loading = ref(false)
const list = ref<Order[]>([])
const total = ref(0)
const detailVisible = ref(false)
const activeOrder = ref<Order | null>(null)

const query = reactive({
  status: '',
  keyword: '',
  pageNum: 1,
  pageSize: 10
})

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await orderApi.pageOrders({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      status: query.status || undefined,
      keyword: query.keyword || undefined
    })
    list.value = page.list
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function search(): void {
  query.pageNum = 1
  load()
}

function reset(): void {
  query.status = ''
  query.keyword = ''
  query.pageNum = 1
  load()
}

function goPay(row: Order): void {
  router.push({ path: '/student/cashier', query: { orderNo: row.orderNo } })
}

async function close(row: Order): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定关闭订单 ${row.orderNo} 吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  await orderApi.closeOrder(row.orderNo)
  ElMessage.success('订单已关闭')
  load()
}

async function voucher(row: Order): Promise<void> {
  const blob = await orderApi.getVoucher(row.orderNo)
  // 直接下载到本地，而不是新开标签预览（浏览器默认可能拦截弹窗，用户也拿不到文件）
  downloadBlob(blob, `缴费凭证-${row.orderNo}.html`)
  ElMessage.success('凭证已下载到本地')
}

function detail(row: Order): void {
  activeOrder.value = row
  detailVisible.value = true
}

onMounted(load)
</script>
