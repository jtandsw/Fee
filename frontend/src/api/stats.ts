import service, { download } from '@/utils/request'
import type { CollegeStat, ItemStat, OverviewStat, TrendPoint } from '@/types'

export const overview = (params?: { itemId?: number }) =>
  service.get<unknown, OverviewStat>('/v1/stats/overview', { params })

export const byItem = (params?: { term?: string }) =>
  service.get<unknown, ItemStat[]>('/v1/stats/by-item', { params })

export const byCollege = () => service.get<unknown, CollegeStat[]>('/v1/stats/by-college')

export const trend = (params?: { start?: string; end?: string; granularity?: 'day' | 'month' }) =>
  service.get<unknown, TrendPoint[]>('/v1/stats/trend', { params })

export const exportStats = () => download('/v1/stats/export')
