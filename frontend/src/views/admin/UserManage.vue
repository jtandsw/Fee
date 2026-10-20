<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="账号 / 姓名" clearable style="width: 220px" @keyup.enter="search" />
        <el-select v-model="query.role" placeholder="全部角色" clearable style="width: 130px" @change="search">
          <el-option label="学生" value="STUDENT" />
          <el-option label="财务" value="FINANCE" />
          <el-option label="管理员" value="ADMIN" />
        </el-select>
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px" @change="search">
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <div class="toolbar-right">
          <el-button :icon="Upload" @click="importVisible = true">导入学生名单</el-button>
          <el-button type="primary" :icon="Plus" @click="openCreate">新增用户</el-button>
        </div>
      </div>
    </div>

    <div class="card">
      <el-table v-loading="loading" :data="list" empty-text="暂无用户" stripe>
        <el-table-column prop="username" label="登录账号" width="140" />
        <el-table-column prop="realName" label="姓名" width="110" />
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="roleTag(row.role)" effect="light">{{ ROLE_LABEL[row.role] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="学籍信息" min-width="220">
          <template #default="{ row }">
            <span v-if="row.role === 'STUDENT'">
              {{ row.studentNo }} · {{ row.college }} · {{ row.major }} · {{ row.className }}
            </span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              :disabled="row.id === currentUserId"
              @change="toggleStatus(row as UserInfo)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row as UserInfo)">编辑</el-button>
            <el-button link type="warning" @click="resetPassword(row as UserInfo)">重置密码</el-button>
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

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑用户' : '新增用户'" width="600px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="登录账号" prop="username">
          <el-input v-model="form.username" :disabled="!!editingId" placeholder="学号 / 工号" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="form.role" style="width: 100%" :disabled="!!editingId" @change="onRoleChange">
            <el-option label="学生" value="STUDENT" />
            <el-option label="财务" value="FINANCE" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!editingId" label="初始密码" prop="password">
          <el-input v-model="form.password" placeholder="留空默认 123456" show-password />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>

        <template v-if="form.role === 'STUDENT'">
          <el-divider content-position="left">学籍信息</el-divider>
          <el-form-item label="学号" prop="studentNo">
            <el-input v-model="form.studentNo" />
          </el-form-item>
          <el-form-item label="学院" prop="college">
            <el-input v-model="form.college" />
          </el-form-item>
          <el-form-item label="专业" prop="major">
            <el-input v-model="form.major" />
          </el-form-item>
          <el-form-item label="班级" prop="className">
            <el-input v-model="form.className" />
          </el-form-item>
          <el-form-item label="年级" prop="grade">
            <el-input v-model.number="form.grade" placeholder="如 2023" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="importVisible" title="导入学生名单" width="560px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="CSV 表头：学号,姓名,学院,专业,班级,年级"
        description="UTF-8 编码；已存在的学号将跳过；初始密码统一为 123456"
        class="import-alert"
      />
      <el-upload
        drag
        :auto-upload="false"
        :limit="1"
        :on-change="onImportFileChange"
        :on-remove="onImportFileRemove"
        :file-list="importFileList"
        accept=".csv"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽 CSV 到此处，或 <em>点击选择</em></div>
      </el-upload>

      <div v-if="importResult" class="import-result">
        <el-descriptions :column="4" border size="small">
          <el-descriptions-item label="总行数">{{ importResult.total }}</el-descriptions-item>
          <el-descriptions-item label="成功">{{ importResult.success }}</el-descriptions-item>
          <el-descriptions-item label="跳过">{{ importResult.skipped }}</el-descriptions-item>
          <el-descriptions-item label="失败">{{ importResult.failed }}</el-descriptions-item>
        </el-descriptions>
        <ul v-if="importResult.messages.length" class="import-messages">
          <li v-for="(message, index) in importResult.messages" :key="index">{{ message }}</li>
        </ul>
      </div>

      <template #footer>
        <el-button @click="importVisible = false">关闭</el-button>
        <el-button type="primary" :loading="importing" @click="submitImport">开始导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadFile, type UploadFiles } from 'element-plus'
import { Plus, Upload, UploadFilled } from '@element-plus/icons-vue'
import * as userApi from '@/api/user'
import { useUserStore } from '@/stores/user'
import type { ImportResult, UserInfo } from '@/types'

const ROLE_LABEL: Record<string, string> = { STUDENT: '学生', FINANCE: '财务', ADMIN: '管理员' }
const userStore = useUserStore()
const currentUserId = userStore.user?.id ?? 0

const loading = ref(false)
const submitting = ref(false)
const importing = ref(false)
const list = ref<UserInfo[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const importVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const importResult = ref<ImportResult | null>(null)
const importFile = ref<File | null>(null)
const importFileList = ref<UploadFile[]>([])

const query = reactive({
  keyword: '',
  role: '',
  status: undefined as number | undefined,
  pageNum: 1,
  pageSize: 10
})

const form = reactive({
  username: '',
  realName: '',
  role: 'STUDENT',
  password: '',
  phone: '',
  email: '',
  studentNo: '',
  college: '',
  major: '',
  className: '',
  grade: undefined as number | undefined
})

const rules = computed<FormRules>(() => {
  const base: FormRules = {
    username: [{ required: true, message: '请输入登录账号', trigger: 'blur' }],
    realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
    role: [{ required: true, message: '请选择角色', trigger: 'change' }]
  }
  if (form.role === 'STUDENT') {
    base.studentNo = [{ required: true, message: '请输入学号', trigger: 'blur' }]
    base.college = [{ required: true, message: '请输入学院', trigger: 'blur' }]
    base.major = [{ required: true, message: '请输入专业', trigger: 'blur' }]
    base.className = [{ required: true, message: '请输入班级', trigger: 'blur' }]
    base.grade = [{ required: true, message: '请输入年级', trigger: 'blur' }]
  }
  return base
})

function roleTag(role: string): 'primary' | 'success' | 'info' {
  if (role === 'ADMIN') {
    return 'primary'
  }
  return role === 'FINANCE' ? 'success' : 'info'
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await userApi.pageUsers({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      role: query.role || undefined,
      status: query.status
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
  query.role = ''
  query.status = undefined
  query.pageNum = 1
  load()
}

function openCreate(): void {
  editingId.value = null
  Object.assign(form, {
    username: '',
    realName: '',
    role: 'STUDENT',
    password: '',
    phone: '',
    email: '',
    studentNo: '',
    college: '',
    major: '',
    className: '',
    grade: undefined
  })
  dialogVisible.value = true
}

function openEdit(row: UserInfo): void {
  editingId.value = row.id
  Object.assign(form, {
    username: row.username,
    realName: row.realName,
    role: row.role,
    password: '',
    phone: row.phone ?? '',
    email: row.email ?? '',
    studentNo: row.studentNo ?? '',
    college: row.college ?? '',
    major: row.major ?? '',
    className: row.className ?? '',
    grade: row.grade
  })
  dialogVisible.value = true
}

function onRoleChange(): void {
  if (form.role !== 'STUDENT') {
    form.studentNo = ''
    form.college = ''
    form.major = ''
    form.className = ''
    form.grade = undefined
  }
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
    const payload = {
      username: form.username,
      realName: form.realName,
      role: form.role,
      password: form.password || undefined,
      phone: form.phone || undefined,
      email: form.email || undefined,
      studentNo: form.role === 'STUDENT' ? form.studentNo : undefined,
      college: form.role === 'STUDENT' ? form.college : undefined,
      major: form.role === 'STUDENT' ? form.major : undefined,
      className: form.role === 'STUDENT' ? form.className : undefined,
      grade: form.role === 'STUDENT' ? form.grade : undefined
    }
    if (editingId.value) {
      await userApi.updateUser(editingId.value, payload)
      ElMessage.success('用户已更新')
    } else {
      await userApi.createUser(payload)
      ElMessage.success('用户已创建')
    }
    dialogVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(row: UserInfo): Promise<void> {
  const next = row.status
  try {
    await ElMessageBox.confirm(
      `确定${next === 1 ? '启用' : '禁用'}账号 ${row.username} 吗？`,
      '提示',
      { type: 'warning' }
    )
  } catch {
    row.status = next === 1 ? 0 : 1
    return
  }
  try {
    await userApi.updateUserStatus(row.id, next ?? 0)
    ElMessage.success('状态已更新')
  } catch {
    row.status = next === 1 ? 0 : 1
  }
}

async function resetPassword(row: UserInfo): Promise<void> {
  try {
    const { value } = await ElMessageBox.prompt(`为 ${row.username} 设置新密码`, '重置密码', {
      inputType: 'password',
      inputPattern: /^.{6,32}$/,
      inputErrorMessage: '密码长度需为 6-32 位'
    })
    await userApi.resetUserPassword(row.id, value)
    ElMessage.success('密码已重置')
  } catch {
    // 取消或校验失败
  }
}

function onImportFileChange(file: UploadFile, _files: UploadFiles): void {
  importFileList.value = [file]
  importFile.value = file.raw ?? null
}

function onImportFileRemove(): void {
  importFileList.value = []
  importFile.value = null
}

async function submitImport(): Promise<void> {
  if (!importFile.value) {
    ElMessage.warning('请选择 CSV 文件')
    return
  }
  importing.value = true
  try {
    importResult.value = await userApi.importStudents(importFile.value)
    ElMessage.success('导入完成')
    load()
  } finally {
    importing.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.import-alert {
  margin-bottom: 16px;
}

.import-result {
  margin-top: 16px;
}

.import-messages {
  margin: 10px 0 0;
  padding-left: 18px;
  max-height: 160px;
  overflow-y: auto;
  color: #d9534f;
  font-size: 13px;
  line-height: 1.8;
}
</style>
