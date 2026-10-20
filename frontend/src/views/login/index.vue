<template>
  <div class="login-page">
    <div class="brand">
      <div class="brand-mark">缴</div>
      <h1>大学生缴费管理系统</h1>
      <p class="brand-sub">项目发布 · 在线缴费 · 订单对账 · 统计报表</p>
      <ul class="brand-list">
        <li>缴费项目在线发布，学生宿舍即可完成缴费</li>
        <li>订单号唯一 + 支付回调幂等，全程可追溯</li>
        <li>渠道账单自动匹配，差异逐笔核销</li>
      </ul>
    </div>

    <el-card class="login-card" shadow="always">
      <h2>账号登录</h2>
      <p class="muted">使用学号 / 工号登录，学生与教职工统一入口</p>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="submit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="学号 / 工号" :prefix-icon="User" clearable />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="密码"
            :prefix-icon="Lock"
          />
        </el-form-item>
        <el-button type="primary" class="submit" :loading="loading" @click="submit">登 录</el-button>
      </el-form>

      <div class="demo">
        <span class="muted">演示账号（密码 123456）</span>
        <div class="demo-tags">
          <el-tag
            v-for="account in demoAccounts"
            :key="account.username"
            class="demo-tag"
            @click="fill(account.username)"
          >
            {{ account.label }}
          </el-tag>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { homePath, useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = ref({ username: '20230101', password: '123456' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入学号或工号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const demoAccounts = [
  { label: '学生 20230101', username: '20230101' },
  { label: '财务 caiwu01', username: 'caiwu01' },
  { label: '管理员 admin', username: 'admin' }
]

function fill(username: string): void {
  form.value.username = username
  form.value.password = '123456'
}

async function submit(): Promise<void> {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  loading.value = true
  try {
    const user = await userStore.login(form.value)
    ElMessage.success(`欢迎回来，${user.realName}`)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    await router.replace(redirect || homePath(user.role))
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 60px;
  padding: 0 10vw;
  background: radial-gradient(circle at 15% 20%, #e8f0ff 0%, #f5f7fa 45%, #eef4ff 100%);
}

.brand {
  max-width: 520px;
}

.brand-mark {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  background: linear-gradient(135deg, #2f6fed, #5b9bff);
  color: #fff;
  font-size: 26px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand h1 {
  font-size: 34px;
  margin: 20px 0 8px;
  color: #1f2d3d;
}

.brand-sub {
  color: #606266;
  font-size: 15px;
  margin: 0 0 28px;
}

.brand-list {
  margin: 0;
  padding-left: 20px;
  color: #606266;
  line-height: 2;
  font-size: 14px;
}

.login-card {
  width: 400px;
  border-radius: 14px;
  padding: 12px 8px;
}

.login-card h2 {
  margin: 0 0 6px;
  font-size: 22px;
}

.login-card .muted {
  margin: 0 0 20px;
  display: block;
}

.submit {
  width: 100%;
  margin-top: 4px;
}

.demo {
  margin-top: 22px;
  border-top: 1px dashed #ebeef5;
  padding-top: 16px;
}

.demo-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.demo-tag {
  cursor: pointer;
}

@media (max-width: 900px) {
  .login-page {
    justify-content: center;
    padding: 24px;
  }

  .brand {
    display: none;
  }
}
</style>
