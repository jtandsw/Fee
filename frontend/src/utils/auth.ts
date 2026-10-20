/**
 * 令牌存取。
 * 说明：README FAQ Q6 提到 localStorage，但为避免 XSS 长期驻留，MVP 使用 sessionStorage
 * （关闭标签页即失效），差异见 docs/架构说明.md。
 */
const ACCESS_KEY = 'campus_access_token'
const REFRESH_KEY = 'campus_refresh_token'

export function getAccessToken(): string {
  return sessionStorage.getItem(ACCESS_KEY) || ''
}

export function getRefreshToken(): string {
  return sessionStorage.getItem(REFRESH_KEY) || ''
}

export function setTokens(accessToken: string, refreshToken: string): void {
  sessionStorage.setItem(ACCESS_KEY, accessToken)
  if (refreshToken) {
    sessionStorage.setItem(REFRESH_KEY, refreshToken)
  }
}

export function clearTokens(): void {
  sessionStorage.removeItem(ACCESS_KEY)
  sessionStorage.removeItem(REFRESH_KEY)
}
