/** 状态字典：仅用于展示，不作为权限或业务判断依据 */
export const ORDER_STATUS: Record<string, { label: string; type: 'primary' | 'success' | 'warning' | 'info' | 'danger' }> = {
  PENDING: { label: '待支付', type: 'warning' },
  PAYING: { label: '支付中', type: 'primary' },
  PAID: { label: '已支付', type: 'success' },
  CLOSED: { label: '已关闭', type: 'info' },
  REFUNDED: { label: '已退款', type: 'danger' },
  PARTIAL_REFUND: { label: '部分退款', type: 'danger' }
}

export const ITEM_STATUS: Record<string, { label: string; type: 'primary' | 'success' | 'warning' | 'info' | 'danger' }> = {
  DRAFT: { label: '草稿', type: 'info' },
  PUBLISHED: { label: '已发布', type: 'success' },
  CLOSED: { label: '已下架', type: 'warning' }
}

/** 学生对某个缴费项目的缴费进度（/payment-items/available 返回 payStatus） */
export const PAY_STATUS: Record<string, { label: string; type: 'primary' | 'success' | 'warning' | 'info' | 'danger' }> = {
  UNPAID: { label: '未缴费', type: 'info' },
  PARTIAL: { label: '部分缴费', type: 'warning' },
  PAID: { label: '已缴清', type: 'success' }
}

export const BATCH_STATUS: Record<string, { label: string; type: 'primary' | 'success' | 'warning' | 'info' | 'danger' }> = {
  PENDING: { label: '待匹配', type: 'info' },
  PROCESSING: { label: '处理中', type: 'warning' },
  FINISHED: { label: '已归档', type: 'success' }
}

export const DIFF_TYPE: Record<string, { label: string; type: 'primary' | 'success' | 'warning' | 'info' | 'danger' }> = {
  MATCH: { label: '一致', type: 'success' },
  LOCAL_ONLY: { label: '本地有渠道无', type: 'warning' },
  CHANNEL_ONLY: { label: '渠道有本地无', type: 'danger' },
  AMOUNT_DIFF: { label: '金额不符', type: 'warning' }
}

export const HANDLE_STATUS: Record<string, string> = {
  UNHANDLED: '未处理',
  HANDLED: '已处理',
  IGNORED: '已忽略'
}

export const CATEGORY_LABEL: Record<string, string> = {
  TUITION: '学费',
  ACCOMMODATION: '住宿费',
  BOOK: '教材费',
  EXAM: '考试费',
  OTHER: '其他'
}

export const CHANNEL_LABEL: Record<string, string> = {
  MOCK: '模拟支付',
  WECHAT: '微信支付',
  ALIPAY: '支付宝',
  UNIONPAY: '银联',
  CASH: '现金'
}

export const TARGET_SCOPE_LABEL: Record<string, string> = {
  ALL: '全校',
  GRADE: '年级',
  COLLEGE: '学院',
  MAJOR: '专业',
  CUSTOM: '指定名单'
}
