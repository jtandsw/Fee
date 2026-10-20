<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="项目名称 / 编号" clearable style="width: 220px" @keyup.enter="search" />
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px" @change="search">
          <el-option v-for="(meta, key) in ITEM_STATUS" :key="key" :label="meta.label" :value="key" />
        </el-select>
        <el-select v-model="query.category" placeholder="全部类别" clearable style="width: 130px" @change="search">
          <el-option v-for="(label, key) in CATEGORY_LABEL" :key="key" :label="label" :value="key" />
        </el-select>
        <el-input v-model="query.term" placeholder="学年学期，如 2026-2027-1" clearable style="width: 200px" @keyup.enter="search" />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <div class="toolbar-right">
          <el-button type="primary" :icon="Plus" @click="openCreate">新建项目</el-button>
        </div>
      </div>
    </div>

    <div class="card">
      <el-table v-loading="loading" :data="list" empty-text="暂无项目" stripe>
        <el-table-column prop="itemNo" label="项目编号" width="130" />
        <el-table-column prop="itemName" label="项目名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="类别" width="100">
          <template #default="{ row }">{{ CATEGORY_LABEL[row.category] || row.category }}</template>
        </el-table-column>
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }"><MoneyText :amount="row.amount" /></template>
        </el-table-column>
        <el-table-column label="适用范围" min-width="150">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ TARGET_SCOPE_LABEL[row.targetScope] || row.targetScope }}</el-tag>
            <span v-if="row.scopeValue?.length" class="muted scope-text">{{ row.scopeValue.join('、') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="缴费时间窗" width="190">
          <template #default="{ row }">
            <div class="muted">{{ formatDate(row.startTime) }}</div>
            <div class="muted">至 {{ formatDate(row.endTime) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><StatusTag :status="row.status" type="item" /></template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT'" link type="primary" @click="openEdit(row as PaymentItem)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="success" @click="publish(row as PaymentItem)">发布</el-button>
            <el-button v-if="row.status === 'PUBLISHED'" link type="warning" @click="close(row as PaymentItem)">下架</el-button>
            <el-button link @click="showDetail(row as PaymentItem)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="load"
          @size-change="search"
        />
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑缴费项目' : '新建缴费项目'" width="640px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="项目名称" prop="itemName">
          <el-input v-model="form.itemName" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="缴费类别" prop="category">
          <el-select v-model="form.category" placeholder="请选择" style="width: 100%">
            <el-option v-for="(label, key) in CATEGORY_LABEL" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="学年学期" prop="term">
          <el-input v-model="form.term" placeholder="如 2026-2027-1" />
        </el-form-item>
        <el-form-item label="应缴金额" prop="amount">
          <el-input v-model="form.amount" placeholder="如 5800.00">
            <template #prepend>￥</template>
          </el-input>
        </el-form-item>
        <el-form-item label="允许分次缴纳" prop="allowPartial">
          <el-switch v-model="form.allowPartial" />
          <span class="muted switch-tip">开启后学生可分多次缴纳直至累计达应缴额</span>
        </el-form-item>
        <el-form-item label="适用范围" prop="targetScope">
          <el-select v-model="form.targetScope" style="width: 100%" @change="onScopeChange">
            <el-option v-for="(label, key) in TARGET_SCOPE_LABEL" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.targetScope !== 'ALL'" label="范围值" prop="scopeValue">
          <el-select
            v-model="form.scopeValue"
            multiple
            filterable
            allow-create
            default-first-option
            style="width: 100%"
            :placeholder="scopePlaceholder"
          />
        </el-form-item>
        <el-form-item label="缴费时间窗" prop="timeRange">
          <el-date-picker
            v-model="form.timeRange"
            type="datetimerange"
            value-format="YYYY-MM-DD HH:mm:ss"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="截止时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="缴费说明">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="项目详情" size="520px">
      <el-descriptions v-if="activeItem" :column="1" border>
        <el-descriptions-item label="项目编号">{{ activeItem.itemNo }}</el-descriptions-item>
        <el-descriptions-item label="项目名称">{{ activeItem.itemName }}</el-descriptions-item>
        <el-descriptions-item label="类别">{{ CATEGORY_LABEL[activeItem.category] }}</el-descriptions-item>
        <el-descriptions-item label="学年学期">{{ activeItem.term }}</el-descriptions-item>
        <el-descriptions-item label="应缴金额"><MoneyText :amount="activeItem.amount" /></el-descriptions-item>
        <el-descriptions-item label="允许分次">{{ activeItem.allowPartial === 1 ? '是' : '否' }}</el-descriptions-item>
        <el-descriptions-item label="适用范围">
          {{ TARGET_SCOPE_LABEL[activeItem.targetScope] }}
          <span v-if="activeItem.scopeValue?.length">（{{ activeItem.scopeValue.join('、') }}）</span>
        </el-descriptions-item>
        <el-descriptions-item label="时间窗">
          {{ formatDateTime(activeItem.startTime) }} 至 {{ formatDateTime(activeItem.endTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="状态"><StatusTag :status="activeItem.status" type="item" /></el-descriptions-item>
        <el-descriptions-item label="缴费说明">{{ activeItem.description || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import MoneyText from '@/components/MoneyText.vue'
import StatusTag from '@/components/StatusTag.vue'
import * as itemApi from '@/api/item'
import { formatDate, formatDateTime } from '@/utils/format'
import { CATEGORY_LABEL, ITEM_STATUS, TARGET_SCOPE_LABEL } from '@/utils/status'
import type { PaymentItem } from '@/types'

const loading = ref(false)
const submitting = ref(false)
const list = ref<PaymentItem[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const detailVisible = ref(false)
const editingId = ref<number | null>(null)
const activeItem = ref<PaymentItem | null>(null)
const formRef = ref<FormInstance>()

const query = reactive({
  keyword: '',
  status: '',
  category: '',
  term: '',
  pageNum: 1,
  pageSize: 10
})

const form = reactive({
  itemName: '',
  category: 'TUITION',
  term: '',
  amount: '',
  allowPartial: false,
  targetScope: 'ALL',
  scopeValue: [] as string[],
  timeRange: [] as string[],
  description: ''
})

const scopePlaceholder = computed(() => {
  const map: Record<string, string> = {
    GRADE: '输入年级后回车，如 2023',
    COLLEGE: '输入学院后回车，如 计算机学院',
    MAJOR: '输入专业后回车，如 软件工程',
    CUSTOM: '输入学生 student.id 后回车'
  }
  return map[form.targetScope] || '输入后回车'
})

const rules: FormRules = {
  itemName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择缴费类别', trigger: 'change' }],
  term: [{ required: true, message: '请输入学年学期', trigger: 'blur' }],
  amount: [
    { required: true, message: '请输入应缴金额', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        const num = Number(value)
        if (Number.isNaN(num) || num <= 0) {
          callback(new Error('金额必须为大于 0 的数字'))
        } else if (!/^\d+(\.\d{1,2})?$/.test(String(value))) {
          callback(new Error('金额最多两位小数'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  targetScope: [{ required: true, message: '请选择适用范围', trigger: 'change' }],
  scopeValue: [
    {
      validator: (_rule, value, callback) => {
        if (form.targetScope !== 'ALL' && (!value || value.length === 0)) {
          callback(new Error('请填写适用范围值'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
  timeRange: [
    {
      validator: (_rule, value, callback) => {
        if (!value || value.length !== 2) {
          callback(new Error('请选择完整的缴费时间窗'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ]
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await itemApi.pageItems({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      status: query.status || undefined,
      category: query.category || undefined,
      term: query.term || undefined
    })
    list.value = page.list
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function search(): void {
  query.pageNum = 1
  load()
}

function reset(): void {
  query.keyword = ''
  query.status = ''
  query.category = ''
  query.term = ''
  query.pageNum = 1
  load()
}

function resetForm(): void {
  form.itemName = ''
  form.category = 'TUITION'
  form.term = ''
  form.amount = ''
  form.allowPartial = false
  form.targetScope = 'ALL'
  form.scopeValue = []
  form.timeRange = []
  form.description = ''
  formRef.value?.clearValidate()
}

function openCreate(): void {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(item: PaymentItem): void {
  editingId.value = item.id
  form.itemName = item.itemName
  form.category = item.category
  form.term = item.term
  form.amount = item.amount
  form.allowPartial = item.allowPartial === 1
  form.targetScope = item.targetScope
  form.scopeValue = item.scopeValue ? [...item.scopeValue] : []
  form.timeRange = [item.startTime, item.endTime]
  form.description = item.description || ''
  dialogVisible.value = true
}

function onScopeChange(): void {
  form.scopeValue = []
}

async function submit(): Promise<void> {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  submitting.value = true
  try {
    const payload: itemApi.ItemPayload = {
      itemName: form.itemName,
      category: form.category,
      term: form.term,
      amount: form.amount,
      allowPartial: form.allowPartial ? 1 : 0,
      targetScope: form.targetScope,
      scopeValue: form.targetScope === 'ALL' ? undefined : form.scopeValue,
      startTime: form.timeRange[0],
      endTime: form.timeRange[1],
      description: form.description || undefined
    }
    if (editingId.value) {
      await itemApi.updateItem(editingId.value, payload)
      ElMessage.success('项目已更新')
    } else {
      await itemApi.createItem(payload)
      ElMessage.success('项目已创建（草稿）')
    }
    dialogVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function publish(item: PaymentItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定发布「${item.itemName}」吗？发布后学生即可缴费。`, '发布确认', { type: 'warning' })
  } catch {
    return
  }
  await itemApi.publishItem(item.id)
  ElMessage.success('项目已发布')
  load()
}

async function close(item: PaymentItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`下架后不可回退，确定下架「${item.itemName}」吗？`, '下架确认', { type: 'warning' })
  } catch {
    return
  }
  await itemApi.closeItem(item.id)
  ElMessage.success('项目已下架')
  load()
}

function showDetail(item: PaymentItem): void {
  activeItem.value = item
  detailVisible.value = true
}

onMounted(load)
</script>

<style scoped>
.scope-text {
  margin-left: 6px;
}

.switch-tip {
  margin-left: 10px;
}
</style>
