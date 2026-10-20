<template>
  <div class="page">
    <el-result
      v-if="!loading && !order"
      icon="info"
      title="订单不存在"
      sub-title="请返回我的订单重新发起支付"
    >
      <template #extra>
        <el-button type="primary" @click="router.replace('/student/orders')">返回我的订单</el-button>
      </template>
    </el-result>

    <div v-else class="card cashier" v-loading="loading">
      <div class="head">
        <div>
          <h3 class="card-title">收银台</h3>
          <p class="muted">订单号 {{ order?.orderNo }}</p>
        </div>
        <StatusTag :status="order?.status || ''" />
      </div>

      <div class="amount-box">
        <span class="label">应付金额</span>
        <div class="amount">
          <MoneyText :amount="order?.amount" />
        </div>
        <div v-if="order?.status === 'PENDING'" class="deadline">
          支付剩余时间：<b>{{ countdownText }}</b>
        </div>
      </div>

      <el-descriptions :column="2" border class="desc">
        <el-descriptions-item label="缴费项目">{{ order?.itemName }}</el-descriptions-item>
        <el-descriptions-item label="项目编号">{{ order?.itemNo }}</el-descriptions-item>
        <el-descriptions-item label="学生">
          {{ order?.studentName }}（{{ order?.studentNo }}）
        </el-descriptions-item>
        <el-descriptions-item label="支付渠道">
          {{ CHANNEL_LABEL[order?.channel || ''] || order?.channel }}
        </el-descriptions-item>
      </el-descriptions>

      <div v-if="order?.status === 'PENDING'" class="actions">
        <el-button type="primary" size="large" :loading="paying" @click="startPay">发起支付</el-button>
        <el-button size="large" @click="closeOrder">关闭订单</el-button>
      </div>

      <div v-else-if="order?.status === 'PAYING'" class="mock-panel">
        <el-alert
          type="info"
          :closable="false"
          title="模拟支付渠道（MOCK）"
          description="演示环境不会产生真实扣款，点击下方按钮模拟渠道回调结果。"
          show-icon
        />
        <div class="mock-actions">
          <el-button type="success" size="large" :loading="callbackLoading" @click="sendCallback('SUCCESS')">
            模拟支付成功
          </el-button>
          <el-button type="danger" plain size="large" :loading="callbackLoading" @click="sendCallback('FAILED')">
            模拟支付失败
          </el-button>
        </div>
      </div>

      <div v-else class="actions">
        <el-alert
          v-if="order?.status === 'PAID'"
          type="success"
          :closable="false"
          title="该订单已支付完成"
          show-icon
        />
        <el-alert
          v-else-if="order?.status === 'CLOSED'"
          type="warning"
          :closable="false"
          title="该订单已关闭"
          show-icon
        />
        <el-alert v-else type="info" :closable="false" :title="`当前状态：${order?.status}`" show-icon />
        <el-button
          type="primary"
          @click="router.push({ path: '/student/pay-result', query: { orderNo: order?.orderNo } })"
        >
          查看支付结果
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import StatusTag from '@/components/StatusTag.vue'
import MoneyText from '@/components/MoneyText.vue'
import * as orderApi from '@/api/order'
import { CHANNEL_LABEL } from '@/utils/status'
import type { Order } from '@/types'

const route = useRoute()
const router = useRouter()

const orderNo = computed(() => String(route.query.orderNo || ''))
const order = ref<Order | null>(null)
const loading = ref(false)
const paying = ref(false)
const callbackLoading = ref(false)
const now = ref(dayjs())
let timer: number | undefined

const countdownText = computed(() => {
  if (!order.value?.expireAt) {
    return '-'
  }
  const seconds = dayjs(order.value.expireAt).diff(now.value, 'second')
  if (seconds <= 0) {
    return '已超时'
  }
  const minutes = Math.floor(seconds / 60)
  const rest = seconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(rest).padStart(2, '0')}`
})

async function loadOrder(): Promise<void> {
  if (!orderNo.value) {
    return
  }
  loading.value = true
  try {
    order.value = await orderApi.getOrder(orderNo.value)
  } finally {
    loading.value = false
  }
}

async function startPay(): Promise<void> {
  paying.value = true
  try {
    await orderApi.payOrder(orderNo.value)
    order.value = await orderApi.getOrder(orderNo.value)
    ElMessage.success('支付已发起，请在模拟渠道完成支付')
  } finally {
    paying.value = false
  }
}

async function sendCallback(status: 'SUCCESS' | 'FAILED'): Promise<void> {
  if (!order.value) {
    return
  }
  callbackLoading.value = true
  try {
    await orderApi.mockPayCallback({
      orderNo: order.value.orderNo,
      transactionNo: `MOCK${Date.now()}`,
      amount: order.value.amount,
      status
    })
    if (status === 'SUCCESS') {
      ElMessage.success('支付成功')
      await router.replace({ path: '/student/pay-result', query: { orderNo: order.value.orderNo } })
    } else {
      ElMessage.warning('渠道返回支付失败，订单已回到待支付状态')
      await loadOrder()
    }
  } finally {
    callbackLoading.value = false
  }
}

async function closeOrder(): Promise<void> {
  try {
    await ElMessageBox.confirm('确定关闭该订单吗？关闭后需重新下单。', '提示', { type: 'warning' })
  } catch {
    return
  }
  await orderApi.closeOrder(orderNo.value)
  ElMessage.success('订单已关闭')
  await loadOrder()
}

onMounted(() => {
  loadOrder()
  timer = window.setInterval(() => {
    now.value = dayjs()
  }, 1000)
})

onUnmounted(() => {
  if (timer) {
    window.clearInterval(timer)
  }
})
</script>

<style scoped>
.cashier {
  max-width: 880px;
  margin: 0 auto;
}

.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.amount-box {
  margin: 24px 0;
  padding: 24px;
  border-radius: 10px;
  background: linear-gradient(135deg, #f4f8ff, #eef4ff);
  text-align: center;
}

.amount-box .label {
  color: #606266;
  font-size: 14px;
}

.amount {
  font-size: 38px;
  margin: 8px 0;
}

.deadline {
  color: #909399;
  font-size: 13px;
}

.deadline b {
  color: #d9534f;
  font-variant-numeric: tabular-nums;
}

.desc {
  margin-bottom: 24px;
}

.actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
}

.actions .el-button {
  min-width: 180px;
}

.mock-panel {
  border-top: 1px dashed #ebeef5;
  padding-top: 20px;
}

.mock-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-top: 18px;
}
</style>
