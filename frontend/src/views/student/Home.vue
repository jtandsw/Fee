<template>
  <div class="page">
    <div class="card welcome">
      <div>
        <h2 class="welcome-title">你好，{{ userStore.displayName }}</h2>
        <p class="muted">
          {{ userStore.user?.college || '—' }} · {{ userStore.user?.major || '—' }} ·
          {{ userStore.user?.className || '—' }}
        </p>
      </div>
      <div class="welcome-actions">
        <el-button type="primary" @click="router.push('/student/items')">去缴费</el-button>
        <el-button @click="router.push('/student/orders')">我的订单</el-button>
      </div>
    </div>

    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-label">待缴项目</div>
        <div class="stat-value danger">{{ unpaidTotal }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">已缴清项目</div>
        <div class="stat-value success">{{ paidItemTotal }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">待支付订单</div>
        <div class="stat-value warning">{{ pendingTotal }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">已支付订单</div>
        <div class="stat-value primary">{{ paidTotal }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">未读通知</div>
        <div class="stat-value">{{ unread }}</div>
      </div>
    </div>

    <div class="two-col">
      <div class="card" v-loading="loading">
        <div class="card-head">
          <h3 class="card-title">待缴项目</h3>
          <el-link type="primary" @click="router.push('/student/items')">查看全部</el-link>
        </div>
        <el-empty v-if="!loading && items.length === 0" description="所有项目均已缴清，暂无待缴项目" />
        <div v-for="item in items" :key="item.id" class="item-row">
          <div>
            <div class="item-name">
              {{ item.itemName }}
              <el-tag v-if="item.payStatus === 'PARTIAL'" size="small" type="warning" effect="plain">
                已缴 {{ formatAmount(item.paidAmount) }}
              </el-tag>
            </div>
            <div class="muted">截止 {{ formatDateTime(item.endTime) }}</div>
          </div>
          <div class="item-action">
            <MoneyText :amount="item.payStatus === 'PARTIAL' ? item.remainAmount : item.amount" />
            <el-button size="small" type="primary" :loading="payingId === item.id" @click="startPay(item)">
              {{ item.payStatus === 'PARTIAL' ? '继续缴纳' : '去缴费' }}
            </el-button>
          </div>
        </div>
      </div>

      <div class="card" v-loading="loading">
        <h3 class="card-title">最新通知</h3>
        <el-empty v-if="!loading && notices.length === 0" description="暂无通知" />
        <div v-for="notice in notices" :key="notice.id" class="notice-row" @click="openNotice(notice)">
          <div class="notice-title">
            <el-badge is-dot :hidden="notice.read" />
            <span>{{ notice.title }}</span>
          </div>
          <div class="muted">{{ formatDateTime(notice.publishAt || notice.createdAt) }}</div>
        </div>
      </div>
    </div>

    <el-dialog v-model="noticeVisible" :title="activeNotice?.title" width="560px">
      <div class="notice-content">{{ activeNotice?.content }}</div>
    </el-dialog>

    <PartialPayDialog
      v-model="dialogVisible"
      :item="dialogItem"
      :loading="submitting"
      @confirm="submitFromDialog"
    />
  </div>
</template>

<script setup lang="ts">
import { onActivated, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import MoneyText from '@/components/MoneyText.vue'
import PartialPayDialog from '@/components/PartialPayDialog.vue'
import { useItemPayment } from '@/composables/useItemPayment'
import { useUserStore } from '@/stores/user'
import * as itemApi from '@/api/item'
import * as orderApi from '@/api/order'
import * as noticeApi from '@/api/notice'
import { formatAmount, formatDateTime } from '@/utils/format'
import type { Notice, PaymentItem } from '@/types'

const router = useRouter()
const userStore = useUserStore()
const { payingId, dialogVisible, dialogItem, submitting, startPay, submitFromDialog } = useItemPayment()

const loading = ref(false)
const items = ref<PaymentItem[]>([])
const notices = ref<Notice[]>([])
const unpaidTotal = ref(0)
const paidItemTotal = ref(0)
const pendingTotal = ref(0)
const paidTotal = ref(0)
const unread = ref(0)
const noticeVisible = ref(false)
const activeNotice = ref<Notice | null>(null)

async function load(): Promise<void> {
  loading.value = true
  try {
    const [unpaidPage, allPage, pending, paid, noticePage, unreadCount] = await Promise.all([
      // 待缴 = 未缴清（含部分缴纳）；已缴清的项目不再出现在首页待缴列表里
      itemApi.availableItems({ pageNum: 1, pageSize: 5, payFilter: 'UNPAID' }),
      // 只取 total 用于反推"已缴清"数量，避免把全量列表拉回来
      itemApi.availableItems({ pageNum: 1, pageSize: 1, payFilter: 'ALL' }),
      orderApi.pageOrders({ pageNum: 1, pageSize: 1, status: 'PENDING' }),
      orderApi.pageOrders({ pageNum: 1, pageSize: 1, status: 'PAID' }),
      noticeApi.pageNotices({ pageNum: 1, pageSize: 5 }),
      noticeApi.unreadCount()
    ])
    items.value = unpaidPage.list
    unpaidTotal.value = unpaidPage.total
    paidItemTotal.value = Math.max(allPage.total - unpaidPage.total, 0)
    pendingTotal.value = pending.total
    paidTotal.value = paid.total
    notices.value = noticePage.list
    unread.value = unreadCount
  } finally {
    loading.value = false
  }
}

async function openNotice(notice: Notice): Promise<void> {
  try {
    activeNotice.value = await noticeApi.getNotice(notice.id)
    noticeVisible.value = true
    unread.value = await noticeApi.unreadCount()
    notice.read = true
  } catch {
    ElMessage.warning('通知不存在或已过期')
  }
}

onMounted(load)
// 页面被缓存（keep-alive）时，从收银台/订单页返回也重新拉取，保证统计与待缴列表是最新的
onActivated(load)
</script>

<style scoped>
.welcome {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.welcome-title {
  margin: 0 0 6px;
  font-size: 22px;
}

.welcome-actions {
  display: flex;
  gap: 10px;
}

.two-col {
  display: grid;
  grid-template-columns: 1.3fr 1fr;
  gap: 16px;
}

@media (max-width: 1000px) {
  .two-col {
    grid-template-columns: 1fr;
  }
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.item-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px dashed #ebeef5;
}

.item-row:last-child {
  border-bottom: none;
}

.item-name {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 4px;
}

.item-action {
  display: flex;
  align-items: center;
  gap: 14px;
}

.notice-row {
  padding: 10px 0;
  border-bottom: 1px dashed #ebeef5;
  cursor: pointer;
}

.notice-row:last-child {
  border-bottom: none;
}

.notice-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  margin-bottom: 4px;
}

.notice-content {
  white-space: pre-wrap;
  line-height: 1.8;
}
</style>
