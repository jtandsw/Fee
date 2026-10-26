<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.studentNo" placeholder="学号" clearable style="width: 150px" @keyup.enter="search" />
        <el-input v-model="query.keyword" placeholder="订单号 / 姓名 / 流水号" clearable style="width: 230px" @keyup.enter="search" />
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px" @change="search">
          <el-option v-for="(meta, key) in ORDER_STATUS" :key="key" :label="meta.label" :value="key" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>
    </div>

    <div class="card">
      <el-table v-loading="loading" :data="list" empty-text="暂无订单" stripe>
        <el-table-column prop="orderNo" label="订单号" min-width="215" />
        <el-table-column label="学生" width="140">
          <template #default="{ row }">
            <div>{{ row.studentName }}</div>
            <div class="muted">{{ row.studentNo }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="itemName" label="缴费项目" min-width="180" show-overflow-tooltip />
        <el-table-column label="应缴 / 实缴" width="140" align="right">
          <template #default="{ row }">
            <div><MoneyText :amount="row.amount" /></div>
            <div class="muted">实缴 {{ formatAmount(row.paidAmount) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="渠道" width="100">
          <template #default="{ row }">{{ CHANNEL_LABEL[row.channel] || row.channel }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StatusTag :status="row.status" /></template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link @click="showDetail(row as Order)">详情</el-button>
            <el-button v-if="row.status === 'PAID' || row.status === 'PARTIAL_REFUND'" link type="warning" @click="openRefund(row as Order)">
              退款
            </el-button>
            <el-button v-if="row.status === 'PAID'" link type="primary" @click="voucher(row as Order)">凭证</el-button>
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

    <el-drawer v-model="detailVisible" title="订单详情" size="500px">
      <el-descriptions v-if="activeOrder" :column="1" border>
        <el-descriptions-item label="订单号">{{ activeOrder.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="学生">
          {{ activeOrder.studentName }}（{{ activeOrder.studentNo }}）
        </el-descriptions-item>
        <el-descriptions-item label="学院">{{ activeOrder.college }}</el-descriptions-item>
        <el-descriptions-item label="缴费项目">{{ activeOrder.itemName }}</el-descriptions-item>
        <el-descriptions-item label="应缴金额"><MoneyText :amount="activeOrder.amount" /></el-descriptions-item>
        <el-descriptions-item label="实缴金额"><MoneyText :amount="activeOrder.paidAmount" /></el-descriptions-item>
        <el-descriptions-item label="订单状态"><StatusTag :status="activeOrder.status" /></el-descriptions-item>
        <el-descriptions-item label="支付渠道">{{ CHANNEL_LABEL[activeOrder.channel] || activeOrder.channel }}</el-descriptions-item>
        <el-descriptions-item label="渠道流水号">{{ activeOrder.transactionNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateTime(activeOrder.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="支付时间">{{ formatDateTime(activeOrder.paidAt) }}</el-descriptions-item>
        <el-descriptions-item label="关闭时间">{{ formatDateTime(activeOrder.closedAt) }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>

    <el-dialog v-model="refundVisible" title="订单退款" width="480px">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="退款将通过模拟渠道立即完成，请核对金额与原因"
        class="refund-alert"
      />
      <el-form :model="refundForm" label-width="90px">
        <el-form-item label="订单号">{{ refundTarget?.orderNo }}</el-form-item>
        <el-form-item label="实缴金额"><MoneyText :amount="refundTarget?.paidAmount" /></el-form-item>
        <el-form-item label="退款金额" required>
          <el-input v-model="refundForm.amount" placeholder="如 5800.00">
            <template #prepend>￥</template>
          </el-input>
        </el-form-item>
        <el-form-item label="退款原因" required>
          <el-input v-model="refundForm.reason" type="textarea" :rows="3" maxlength="255" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="refundVisible = false">取消</el-button>
        <el-button type="warning" :loading="refunding" @click="submitRefund">确认退款</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import MoneyText from '@/components/MoneyText.vue'
import StatusTag from '@/components/StatusTag.vue'
import * as orderApi from '@/api/order'
import { downloadBlob, formatAmount, formatDateTime } from '@/utils/format'
import { CHANNEL_LABEL, ORDER_STATUS } from '@/utils/status'
import type { Order } from '@/types'

const loading = ref(false)
const list = ref<Order[]>([])
const total = ref(0)
const detailVisible = ref(false)
const activeOrder = ref<Order | null>(null)

const refundVisible = ref(false)
const refunding = ref(false)
const refundTarget = ref<Order | null>(null)
const refundForm = reactive({ amount: '', reason: '' })

const query = reactive({
  studentNo: '',
  keyword: '',
  status: '',
  pageNum: 1,
  pageSize: 10
})

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await orderApi.pageOrders({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      studentNo: query.studentNo.trim() || undefined,
      keyword: query.keyword || undefined,
      status: query.status || undefined
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
  query.studentNo = ''
  query.keyword = ''
  query.status = ''
  query.pageNum = 1
  load()
}

function showDetail(row: Order): void {
  activeOrder.value = row
  detailVisible.value = true
}

function openRefund(row: Order): void {
  refundTarget.value = row
  refundForm.amount = row.paidAmount
  refundForm.reason = ''
  refundVisible.value = true
}

async function submitRefund(): Promise<void> {
  if (!refundTarget.value) {
    return
  }
  if (!refundForm.amount || Number(refundForm.amount) <= 0) {
    ElMessage.warning('请输入正确的退款金额')
    return
  }
  if (!refundForm.reason.trim()) {
    ElMessage.warning('请填写退款原因')
    return
  }
  refunding.value = true
  try {
    await orderApi.refundOrder(refundTarget.value.orderNo, {
      amount: refundForm.amount,
      reason: refundForm.reason.trim()
    })
    ElMessage.success('退款成功')
    refundVisible.value = false
    load()
  } finally {
    refunding.value = false
  }
}

async function voucher(row: Order): Promise<void> {
  const blob = await orderApi.getVoucher(row.orderNo)
  downloadBlob(blob, `缴费凭证-${row.studentNo || row.orderNo}-${row.orderNo}.html`)
  ElMessage.success('凭证已下载到本地')
}

onMounted(load)
</script>

<style scoped>
.refund-alert {
  margin-bottom: 16px;
}
</style>
