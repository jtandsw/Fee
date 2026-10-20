<template>
  <span class="money-text">￥{{ formatAmount(amount) }}</span>
</template>

<script setup lang="ts">
import { formatAmount } from '@/utils/format'

defineProps<{ amount?: string | number | null }>()
</script>

<style scoped>
.money-text {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: #303133;
}
</style>
