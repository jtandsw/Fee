<template>
  <el-menu
    :default-active="route.path"
    :collapse="collapsed"
    :collapse-transition="false"
    router
    class="side-menu"
  >
    <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
      <el-icon><component :is="item.icon" /></el-icon>
      <template #title>{{ item.title }}</template>
    </el-menu-item>
  </el-menu>
</template>

<script setup lang="ts">
import { computed, markRaw, type Component } from 'vue'
import { useRoute } from 'vue-router'
import {
  Bell,
  DataAnalysis,
  Document,
  HomeFilled,
  List,
  OfficeBuilding,
  Setting,
  Tickets,
  User,
  Wallet
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

defineProps<{ collapsed: boolean }>()

interface MenuItem {
  path: string
  title: string
  icon: Component
}

const route = useRoute()
const userStore = useUserStore()

const financeMenus: MenuItem[] = [
  { path: '/finance/dashboard', title: '财务看板', icon: markRaw(DataAnalysis) },
  { path: '/finance/items', title: '项目管理', icon: markRaw(Wallet) },
  { path: '/finance/orders', title: '订单管理', icon: markRaw(Tickets) },
  { path: '/finance/reconciliation', title: '对账管理', icon: markRaw(OfficeBuilding) },
  { path: '/finance/report', title: '统计报表', icon: markRaw(Document) },
  { path: '/finance/notices', title: '通知管理', icon: markRaw(Bell) }
]

const adminMenus: MenuItem[] = [
  { path: '/admin/logs', title: '操作日志', icon: markRaw(List) },
  { path: '/admin/users', title: '用户管理', icon: markRaw(User) },
  { path: '/admin/settings', title: '系统设置', icon: markRaw(Setting) },
  { path: '/admin/dicts', title: '数据字典', icon: markRaw(Document) }
]

const studentMenus: MenuItem[] = [
  { path: '/student/home', title: '学生首页', icon: markRaw(HomeFilled) },
  { path: '/student/items', title: '缴费项目', icon: markRaw(Wallet) },
  { path: '/student/orders', title: '我的订单', icon: markRaw(Tickets) }
]

const menus = computed<MenuItem[]>(() => {
  if (userStore.role === 'STUDENT') {
    return studentMenus
  }
  if (userStore.role === 'ADMIN') {
    // 管理员不展示任何财务入口，避免误入财务数据页面
    return adminMenus
  }
  return financeMenus
})
</script>

<style scoped>
.side-menu {
  border-right: none;
  background: transparent;
}
:deep(.el-menu-item) {
  margin: 4px 10px;
  border-radius: 8px;
}
:deep(.el-menu-item.is-active) {
  background: #ecf3ff;
  color: #2f6fed;
  font-weight: 600;
}
</style>
