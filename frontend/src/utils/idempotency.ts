/** 生成下单幂等键（README 10.1：Idempotency-Key 为 uuid） */
export function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `idem-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`
}
