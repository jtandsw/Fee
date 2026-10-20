<template>
  <div class="error-page">
    <el-result icon="warning" title="403" sub-title="当前角色无权访问该页面">
      <template #extra>
        <el-button type="primary" @click="goHome">返回首页</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { homePath, useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

function goHome(): void {
  router.replace(userStore.role ? homePath(userStore.role) : '/login')
}
</script>

<style scoped>
.error-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
}
</style>
