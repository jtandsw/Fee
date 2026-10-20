import service from '@/utils/request'
import type { Notice, PageData, PageQuery } from '@/types'

export const pageNotices = (params: PageQuery) =>
  service.get<unknown, PageData<Notice>>('/v1/notices', { params })

export const getNotice = (id: number) => service.get<unknown, Notice>(`/v1/notices/${id}`)

export const unreadCount = () => service.get<unknown, number>('/v1/notices/unread-count')

export const createNotice = (data: {
  title: string
  content: string
  noticeType: string
  targetRole: string
  itemId?: number
  expireAt?: string
}) => service.post<unknown, Notice>('/v1/notices', data)

export const publishNotice = (id: number) => service.post<unknown, void>(`/v1/notices/${id}/publish`)
