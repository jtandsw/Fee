import service from '@/utils/request'
import type { ImportResult, OperationLog, PageData, PageQuery, UserInfo } from '@/types'

export interface UserQuery extends PageQuery {
  role?: string
  status?: number
  keyword?: string
}

export interface UserPayload {
  username: string
  realName: string
  role: string
  phone?: string
  email?: string
  password?: string
  studentNo?: string
  college?: string
  major?: string
  className?: string
  grade?: number
}

export interface LogQuery extends PageQuery {
  username?: string
  module?: string
  startDate?: string
  endDate?: string
}

export const pageUsers = (params: UserQuery) =>
  service.get<unknown, PageData<UserInfo>>('/v1/users', { params })

export const createUser = (data: UserPayload) => service.post<unknown, UserInfo>('/v1/users', data)

export const updateUser = (id: number, data: UserPayload) =>
  service.put<unknown, UserInfo>(`/v1/users/${id}`, data)

export const updateUserStatus = (id: number, status: number) =>
  service.put<unknown, void>(`/v1/users/${id}/status`, { status })

export const resetUserPassword = (id: number, newPassword: string) =>
  service.put<unknown, void>(`/v1/users/${id}/reset-password`, { newPassword })

export const importStudents = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return service.post<unknown, ImportResult>('/v1/students/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export const pageLogs = (params: LogQuery) =>
  service.get<unknown, PageData<OperationLog>>('/v1/logs', { params })
