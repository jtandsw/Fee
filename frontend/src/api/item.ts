import service from '@/utils/request'
import type { PageData, PageQuery, PaymentItem } from '@/types'

export interface ItemQuery extends PageQuery {
  status?: string
  category?: string
  term?: string
  keyword?: string
}

/** 适用范围条件：四维度可任意组合，非空维度取交集，全空 = 全校 */
export interface ScopeRule {
  grades?: string[]
  colleges?: string[]
  majors?: string[]
  /** 学生 id 列表 */
  studentIds?: string[]
}

export interface ItemPayload {
  itemName: string
  category: string
  term: string
  amount: string | number
  allowPartial: number
  scope?: ScopeRule
  startTime: string
  endTime: string
  description?: string
}

export interface AvailableQuery extends PageQuery {
  /** ALL(默认) / UNPAID 未缴清 / PAID 已缴清 */
  payFilter?: 'ALL' | 'UNPAID' | 'PAID'
}

/** 适用范围可选项：全部取自学籍档案现存值，避免手填与学生档案字面不一致导致匹配不到 */
export interface ScopeOptions {
  grades: string[]
  colleges: string[]
  majors: Array<{ college: string; major: string }>
}

export interface StudentOption {
  id: number
  studentNo: string
  realName: string
  college: string
  major: string
  grade: number
}

export interface ScopePreview {
  studentCount: number
  text: string
}

export const pageItems = (params: ItemQuery) =>
  service.get<unknown, PageData<PaymentItem>>('/v1/payment-items', { params })

export const availableItems = (params: AvailableQuery) =>
  service.get<unknown, PageData<PaymentItem>>('/v1/payment-items/available', { params })

export const getItem = (id: number) => service.get<unknown, PaymentItem>(`/v1/payment-items/${id}`)

export const createItem = (data: ItemPayload) => service.post<unknown, PaymentItem>('/v1/payment-items', data)

export const updateItem = (id: number, data: ItemPayload) =>
  service.put<unknown, PaymentItem>(`/v1/payment-items/${id}`, data)

export const getScopeOptions = () => service.get<unknown, ScopeOptions>('/v1/payment-items/scope-options')

/** ids 传入时用于回显已选学生；只传 keyword 时按学号 / 姓名模糊搜索 */
export const searchScopeStudents = (params: { keyword?: string; ids?: string }) =>
  service.get<unknown, StudentOption[]>('/v1/payment-items/scope-students', { params })

/** 发布前预览覆盖人数，0 表示发出去没有任何学生能看到 */
export const previewScope = (data: ScopeRule) =>
  service.post<unknown, ScopePreview>('/v1/payment-items/scope-preview', data)

/** 下架前影响预览：已缴人数与金额、未完成订单数 */
export interface ItemClosePreview {
  itemId: number
  itemName: string
  paidOrderCount: number
  paidStudentCount: number
  paidAmount: string
  unfinishedOrderCount: number
  canClose: boolean
  blockReason?: string
}

export const getClosePreview = (id: number) =>
  service.get<unknown, ItemClosePreview>(`/v1/payment-items/${id}/close-preview`)

export const publishItem = (id: number) => service.post<unknown, void>(`/v1/payment-items/${id}/publish`)

export const closeItem = (id: number) => service.post<unknown, void>(`/v1/payment-items/${id}/close`)
