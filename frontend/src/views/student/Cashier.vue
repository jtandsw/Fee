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
        <el-descriptions-item label="支付渠道">{{ displayChannel }}</el-descriptions-item>
      </el-descriptions>

      <div v-if="order?.status === 'PENDING' && channels.length > 1" class="channel-box">
        <div class="channel-head">
          <span class="channel-label">支付渠道</span>
          <span class="channel-tip">演示环境仅「模拟支付」可用，不会产生真实扣款</span>
        </div>
        <div class="channel-list">
          <button
            v-for="c in channels"
            :key="c.value"
            type="button"
            class="channel-item"
            :class="{ active: selectedChannel === c.value, disabled: !c.available }"
            @click="selectChannel(c)"
          >
            <span class="channel-name">{{ c.label }}</span>
            <span v-if="!c.available" class="channel-badge">{{ c.remark || '未开通' }}</span>
            <span v-else-if="selectedChannel === c.value" class="channel-badge ok">已选择</span>
          </button>
        </div>
      </div>

      <!-- 只有一个可用渠道时没有选择的意义，退化成一行说明，避免展示无法使用的占位卡片 -->
      <p v-else-if="order?.status === 'PENDING' && channels.length === 1" class="channel-note">
        演示环境不会产生真实扣款，发起支付后将进入模拟回调确认
      </p>

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
import type { ChannelOption } from '@/api/order'
import type { Order } from '@/types'

const route = useRoute()
const router = useRouter()

const orderNo = computed(() => String(route.query.orderNo || ''))
const order = ref<Order | null>(null)
const loading = ref(false)
const paying = ref(false)
const callbackLoading = ref(false)
const now = ref(dayjs())
const channels = ref<ChannelOption[]>([])
const selectedChannel = ref('MOCK')
let timer: number | undefined

/** 待支付时展示用户即将使用的渠道，其余状态展示订单实际落库的渠道 */
const displayChannel = computed(() => {
  if (order.value?.status === 'PENDING' && selectedChannel.value) {
    const hit = channels.value.find((c) => c.value === selectedChannel.value)
    return hit?.label || selectedChannel.value
  }
  const channel = order.value?.channel || ''
  return CHANNEL_LABEL[channel] || channel
})

const countdownText = computed(() => {
  if (!order.value?.expireAt) {
    return '-'
  }
  const seconds = dayjs(order.value.expireAt).diff(now.value, 'second')
  if (seconds <= 0) {
    return '已超时'
  }
  // 超过一天时不能只按分钟累加，否则会输出「7090:13」这类不是时长的字符串
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const rest = seconds % 60
  const clock = [hours, minutes, rest].map((v) => String(v).padStart(2, '0')).join(':')
  return days > 0 ? `${days} 天 ${clock}` : clock
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

async function loadChannels(): Promise<void> {
  try {
    channels.value = await orderApi.getPayChannels()
    const firstAvailable = channels.value.find((c) => c.available)
    if (firstAvailable) {
      selectedChannel.value = firstAvailable.value
    }
  } catch {
    // 渠道清单拉取失败时退回模拟渠道，不阻塞收银台
    channels.value = [{ value: 'MOCK', label: '模拟支付', available: true }]
  }
}

function selectChannel(channel: ChannelOption): void {
  if (!channel.available) {
    ElMessage.info(`${channel.label}：${channel.remark || '暂未开通'}`)
    return
  }
  selectedChannel.value = channel.value
}

async function startPay(): Promise<void> {
  paying.value = true
  try {
    await orderApi.payOrder(orderNo.value, selectedChannel.value)
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
  loadChannels()
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

.channel-box {
  margin-bottom: 24px;
  padding: 16px 18px;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  background: #fafbfe;
}

.channel-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.channel-label {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.channel-tip {
  font-size: 12px;
  color: #909399;
}

.channel-note {
  margin: 0 0 20px;
  padding: 10px 14px;
  border-radius: 8px;
  background: #f5f7fa;
  font-size: 13px;
  color: #606266;
  text-align: center;
}

.channel-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 10px;
}

.channel-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding: 10px 14px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: #fff;
  color: #303133;
  font-size: 14px;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
}

.channel-name {
  white-space: nowrap;
  line-height: 1.3;
}

.channel-item:hover:not(.disabled) {
  border-color: #a7c0f7;
}

.channel-item.active {
  border-color: #2f6fed;
  background: #f2f7ff;
  color: #2f6fed;
  font-weight: 600;
}

.channel-item.disabled {
  cursor: not-allowed;
  border-color: #ebeef5;
  background: #f7f8fa;
  color: #c0c4cc;
}

.channel-badge {
  font-size: 12px;
  color: #c0c4cc;
  white-space: nowrap;
}

.channel-badge.ok {
  color: #2f6fed;
}

.actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
}

.actions .el-button {
  width: 220px;
  /* Element Plus 默认给相邻按钮加 margin-left，纵向排列时会整体偏移，这里清零 */
  margin-left: 0;
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
