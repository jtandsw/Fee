import type { Router } from 'vue-router'
import { homePath, useUserStore } from '@/stores/user'
import { getAccessToken } from '@/utils/auth'

/**
 * 路由守卫（README 11.3）：
 * 无 token → /login?redirect=...；角色不匹配 → /403。
 * 仅为展示层控制，真正的权限校验在后端。
 */
export function setupRouterGuard(router: Router): void {
  router.beforeEach(async (to) => {
    const appTitle = import.meta.env.VITE_APP_TITLE || '大学生缴费管理系统'
    document.title = to.meta.title ? `${to.meta.title} - ${appTitle}` : appTitle

    const token = getAccessToken()
    const store = useUserStore()

    if (to.meta.public) {
      if (token && to.path === '/login') {
        if (!store.user) {
          try {
            await store.fetchMe()
          } catch {
            return true
          }
        }
        return homePath(store.role)
      }
      return true
    }

    if (!token) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }

    if (!store.user) {
      try {
        await store.fetchMe()
      } catch {
        return { path: '/login', query: { redirect: to.fullPath } }
      }
    }

    const roles = to.matched.flatMap((record) => (record.meta.roles as string[] | undefined) ?? [])
    if (roles.length > 0 && !roles.includes(store.role)) {
      return '/403'
    }
    return true
  })
}
