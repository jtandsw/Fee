<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <div>
          <h3 class="card-title">通知管理</h3>
          <p class="muted">通知按角色定向发布，学生端可见 STUDENT 与 ALL 的通知</p>
        </div>
        <div class="toolbar-right">
          <el-button type="primary" :icon="Plus" @click="openCreate">新建通知</el-button>
          <el-button :icon="Refresh" @click="load">刷新</el-button>
        </div>
      </div>
    </div>

    <div class="card">
      <el-table v-loading="loading" :data="list" empty-text="暂无通知" stripe>
        <el-table-column prop="title" label="标题" min-width="240" show-overflow-tooltip />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">{{ NOTICE_TYPE[row.noticeType] || row.noticeType }}</template>
        </el-table-column>
        <el-table-column label="目标角色" width="110">
          <template #default="{ row }">{{ TARGET_ROLE[row.targetRole] || row.targetRole }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'PUBLISHED' ? 'success' : 'info'" effect="light">
              {{ row.status === 'PUBLISHED' ? '已发布' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发布时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.publishAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link @click="showDetail(row as Notice)">查看</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="primary" @click="publish(row as Notice)">发布</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="load"
          @size-change="onSizeChange"
        />
      </div>
    </div>

    <el-dialog v-model="createVisible" title="新建通知" width="580px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="类型" prop="noticeType">
          <el-select v-model="form.noticeType" style="width: 100%">
            <el-option label="系统通知" value="SYSTEM" />
            <el-option label="缴费通知" value="PAYMENT" />
            <el-option label="截止提醒" value="DEADLINE" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标角色" prop="targetRole">
          <el-select v-model="form.targetRole" style="width: 100%">
            <el-option label="学生" value="STUDENT" />
            <el-option label="财务" value="FINANCE" />
            <el-option label="管理员" value="ADMIN" />
            <el-option label="全部" value="ALL" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联项目">
          <el-input v-model.number="form.itemId" placeholder="可选，缴费项目 id" />
        </el-form-item>
        <el-form-item label="过期时间">
          <el-date-picker
            v-model="form.expireAt"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="可选，到期后自动隐藏"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="6" maxlength="2000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存草稿</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" :title="activeNotice?.title" width="600px">
      <div class="notice-meta muted">
        {{ TARGET_ROLE[activeNotice?.targetRole || ''] }} · {{ formatDateTime(activeNotice?.publishAt) }}
      </div>
      <div class="notice-body">{{ activeNotice?.content }}</div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import * as noticeApi from '@/api/notice'
import { formatDateTime } from '@/utils/format'
import type { Notice } from '@/types'

const NOTICE_TYPE: Record<string, string> = { SYSTEM: '系统通知', PAYMENT: '缴费通知', DEADLINE: '截止提醒' }
const TARGET_ROLE: Record<string, string> = { STUDENT: '学生', FINANCE: '财务', ADMIN: '管理员', ALL: '全部' }

const loading = ref(false)
const submitting = ref(false)
const list = ref<Notice[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const createVisible = ref(false)
const detailVisible = ref(false)
const activeNotice = ref<Notice | null>(null)
const formRef = ref<FormInstance>()

const form = reactive({
  title: '',
  noticeType: 'SYSTEM',
  targetRole: 'STUDENT',
  itemId: undefined as number | undefined,
  expireAt: '',
  content: ''
})

const rules: FormRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入通知内容', trigger: 'blur' }],
  noticeType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  targetRole: [{ required: true, message: '请选择目标角色', trigger: 'change' }]
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await noticeApi.pageNotices({ pageNum: pageNum.value, pageSize: pageSize.value })
    list.value = page.list
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function onSizeChange(): void {
  pageNum.value = 1
  load()
}

function openCreate(): void {
  form.title = ''
  form.noticeType = 'SYSTEM'
  form.targetRole = 'STUDENT'
  form.itemId = undefined
  form.expireAt = ''
  form.content = ''
  createVisible.value = true
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
    await noticeApi.createNotice({
      title: form.title,
      content: form.content,
      noticeType: form.noticeType,
      targetRole: form.targetRole,
      itemId: form.itemId || undefined,
      expireAt: form.expireAt || undefined
    })
    ElMessage.success('通知已保存为草稿')
    createVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function publish(notice: Notice): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定发布「${notice.title}」吗？`, '发布确认', { type: 'warning' })
  } catch {
    return
  }
  await noticeApi.publishNotice(notice.id)
  ElMessage.success('通知已发布')
  load()
}

function showDetail(notice: Notice): void {
  activeNotice.value = notice
  detailVisible.value = true
}

onMounted(load)
</script>

<style scoped>
.notice-meta {
  margin-bottom: 12px;
}

.notice-body {
  white-space: pre-wrap;
  line-height: 1.9;
  color: #303133;
}
</style>
