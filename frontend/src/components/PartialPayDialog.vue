<template>
  <el-dialog
    :model-value="modelValue"
    title="分次缴纳"
    width="480px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-if="item" class="pay-dialog">
      <div class="item-name">{{ item.itemName }}</div>

      <el-descriptions :column="3" size="small" border>
        <el-descriptions-item label="应缴总额">{{ formatAmount(item.amount) }} 元</el-descriptions-item>
        <el-descriptions-item label="已缴">{{ formatAmount(item.paidAmount) }} 元</el-descriptions-item>
        <el-descriptions-item label="剩余可缴">{{ formatAmount(maxAmount) }} 元</el-descriptions-item>
      </el-descriptions>

      <div class="amount-field">
        <span class="field-label">本次缴纳</span>
        <el-input-number
          v-model="amount"
          :min="0.01"
          :max="maxAmount"
          :precision="2"
          :step="100"
          controls-position="right"
          class="amount-input"
        />
        <el-button link type="primary" :disabled="amount === maxAmount" @click="fillRemain">缴清剩余</el-button>
      </div>

      <p class="tip">
        本项目支持分次缴纳，支付后仍可在截止前继续补缴剩余部分。演示环境走模拟支付，不产生真实扣款。
      </p>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="loading" @click="confirm">
        去支付 {{ formatAmount(amount) }} 元
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { formatAmount } from '@/utils/format'
import type { PaymentItem } from '@/types'

const props = defineProps<{
  modelValue: boolean
  item: PaymentItem | null
  loading?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'confirm', amount: string): void
}>()

const amount = ref(0.01)

const maxAmount = computed(() => Number(props.item?.remainAmount ?? props.item?.amount ?? 0))

watch(
  () => [props.modelValue, props.item?.id],
  () => {
    // 每次打开默认缴清剩余，想分次再改小
    if (props.modelValue) {
      amount.value = maxAmount.value
    }
  },
  { immediate: true }
)

function fillRemain(): void {
  amount.value = maxAmount.value
}

function confirm(): void {
  // 用定长字符串提交，避免浮点数在 JSON 里出现 0.30000000000000004 这类精度尾巴
  emit('confirm', Number(amount.value).toFixed(2))
}
</script>

<style scoped>
.item-name {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 12px;
}

.amount-field {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 18px;
}

.field-label {
  font-size: 13px;
  color: #606266;
  white-space: nowrap;
}

.amount-input {
  width: 160px;
}

.tip {
  margin: 12px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: #909399;
}
</style>
