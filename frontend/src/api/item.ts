import service from '@/utils/request'
import type { PageData, PageQuery, PaymentItem } from '@/types'

export interface ItemQuery extends PageQuery {
  status?: string
  category?: string
  term?: string
  keyword?: string
}

export interface ItemPayload {
  itemName: string
  category: string
  term: string
  amount: string | number
  allowPartial: number
  targetScope: string
  scopeValue?: string[]
  startTime: string
  endTime: string
  description?: string
}

export interface AvailableQuery extends PageQuery {
  /** ALL(默认) / UNPAID 未缴清 / PAID 已缴清 */
  payFilter?: 'ALL' | 'UNPAID' | 'PAID'
}

export const pageItems = (params: ItemQuery) =>
  service.get<unknown, PageData<PaymentItem>>('/v1/payment-items', { params })

export const availableItems = (params: AvailableQuery) =>
  service.get<unknown, PageData<PaymentItem>>('/v1/payment-items/available', { params })

export const getItem = (id: number) => service.get<unknown, PaymentItem>(`/v1/payment-items/${id}`)

export const createItem = (data: ItemPayload) => service.post<unknown, PaymentItem>('/v1/payment-items', data)

export const updateItem = (id: number, data: ItemPayload) =>
  service.put<unknown, PaymentItem>(`/v1/payment-items/${id}`, data)

export const publishItem = (id: number) => service.post<unknown, void>(`/v1/payment-items/${id}/publish`)

export const closeItem = (id: number) => service.post<unknown, void>(`/v1/payment-items/${id}/close`)
