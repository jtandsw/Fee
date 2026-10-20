import dayjs from 'dayjs'

/** 金额展示：两位小数 + 千分位（README 12.3 第 2 条） */
export function formatAmount(value?: string | number | null): string {
  if (value === null || value === undefined || value === '') {
    return '0.00'
  }
  const num = typeof value === 'number' ? value : Number(value)
  if (Number.isNaN(num)) {
    return String(value)
  }
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export function formatDateTime(value?: string | null): string {
  if (!value) {
    return '-'
  }
  return dayjs(value).isValid() ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : value
}

export function formatDate(value?: string | null): string {
  if (!value) {
    return '-'
  }
  return dayjs(value).isValid() ? dayjs(value).format('YYYY-MM-DD') : value
}

export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.rel = 'noopener'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  // 不能立即 revoke：下载是异步的，立刻释放 object URL 会让部分浏览器（含无头模式）取消下载
  setTimeout(() => URL.revokeObjectURL(url), 10_000)
}

export function openBlob(blob: Blob): void {
  const url = URL.createObjectURL(blob)
  window.open(url, '_blank')
  setTimeout(() => URL.revokeObjectURL(url), 60_000)
}
