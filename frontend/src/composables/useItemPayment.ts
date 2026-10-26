import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as orderApi from '@/api/order'
import { formatAmount } from '@/utils/format'
import { newIdempotencyKey } from '@/utils/idempotency'
import type { PaymentItem } from '@/types'

/**
 * 学生发起缴费的统一入口。
 * <p>
 * 支持分次缴纳的项目先弹窗确认本次金额（默认缴清剩余），其余项目直接按剩余应缴建单。
 */
export function useItemPayment() {
  const router = useRouter()

  /** 卡片按钮的行内 loading（直接下单时使用） */
  const payingId = ref<number | null>(null)
  const dialogVisible = ref(false)
  const dialogItem = ref<PaymentItem | null>(null)
  /** 弹窗内确认按钮的 loading */
  const submitting = ref(false)

  async function startPay(item: PaymentItem): Promise<void> {
    const remain = Number(item.remainAmount ?? item.amount ?? 0)
    if (item.allowPartial === 1 && remain > 0) {
      dialogItem.value = item
      dialogVisible.value = true
      return
    }
    await submit(item)
  }

  /** 弹窗确认：amount 为本次缴纳金额（两位小数字符串） */
  async function submitFromDialog(amount: string): Promise<void> {
    if (!dialogItem.value) {
      return
    }
    await submit(dialogItem.value, amount)
  }

  async function submit(item: PaymentItem, amount?: string): Promise<void> {
    if (amount === undefined) {
      payingId.value = item.id
    } else {
      submitting.value = true
    }
    try {
      const order = await orderApi.createOrder({ itemId: item.id, amount }, newIdempotencyKey())
      dialogVisible.value = false
      // 后端在「同项目同金额的未支付订单」存在时会直接复用，提示要跟着变，否则学生会以为生成了两笔
      if (order.reused) {
        ElMessage.info(`已复用未支付的订单 ${order.orderNo}（${formatAmount(order.amount)} 元）`)
      } else {
        ElMessage.success(`订单已创建：${order.orderNo}，本次应缴 ${formatAmount(order.amount)} 元`)
      }
      await router.push({ path: '/student/cashier', query: { orderNo: order.orderNo } })
    } finally {
      payingId.value = null
      submitting.value = false
    }
  }

  return { payingId, dialogVisible, dialogItem, submitting, startPay, submitFromDialog }
}
