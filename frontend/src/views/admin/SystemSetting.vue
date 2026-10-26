<template>
  <div class="page" v-loading="loading">
    <div class="card intro-card">
      <div class="intro-main">
        <div class="intro-title">系统设置</div>
        <div class="intro-desc">
          本页只呈现运行环境、账号规模与参数配置等系统层面信息。缴费项目、订单、退款、对账与报表属于财务业务，
          由财务角色独立管理，管理员不做干预。
        </div>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="card">
      <h3 class="card-title">账号规模</h3>
      <div class="stat-grid">
        <div v-for="item in accountStats" :key="item.label" class="stat-item">
          <div class="stat-label">{{ item.label }}</div>
          <div class="stat-value" :class="item.className">{{ item.value }}</div>
        </div>
      </div>
    </div>

    <div class="card">
      <h3 class="card-title">运行环境</h3>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="应用名称">{{ info?.app.name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="版本号">{{ info?.app.version || '-' }}</el-descriptions-item>
        <el-descriptions-item label="运行环境">{{ info?.app.profile || '-' }}</el-descriptions-item>
        <el-descriptions-item label="接口前缀">{{ info?.app.contextPath || '-' }}</el-descriptions-item>
        <el-descriptions-item label="服务端口">{{ info?.app.port || '-' }}</el-descriptions-item>
        <el-descriptions-item label="Java 版本">{{ info?.app.javaVersion || '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作系统">{{ info?.app.os || '-' }}</el-descriptions-item>
        <el-descriptions-item label="已运行时长">{{ uptimeText }}</el-descriptions-item>
        <el-descriptions-item label="启动时间" :span="2">{{ info?.app.startedAt || '-' }}</el-descriptions-item>
      </el-descriptions>
    </div>

    <div class="card">
      <h3 class="card-title">数据库</h3>
      <el-descriptions :column="3" border size="small" :label-style="{ width: '96px' }">
        <el-descriptions-item label="状态">
          <el-tag size="small" :type="info?.db.connected ? 'success' : 'danger'" effect="light">
            {{ info?.db.connected ? '连接正常' : '连接异常' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="数据库">
          {{ info?.db.product ? `${info.db.product} ${info.db.version || ''}` : (info?.db.error || '-') }}
        </el-descriptions-item>
        <el-descriptions-item label="驱动">{{ info?.db.driver || '-' }}</el-descriptions-item>
        <el-descriptions-item label="连接串" :span="3">
          <el-tooltip :content="info?.db.url || '-'" placement="top" :show-after="300">
            <span class="mono">{{ jdbcSummary }}</span>
          </el-tooltip>
        </el-descriptions-item>
      </el-descriptions>
    </div>

    <div class="card">
      <h3 class="card-title">支付渠道</h3>
      <div class="channel-line">
        <el-tag
          v-for="c in info?.payment.channels ?? []"
          :key="c.value"
          size="small"
          type="success"
          effect="light"
        >
          {{ c.label }}（{{ c.value }}）
        </el-tag>
        <span v-if="!info?.payment.channels?.length" class="muted">未注册任何渠道</span>
        <span class="channel-hint">演示渠道不产生真实扣款；接入真实渠道需新增对应适配器实现。</span>
      </div>
    </div>

    <div class="card">
      <h3 class="card-title">参数配置（只读）</h3>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="默认支付渠道">{{ info?.payment.defaultChannel || '-' }}</el-descriptions-item>
        <el-descriptions-item label="订单支付超时">{{ info?.payment.orderExpireMinutes ?? '-' }} 分钟</el-descriptions-item>
        <el-descriptions-item label="访问令牌有效期">{{ info?.setting.accessTokenMinutes ?? '-' }} 分钟</el-descriptions-item>
        <el-descriptions-item label="刷新令牌有效期">{{ info?.setting.refreshTokenDays ?? '-' }} 天</el-descriptions-item>
        <el-descriptions-item label="前端地址" :span="2">{{ info?.setting.frontendBaseUrl || '-' }}</el-descriptions-item>
        <el-descriptions-item label="账单存放目录" :span="2">{{ info?.setting.uploadDir || '-' }}</el-descriptions-item>
        <el-descriptions-item label="跨域白名单" :span="2">
          {{ info?.setting.corsOrigins?.length ? info?.setting.corsOrigins.join(' , ') : '-' }}
        </el-descriptions-item>
      </el-descriptions>
      <div class="tip">以上参数由后端配置文件维护，页面不提供在线修改，避免误操作影响在办业务。</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import * as systemApi from '@/api/system'
import type { SystemInfo } from '@/api/system'

const loading = ref(false)
const info = ref<SystemInfo | null>(null)

const accountStats = computed(() => {
  const account = info.value?.account
  return [
    { label: '账号总数', value: account?.total ?? 0, className: '' },
    { label: '学生账号', value: account?.student ?? 0, className: 'c-blue' },
    { label: '财务账号', value: account?.finance ?? 0, className: 'c-teal' },
    { label: '管理员账号', value: account?.admin ?? 0, className: 'c-purple' },
    { label: '已停用', value: account?.disabled ?? 0, className: 'c-grey' }
  ]
})

const uptimeText = computed(() => {
  const seconds = info.value?.app.uptimeSeconds ?? 0
  if (seconds <= 0) {
    return '-'
  }
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const parts: string[] = []
  if (days > 0) {
    parts.push(`${days} 天`)
  }
  if (hours > 0) {
    parts.push(`${hours} 小时`)
  }
  parts.push(`${minutes} 分钟`)
  return parts.join(' ')
})

/** 连接串只展示到库名为止，参数部分过长会撑破表格，悬停可看完整脱敏值 */
const jdbcSummary = computed(() => {
  const url = info.value?.db.url
  if (!url) {
    return '-'
  }
  const queryIndex = url.indexOf('?')
  return queryIndex > 0 ? `${url.slice(0, queryIndex)}?…` : url
})

async function load(): Promise<void> {
  loading.value = true
  try {
    info.value = await systemApi.getSystemInfo()
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.channel-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.channel-hint {
  font-size: 12px;
  color: #909399;
}

.intro-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.intro-title {
  font-size: 18px;
  font-weight: 700;
  color: #1f2d3d;
  margin-bottom: 6px;
}

.intro-desc {
  font-size: 13px;
  line-height: 1.7;
  color: #606266;
  max-width: 720px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.stat-item {
  background: #f7f9fc;
  border: 1px solid #eef1f6;
  border-radius: 10px;
  padding: 14px 16px;
}

.stat-label {
  font-size: 13px;
  color: #909399;
}

.stat-value {
  margin-top: 8px;
  font-size: 22px;
  font-weight: 700;
  color: #1f2d3d;
}

.c-blue {
  color: #2f6fed;
}

.c-teal {
  color: #12a08a;
}

.c-purple {
  color: #7a5af5;
}

.c-grey {
  color: #c45656;
}

.mono {
  display: block;
  width: 100%;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  color: #606266;
  word-break: break-all;
  overflow-wrap: anywhere;
}

/* el-descriptions 内部是 table 布局，超长连接串会把列撑出卡片边界 */
:deep(.el-descriptions__content) {
  word-break: break-all;
  overflow-wrap: anywhere;
}

.tip {
  margin-top: 12px;
  font-size: 12px;
  color: #909399;
}

@media (max-width: 1200px) {
  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
