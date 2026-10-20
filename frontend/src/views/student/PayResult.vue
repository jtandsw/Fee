<template>
  <div class="page">
    <div class="card result-card" v-loading="loading">
      <el-result
        :icon="success ? 'success' : order?.status === 'CLOSED' ? 'warning' : 'info'"
        :title="title"
        :sub-title="subTitle"
      >
        <template #extra>
          <div class="result-actions">
            <el-button v-if="success" type="primary" :loading="voucherLoading" @click="downloadVoucher">
              下载缴费凭证
            </el-button>
            <el-button @click="router.push('/student/orders')">查看我的订单</el-button>
            <el-button @click="router.push('/student/items')">继续缴费</el-button>
          </div>
        </template>
      </el-result>

      <el-descriptions v-if="order" :column="2" border class="desc">
        <el-descriptions-item label="订单号">{{ order.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="订单状态">
          <StatusTag :status="order.status" />
        </el-descriptions-item>
        <el-descriptions-item label="缴费项目">{{ order.itemName }}</el-descriptions-item>
        <el-descriptions-item label="缴费金额"><MoneyText :amount="order.amount" /></el-descriptions-item>
        <el-descriptions-item label="支付渠道">
          {{ CHANNEL_LABEL[order.channel] || order.channel }}
        </el-descriptions-item>
        <el-descriptions-item label="渠道流水号">{{ order.transactionNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="支付时间" :span="2">{{ formatDateTime(order.paidAt) }}</el-descriptions-item>
      </el-descriptions>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import StatusTag from '@/components/StatusTag.vue'
import MoneyText from '@/components/MoneyText.vue'
import * as orderApi from '@/api/order'
import { formatDateTime, openBlob } from '@/utils/format'
import { CHANNEL_LABEL } from '@/utils/status'
import type { Order } from '@/types'

const route = useRoute()
const router = useRouter()
const order = ref<Order | null>(null)
const loading = ref(false)
const voucherLoading = ref(false)

const success = computed(() => order.value?.status === 'PAID' || order.value?.status === 'PARTIAL_REFUND')
const title = computed(() => {
  if (!order.value) {
    return '订单不存在'
  }
  if (success.value) {
    return '缴费成功'
  }
  return order.value.status === 'CLOSED' ? '订单已关闭' : '支付未完成'
})
const subTitle = computed(() => {
  if (!order.value) {
    return '请返回订单列表重试'
  }
  if (success.value) {
    return `已成功缴纳 ${order.value.itemName}`
  }
  return order.value.status === 'CLOSED' ? '订单已关闭，请重新下单' : '订单仍处于待支付状态'
})

async function load(): Promise<void> {
  const orderNo = String(route.query.orderNo || '')
  if (!orderNo) {
    return
  }
  loading.value = true
  try {
    order.value = await orderApi.getOrder(orderNo)
  } finally {
    loading.value = false
  }
}

async function downloadVoucher(): Promise<void> {
  if (!order.value) {
    return
  }
  voucherLoading.value = true
  try {
    const blob = await orderApi.getVoucher(order.value.orderNo)
    openBlob(blob)
    ElMessage.success('凭证已生成')
  } finally {
    voucherLoading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.result-card {
  max-width: 880px;
  margin: 0 auto;
}

.result-actions {
  display: flex;
  gap: 10px;
  justify-content: center;
}

.desc {
  margin-top: 8px;
}
</style>
