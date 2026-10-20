<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.username" placeholder="操作人账号" clearable style="width: 180px" @keyup.enter="search" />
        <el-input v-model="query.module" placeholder="模块，如 缴费项目" clearable style="width: 180px" @keyup.enter="search" />
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 250px"
          @change="search"
        />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>
    </div>

    <div class="card">
      <el-table v-loading="loading" :data="list" empty-text="暂无日志" stripe size="small">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="操作人" width="110" />
        <el-table-column prop="module" label="模块" width="110" />
        <el-table-column prop="action" label="动作" width="110" />
        <el-table-column prop="method" label="方法" width="80" />
        <el-table-column prop="uri" label="URI" min-width="220" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP" width="130" />
        <el-table-column label="结果码" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.resultCode === 0 ? 'success' : 'danger'" effect="light">
              {{ row.resultCode ?? '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="costMs" label="耗时(ms)" width="100" align="right" />
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="load"
          @size-change="search"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import * as userApi from '@/api/user'
import { formatDateTime } from '@/utils/format'
import type { OperationLog } from '@/types'

const loading = ref(false)
const list = ref<OperationLog[]>([])
const total = ref(0)
const dateRange = ref<[string, string] | null>(null)

const query = reactive({
  username: '',
  module: '',
  pageNum: 1,
  pageSize: 20
})

async function load(): Promise<void> {
  loading.value = true
  try {
    const [startDate, endDate] = dateRange.value ?? [undefined, undefined]
    const page = await userApi.pageLogs({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      username: query.username || undefined,
      module: query.module || undefined,
      startDate: startDate || undefined,
      endDate: endDate || undefined
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
  query.username = ''
  query.module = ''
  dateRange.value = null
  query.pageNum = 1
  load()
}

onMounted(load)
</script>
