import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from './auth'

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'

const service = axios.create({
  baseURL,
  timeout: 20_000
})

export class ApiError extends Error {
  code: number
  traceId?: string

  constructor(code: number, message: string, traceId?: string) {
    super(message)
    this.code = code
    this.traceId = traceId
  }
}

let refreshing: Promise<string> | null = null

function redirectToLogin(): void {
  clearTokens()
  const current = window.location.pathname + window.location.search
  if (!window.location.pathname.startsWith('/login')) {
    window.location.href = `/login?redirect=${encodeURIComponent(current)}`
  }
}

async function refreshAccessToken(): Promise<string> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    throw new Error('缺少 refreshToken')
  }
  const { data } = await axios.post(`${baseURL}/v1/auth/refresh`, { refreshToken })
  if (!data || data.code !== 0) {
    throw new Error(data?.message || '刷新令牌失败')
  }
  setTokens(data.data.accessToken, data.data.refreshToken)
  return data.data.accessToken
}

/** 按错误码段处理（README 10.3） */
function handleBusinessCode(code: number, message: string, traceId?: string): void {
  if (code >= 40100 && code < 40200) {
    ElMessage.error(message || '登录已失效')
    redirectToLogin()
    return
  }
  if (code >= 40300 && code < 40400) {
    ElMessage.warning(message || '无权访问')
    return
  }
  if (code >= 60000 && code < 60100) {
    ElMessage.warning(message)
    return
  }
  if (code >= 50000) {
    ElMessage.error(`${message || '系统内部错误'}${traceId ? `（traceId: ${traceId}）` : ''}`)
    return
  }
  ElMessage.warning(message || '请求失败')
}

service.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

service.interceptors.response.use(
  (response) => {
    // 文件流直接返回，不做统一响应体解包
    if (response.config.responseType === 'blob') {
      return response.data
    }
    const body = response.data
    if (body && typeof body.code === 'number') {
      if (body.code === 0) {
        return body.data
      }
      handleBusinessCode(body.code, body.message, body.traceId)
      return Promise.reject(new ApiError(body.code, body.message, body.traceId))
    }
    return body
  },
  async (error: AxiosError) => {
    const config = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined
    const status = error.response?.status

    // 401 自动刷新一次并重放原请求（README 10.3 前端拦截器约定）
    if (status === 401 && config && !config._retry && getRefreshToken() && !config.url?.includes('/auth/refresh')) {
      config._retry = true
      try {
        if (!refreshing) {
          refreshing = refreshAccessToken().finally(() => {
            refreshing = null
          })
        }
        const token = await refreshing
        config.headers.Authorization = `Bearer ${token}`
        return service(config)
      } catch (refreshError) {
        ElMessage.error('登录已过期，请重新登录')
        redirectToLogin()
        return Promise.reject(refreshError)
      }
    }

    if (status === 401) {
      ElMessage.error('登录已失效，请重新登录')
      redirectToLogin()
    } else if (!error.response) {
      ElMessage.error('网络连接失败，请检查后端服务是否启动')
    } else {
      ElMessage.error(`请求失败（HTTP ${status}）`)
    }
    return Promise.reject(error)
  }
)

export function download(url: string, params?: Record<string, unknown>): Promise<Blob> {
  return service.get<unknown, Blob>(url, { params, responseType: 'blob' })
}

export default service
