import service from '@/utils/request'
import type { Order, OrderCreateResult, PageData, PageQuery, PayResult, RefundResult } from '@/types'

export interface OrderQuery extends PageQuery {
  status?: string
  itemId?: number
  channel?: string
  /** 学号（精确到人的订单筛选，财务最常用） */
  studentNo?: string
  /** 订单号 / 学号 / 姓名 / 流水号 混合搜索 */
  keyword?: string
}

export interface CreateOrderPayload {
  itemId: number
  channel?: string
  /** 本次缴纳金额，仅支持分次缴纳的项目可小于剩余应缴；留空表示缴清剩余 */
  amount?: string
}

export const createOrder = (data: CreateOrderPayload, idempotencyKey: string) =>
  service.post<unknown, OrderCreateResult>('/v1/orders', data, {
    headers: { 'Idempotency-Key': idempotencyKey }
  })

export const pageOrders = (params: OrderQuery) => service.get<unknown, PageData<Order>>('/v1/orders', { params })

export const getOrder = (orderNo: string) => service.get<unknown, Order>(`/v1/orders/${orderNo}`)

/** 收银台可选支付渠道（available=false 表示未接入，前端置灰） */
export interface ChannelOption {
  value: string
  label: string
  available: boolean
  remark?: string
}

export const getPayChannels = () => service.get<unknown, ChannelOption[]>('/v1/orders/channels')

export const payOrder = (orderNo: string, channel?: string) =>
  service.post<unknown, PayResult>(`/v1/orders/${orderNo}/pay`, undefined, {
    params: channel ? { channel } : undefined
  })

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
