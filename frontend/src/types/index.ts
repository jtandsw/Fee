export type Role = 'STUDENT' | 'FINANCE' | 'ADMIN'

export interface UserInfo {
  id: number
  username: string
  realName: string
  role: Role
  phone?: string
  email?: string
  status?: number
  studentNo?: string
  college?: string
  major?: string
  className?: string
  grade?: number
}

export interface LoginPayload {
  username: string
  password: string
}

export interface LoginResult {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: UserInfo
}

export interface PageData<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
}

export interface PageQuery {
  pageNum?: number
  pageSize?: number
}

/** 适用范围条件（后端已把历史单一维度数据统一翻译成该结构） */
export interface ItemScope {
  grades?: string[]
  colleges?: string[]
  majors?: string[]
  /** 学生 id 列表 */
  studentIds?: string[]
  /** 一句话描述，全空为「全校」 */
  text?: string
}

export interface PaymentItem {
  id: number
  itemNo: string
  itemName: string
  category: string
  term: string
  amount: string
  allowPartial: number
  targetScope: string
  scopeValue?: string[] | null
  scope?: ItemScope | null
  startTime: string
  endTime: string
  status: string
  description?: string
  createdBy?: number
  createdAt?: string
  /** 以下三项仅在学生端 /payment-items/available 返回：本人缴费进度 */
  paidAmount?: string
  remainAmount?: string
  payStatus?: 'UNPAID' | 'PARTIAL' | 'PAID'
}

export interface Order {
  orderNo: string
  itemId: number
  itemNo: string
  itemName: string
  category: string
  studentId: number
  studentNo: string
  studentName: string
  college: string
  amount: string
  paidAmount: string
  channel: string
  status: string
  transactionNo?: string
  expireAt?: string
  paidAt?: string
  closedAt?: string
  createdAt: string
}

export interface OrderCreateResult {
  orderNo: string
  amount: string
  status: string
  expireAt: string
  payUrl: string
  /** 复用了本人该项目的未支付订单，本次没有新建订单 */
  reused: boolean
}

export interface PayResult {
  orderNo: string
  channel: string
  payUrl: string
  payParams?: Record<string, unknown>
  expireAt?: string
}

export interface RefundResult {
  refundNo: string
  orderNo: string
  amount: string
  reason: string
  status: string
  refundedAt?: string
}

export interface ReconBatch {
  batchNo: string
  channel: string
  billDate: string
  totalCount: number
  totalAmount: string
  matchedCount: number
  diffCount: number
  diffAmount: string
  status: string
  filePath?: string
  createdAt: string
}

export interface ReconDetail {
  id: number
  batchId: number
  orderNo?: string
  channelTradeNo?: string
  localAmount?: string
  channelAmount?: string
  diffType: string
  handleStatus: string
  handleRemark?: string
  createdAt: string
}

export interface OverviewStat {
  receivable: string
  received: string
  outstanding: string
  payRate: string
  orderCount: number
  paidCount: number
  studentCount: number
}

export interface ItemStat {
  itemNo: string
  itemName: string
  category: string
  amount: string
  receivable: string
  received: string
  orderCount: number
  paidCount: number
}

export interface CollegeStat {
  college: string
  studentCount: number
  receivable: string
  received: string
}

export interface TrendPoint {
  statDate: string
  amount: string
  count: number
}

export interface Notice {
  id: number
  title: string
  content: string
  noticeType: string
  targetRole: string
  itemId?: number
  status: string
  publishAt?: string
  expireAt?: string
  createdAt: string
  read?: boolean
}

export interface OperationLog {
  id: number
  userId?: number
  username?: string
  module: string
  action: string
  method?: string
  uri?: string
  ip?: string
  resultCode?: number
  costMs?: number
  createdAt: string
}

export interface ImportResult {
  total: number
  success: number
  skipped: number
  failed: number
  messages: string[]
}
