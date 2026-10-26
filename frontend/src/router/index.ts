import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import DefaultLayout from '@/layouts/DefaultLayout.vue'
import { setupRouterGuard } from './guard'

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/login' },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/login/index.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/student',
    component: DefaultLayout,
    redirect: '/student/home',
    meta: { roles: ['STUDENT'] },
    children: [
      { path: 'home', name: 'student-home', component: () => import('@/views/student/Home.vue'), meta: { title: '学生首页' } },
      { path: 'items', name: 'student-items', component: () => import('@/views/student/ItemList.vue'), meta: { title: '缴费项目' } },
      { path: 'cashier', name: 'student-cashier', component: () => import('@/views/student/Cashier.vue'), meta: { title: '收银台' } },
      { path: 'pay-result', name: 'student-pay-result', component: () => import('@/views/student/PayResult.vue'), meta: { title: '支付结果' } },
      { path: 'orders', name: 'student-orders', component: () => import('@/views/student/MyOrders.vue'), meta: { title: '我的订单' } }
    ]
  },
  {
    path: '/finance',
    component: DefaultLayout,
    redirect: '/finance/dashboard',
    meta: { roles: ['FINANCE'] },
    children: [
      { path: 'dashboard', name: 'finance-dashboard', component: () => import('@/views/finance/Dashboard.vue'), meta: { title: '财务看板' } },
      { path: 'items', name: 'finance-items', component: () => import('@/views/finance/ItemManage.vue'), meta: { title: '项目管理' } },
      { path: 'orders', name: 'finance-orders', component: () => import('@/views/finance/OrderManage.vue'), meta: { title: '订单管理' } },
      { path: 'reconciliation', name: 'finance-reconciliation', component: () => import('@/views/finance/Reconciliation.vue'), meta: { title: '对账管理' } },
      { path: 'report', name: 'finance-report', component: () => import('@/views/finance/Report.vue'), meta: { title: '统计报表' } },
      { path: 'notices', name: 'finance-notices', component: () => import('@/views/finance/NoticeManage.vue'), meta: { title: '通知管理' } }
    ]
  },
  {
    path: '/admin',
    component: DefaultLayout,
    redirect: '/admin/logs',
    meta: { roles: ['ADMIN'] },
    children: [
      { path: 'logs', name: 'admin-logs', component: () => import('@/views/admin/LogList.vue'), meta: { title: '操作日志' } },
      { path: 'users', name: 'admin-users', component: () => import('@/views/admin/UserManage.vue'), meta: { title: '用户管理' } },
      { path: 'dicts', name: 'admin-dicts', component: () => import('@/views/admin/DictManage.vue'), meta: { title: '数据字典' } },
      { path: 'settings', name: 'admin-settings', component: () => import('@/views/admin/SystemSetting.vue'), meta: { title: '系统设置' } }
    ]
  },
  {
    path: '/403',
    name: 'forbidden',
    component: () => import('@/views/error/403.vue'),
    meta: { public: true, title: '无权限' }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/error/404.vue'),
    meta: { public: true, title: '页面不存在' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

setupRouterGuard(router)

export default router
