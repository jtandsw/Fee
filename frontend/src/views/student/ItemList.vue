<template>
  <div class="page">
    <div class="card">
      <div class="card-head">
        <div>
          <h3 class="card-title">缴费项目</h3>
          <p class="muted">仅展示已发布、在缴费时间窗内且适用于你的项目</p>
        </div>
        <div class="filters">
          <el-radio-group v-model="payFilter" size="small" @change="onFilterChange">
            <el-radio-button label="ALL">全部</el-radio-button>
            <el-radio-button label="UNPAID">待缴</el-radio-button>
            <el-radio-button label="PAID">已缴清</el-radio-button>
          </el-radio-group>
          <el-button :icon="Refresh" @click="load">刷新</el-button>
        </div>
      </div>
    </div>

    <div v-loading="loading" class="item-grid">
      <el-empty
        v-if="!loading && items.length === 0"
        :description="emptyText"
        class="empty"
      />
      <div
        v-for="item in items"
        :key="item.id"
        class="item-card"
        :class="{ 'is-paid': item.payStatus === 'PAID' }"
      >
        <div class="item-head">
          <el-tag size="small" effect="plain">{{ CATEGORY_LABEL[item.category] || item.category }}</el-tag>
          <el-tag v-if="item.allowPartial === 1" size="small" type="warning" effect="plain">可分次</el-tag>
          <el-tag
            v-if="item.payStatus"
            size="small"
            :type="PAY_STATUS[item.payStatus].type"
            effect="dark"
            class="pay-tag"
          >
            {{ PAY_STATUS[item.payStatus].label }}
          </el-tag>
        </div>
        <div class="item-name">{{ item.itemName }}</div>
        <div class="item-term muted">{{ item.term }} · {{ TARGET_SCOPE_LABEL[item.targetScope] || item.targetScope }}</div>
        <div class="item-amount">
          <MoneyText :amount="item.amount" />
        </div>
        <div v-if="item.payStatus === 'PARTIAL'" class="pay-progress partial">
          已缴 {{ formatAmount(item.paidAmount) }}，剩余 {{ formatAmount(item.remainAmount) }}
        </div>
        <div v-else-if="item.payStatus === 'PAID'" class="pay-progress paid">
          已缴 {{ formatAmount(item.paidAmount) }}，已结清
        </div>
        <div class="item-deadline">截止 {{ formatDateTime(item.endTime) }}</div>
        <el-button
          v-if="item.payStatus !== 'PAID'"
          type="primary"
          class="pay-btn"
          :loading="payingId === item.id"
          @click="pay(item)"
        >
          {{ item.payStatus === 'PARTIAL' ? `缴剩余 ${formatAmount(item.remainAmount)}` : '立即缴费' }}
        </el-button>
        <el-button v-else class="pay-btn" disabled>已缴清</el-button>
      </div>
    </div>

    <div class="pagination">
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[6, 9, 12, 24]"
        layout="total, sizes, prev, pager, next"
        background
        @current-change="load"
        @size-change="onSizeChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import MoneyText from '@/components/MoneyText.vue'
import * as itemApi from '@/api/item'
import * as orderApi from '@/api/order'
import { formatAmount, formatDateTime } from '@/utils/format'
import { newIdempotencyKey } from '@/utils/idempotency'
import { CATEGORY_LABEL, PAY_STATUS, TARGET_SCOPE_LABEL } from '@/utils/status'
import type { PaymentItem } from '@/types'

const router = useRouter()

const items = ref<PaymentItem[]>([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(9)
const total = ref(0)
const payingId = ref<number | null>(null)
const payFilter = ref<'ALL' | 'UNPAID' | 'PAID'>('ALL')

const emptyText = computed(() => {
  if (payFilter.value === 'UNPAID') {
    return '所有项目均已缴清'
  }
  if (payFilter.value === 'PAID') {
    return '暂无已缴清的项目'
  }
  return '暂无可缴项目'
})

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await itemApi.availableItems({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      payFilter: payFilter.value
    })
    items.value = page.list
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function onFilterChange(): void {
  pageNum.value = 1
  load()
}

function onSizeChange(): void {
  pageNum.value = 1
  load()
}

async function pay(item: PaymentItem): Promise<void> {
  payingId.value = item.id
  try {
    // 分次缴费时后端按「剩余应缴」自动建单，这里无需传金额
    const order = await orderApi.createOrder({ itemId: item.id, channel: 'MOCK' }, newIdempotencyKey())
    ElMessage.success(`订单已创建：${order.orderNo}`)
    await router.push({ path: '/student/cashier', query: { orderNo: order.orderNo } })
  } finally {
    payingId.value = null
  }
}

onMounted(load)
</script>

<style scoped>
.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.filters {
  display: flex;
  align-items: center;
  gap: 12px;
}

.item-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  min-height: 180px;
}

.empty {
  grid-column: 1 / -1;
}

.item-card {
  background: #fff;
  border-radius: 10px;
  padding: 18px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  display: flex;
  flex-direction: column;
  border: 1px solid transparent;
  transition: border-color 0.2s, transform 0.2s;
}

.item-card:hover {
  border-color: #cfe0ff;
  transform: translateY(-2px);
}

/* 已缴清：整体降饱和，和待缴项目形成视觉区分 */
.item-card.is-paid {
  background: #f7f9fc;
  border-color: #e4e9f2;
}

.item-card.is-paid .item-name,
.item-card.is-paid .item-amount {
  color: #8a94a6;
}

.item-head {
  display: flex;
  gap: 6px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.pay-tag {
  margin-left: auto;
}

.item-name {
  font-size: 16px;
  font-weight: 600;
  color: #1f2d3d;
  line-height: 1.5;
  min-height: 48px;
}

.item-term {
  margin-top: 4px;
}

.item-amount {
  margin: 10px 0 4px;
  font-size: 24px;
}

.pay-progress {
  font-size: 12px;
  margin-bottom: 6px;
}

.pay-progress.partial {
  color: #e6a23c;
}

.pay-progress.paid {
  color: #67c23a;
}

.item-deadline {
  font-size: 12px;
  color: #909399;
  margin-bottom: 14px;
}

.pay-btn {
  margin-top: auto;
  width: 100%;
}
</style>
