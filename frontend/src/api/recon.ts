import service from '@/utils/request'
import type { PageData, PageQuery, ReconBatch, ReconDetail } from '@/types'

export const pageBatches = (params: PageQuery) =>
  service.get<unknown, PageData<ReconBatch>>('/v1/reconciliations', { params })

export const createBatch = (channel: string, billDate: string, file: File) => {
  const formData = new FormData()
  formData.append('channel', channel)
  formData.append('billDate', billDate)
  formData.append('file', file)
  return service.post<unknown, ReconBatch>('/v1/reconciliations', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export const matchBatch = (batchNo: string) =>
  service.post<unknown, ReconBatch>(`/v1/reconciliations/${batchNo}/match`)

export const pageDetails = (batchNo: string, params: PageQuery & { diffType?: string }) =>
  service.get<unknown, PageData<ReconDetail>>(`/v1/reconciliations/${batchNo}/details`, { params })

export const handleDetail = (id: number, data: { handleStatus: string; handleRemark?: string }) =>
  service.put<unknown, void>(`/v1/reconciliations/details/${id}/handle`, data)
