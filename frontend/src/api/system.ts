import service from '@/utils/request'

/** 系统运行环境 */
export interface SystemAppInfo {
  name: string
  version: string
  profile: string
  contextPath: string
  port: string
  javaVersion: string
  os: string
  startedAt: string
  uptimeSeconds: number
}

export interface SystemDbInfo {
  product?: string
  version?: string
  url?: string
  driver?: string
  connected: boolean
  error?: string
}

export interface SystemAccountInfo {
  total: number
  student: number
  finance: number
  admin: number
  disabled: number
}

export interface SystemChannelInfo {
  value: string
  label: string
  registered: boolean
  usable: boolean
  remark: string
}

export interface SystemPaymentInfo {
  defaultChannel: string
  orderExpireMinutes: number
  channels: SystemChannelInfo[]
}

export interface SystemSettingInfo {
  frontendBaseUrl: string
  uploadDir: string
  accessTokenMinutes: number
  refreshTokenDays: number
  corsOrigins: string[]
}

export interface SystemInfo {
  app: SystemAppInfo
  db: SystemDbInfo
  account: SystemAccountInfo
  payment: SystemPaymentInfo
  setting: SystemSettingInfo
}

export const getSystemInfo = () => service.get<unknown, SystemInfo>('/v1/system/info')
