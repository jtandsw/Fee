import service from '@/utils/request'
import type { Order, OrderCreateResult, PageData, PageQuery, PayResult, RefundResult } from '@/types'

export interface OrderQuery extends PageQuery {
  status?: string
  itemId?: number
  channel?: string
  keyword?: string
}

export const createOrder = (data: { itemId: number; channel?: string }, idempotencyKey: string) =>
  service.post<unknown, OrderCreateResult>('/v1/orders', data, {
    headers: { 'Idempotency-Key': idempotencyKey }
  })

export const pageOrders = (params: OrderQuery) => service.get<unknown, PageData<Order>>('/v1/orders', { params })

export const getOrder = (orderNo: string) => service.get<unknown, Order>(`/v1/orders/${orderNo}`)

export const payOrder = (orderNo: string) => service.post<unknown, PayResult>(`/v1/orders/${orderNo}/pay`)

export const closeOrder = (orderNo: string) => service.post<unknown, void>(`/v1/orders/${orderNo}/close`)

export const refundOrder = (orderNo: string, data: { amount: string; reason: string }) =>
  service.post<unknown, RefundResult>(`/v1/orders/${orderNo}/refund`, data)

/** 模拟渠道回调，仅用于演示环境（README 10.5） */
export const mockPayCallback = (data: {
  orderNo: string
  transactionNo: string
  amount: string
  status: 'SUCCESS' | 'FAILED'
}) => service.post<unknown, void>('/v1/pay/callback/mock', data)

export const getVoucher = (orderNo: string) =>
  service.get<unknown, Blob>(`/v1/orders/${orderNo}/voucher`, { responseType: 'blob' })
