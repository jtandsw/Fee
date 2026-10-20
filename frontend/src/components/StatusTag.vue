<template>
  <el-tag :type="meta.type" effect="light" size="small">{{ meta.label }}</el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { BATCH_STATUS, DIFF_TYPE, ITEM_STATUS, ORDER_STATUS } from '@/utils/status'

const props = defineProps<{
  status: string
  type?: 'order' | 'item' | 'batch' | 'diff'
}>()

const dict = computed(() => {
  switch (props.type) {
    case 'item':
      return ITEM_STATUS
    case 'batch':
      return BATCH_STATUS
    case 'diff':
      return DIFF_TYPE
    default:
      return ORDER_STATUS
  }
})

const meta = computed(
  () => dict.value[props.status] ?? { label: props.status || '-', type: 'info' as const }
)
</script>
