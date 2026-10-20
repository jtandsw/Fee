import service from '@/utils/request'
import type { LoginPayload, LoginResult, UserInfo } from '@/types'

export const login = (data: LoginPayload) => service.post<unknown, LoginResult>('/v1/auth/login', data)

export const refreshToken = (refreshTokenValue: string) =>
  service.post<unknown, LoginResult>('/v1/auth/refresh', { refreshToken: refreshTokenValue })

export const logout = (refreshTokenValue: string) =>
  service.post<unknown, void>('/v1/auth/logout', { refreshToken: refreshTokenValue })

export const me = () => service.get<unknown, UserInfo>('/v1/auth/me')

export const changePassword = (data: { oldPassword: string; newPassword: string }) =>
  service.put<unknown, void>('/v1/auth/password', data)
