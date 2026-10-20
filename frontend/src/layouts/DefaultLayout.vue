<template>
  <el-container class="layout">
    <el-aside :width="appStore.sidebarCollapsed ? '64px' : '220px'" class="aside">
      <div class="logo">
        <div class="logo-mark">缴</div>
        <span v-if="!appStore.sidebarCollapsed" class="logo-text">大学生缴费系统</span>
      </div>
      <el-scrollbar class="menu-scroll">
        <SideMenu :collapsed="appStore.sidebarCollapsed" />
      </el-scrollbar>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="appStore.toggleSidebar()">
            <Expand v-if="appStore.sidebarCollapsed" />
            <Fold v-else />
          </el-icon>
          <span class="page-title">{{ route.meta.title }}</span>
        </div>
        <div class="header-right">
          <el-badge :value="unread" :hidden="unread === 0" :max="99">
            <el-button text circle @click="openNotices">
              <el-icon :size="18"><Bell /></el-icon>
            </el-button>
          </el-badge>
          <el-dropdown @command="onCommand">
            <span class="user-entry">
              <el-avatar :size="28">{{ userStore.displayName.slice(0, 1) }}</el-avatar>
              <span class="user-name">{{ userStore.displayName }}</span>
              <el-tag size="small" type="info">{{ roleLabel }}</el-tag>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="password">修改密码</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>

  <el-drawer v-model="noticeVisible" title="站内通知" size="420px">
    <div v-loading="noticeLoading">
      <el-empty v-if="notices.length === 0" description="暂无通知" />
      <div v-for="notice in notices" :key="notice.id" class="notice-item" @click="readNotice(notice)">
        <div class="notice-title">
          <el-badge is-dot :hidden="notice.read" />
          <span>{{ notice.title }}</span>
        </div>
        <div class="notice-meta">{{ notice.publishAt || notice.createdAt }}</div>
      </div>
    </div>
  </el-drawer>

  <el-dialog v-model="noticeDetailVisible" :title="currentNotice?.title" width="560px">
    <div class="notice-content">{{ currentNotice?.content }}</div>
  </el-dialog>

  <el-dialog v-model="passwordVisible" title="修改密码" width="420px" @closed="resetPasswordForm">
    <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="88px">
      <el-form-item label="原密码" prop="oldPassword">
        <el-input v-model="passwordForm.oldPassword" type="password" show-password />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input v-model="passwordForm.newPassword" type="password" show-password />
      </el-form-item>
      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="passwordVisible = false">取消</el-button>
      <el-button type="primary" :loading="passwordSubmitting" @click="submitPassword">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { ArrowDown, Bell, Expand, Fold } from '@element-plus/icons-vue'
import SideMenu from './components/SideMenu.vue'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import * as noticeApi from '@/api/notice'
import * as authApi from '@/api/auth'
import type { Notice } from '@/types'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

const roleLabel = computed(() => {
  const map: Record<string, string> = { STUDENT: '学生', FINANCE: '财务', ADMIN: '管理员' }
  return map[userStore.role] ?? userStore.role
})

const unread = ref(0)
const noticeVisible = ref(false)
const noticeLoading = ref(false)
const notices = ref<Notice[]>([])
const currentNotice = ref<Notice | null>(null)
const noticeDetailVisible = ref(false)

async function loadUnread(): Promise<void> {
  try {
    unread.value = await noticeApi.unreadCount()
  } catch {
    unread.value = 0
  }
}

async function openNotices(): Promise<void> {
  noticeVisible.value = true
  noticeLoading.value = true
  try {
    const page = await noticeApi.pageNotices({ pageNum: 1, pageSize: 20 })
    notices.value = page.list
  } finally {
    noticeLoading.value = false
  }
}

async function readNotice(notice: Notice): Promise<void> {
  currentNotice.value = await noticeApi.getNotice(notice.id)
  noticeDetailVisible.value = true
  await loadUnread()
  await openNotices()
}

const passwordVisible = ref(false)
const passwordSubmitting = ref(false)
const passwordFormRef = ref<FormInstance>()
const passwordForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })

const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度 6-32 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== passwordForm.value.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

function resetPasswordForm(): void {
  passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
  passwordFormRef.value?.clearValidate()
}

async function submitPassword(): Promise<void> {
  if (!passwordFormRef.value) {
    return
  }
  const valid = await passwordFormRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  passwordSubmitting.value = true
  try {
    await authApi.changePassword({
      oldPassword: passwordForm.value.oldPassword,
      newPassword: passwordForm.value.newPassword
    })
    ElMessage.success('密码已修改，请重新登录')
    passwordVisible.value = false
    await userStore.logout()
    router.replace('/login')
  } finally {
    passwordSubmitting.value = false
  }
}

async function onCommand(command: string): Promise<void> {
  if (command === 'password') {
    passwordVisible.value = true
    return
  }
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
    } catch {
      return
    }
    await userStore.logout()
    router.replace('/login')
  }
}

onMounted(loadUnread)
</script>

<style scoped>
.layout {
  min-height: 100vh;
}

.aside {
  background: #fff;
  border-right: 1px solid #ebeef5;
  transition: width 0.2s;
  display: flex;
  flex-direction: column;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  border-bottom: 1px solid #f0f2f5;
  overflow: hidden;
}

.logo-mark {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: linear-gradient(135deg, #2f6fed, #5b9bff);
  color: #fff;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.logo-text {
  font-weight: 600;
  white-space: nowrap;
  color: #1f2d3d;
}

.menu-scroll {
  flex: 1;
  padding-top: 8px;
}

.header {
  height: 60px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.collapse-btn {
  cursor: pointer;
  font-size: 18px;
  color: #606266;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2d3d;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 18px;
}

.user-entry {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}

.user-name {
  font-size: 14px;
  color: #303133;
}

.main {
  background: #f5f7fa;
  padding: 20px;
  overflow-y: auto;
}

.notice-item {
  padding: 12px 4px;
  border-bottom: 1px solid #f0f2f5;
  cursor: pointer;
}

.notice-item:hover {
  background: #f7f9fc;
}

.notice-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #303133;
}

.notice-meta {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
  padding-left: 12px;
}

.notice-content {
  white-space: pre-wrap;
  line-height: 1.8;
  color: #303133;
}
</style>
