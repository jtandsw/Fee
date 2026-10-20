<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <div>
          <h3 class="card-title">对账管理</h3>
          <p class="muted">上传渠道 T-1 账单 CSV，按渠道流水号与本地已支付订单逐笔匹配</p>
        </div>
        <div class="toolbar-right">
          <el-button type="primary" :icon="Upload" @click="openUpload">上传账单</el-button>
          <el-button :icon="Refresh" @click="load">刷新</el-button>
        </div>
      </div>
    </div>

    <div class="card">
      <el-table v-loading="loading" :data="list" empty-text="暂无对账批次" stripe>
        <el-table-column prop="batchNo" label="批次号" width="160" />
        <el-table-column label="渠道" width="110">
          <template #default="{ row }">{{ CHANNEL_LABEL[row.channel] || row.channel }}</template>
        </el-table-column>
        <el-table-column prop="billDate" label="账单日期" width="120" />
        <el-table-column prop="totalCount" label="账单笔数" width="100" align="right" />
        <el-table-column label="账单总额" width="130" align="right">
          <template #default="{ row }"><MoneyText :amount="row.totalAmount" /></template>
        </el-table-column>
        <el-table-column prop="matchedCount" label="匹配" width="80" align="right" />
        <el-table-column prop="diffCount" label="差异" width="80" align="right" />
        <el-table-column label="差异金额" width="120" align="right">
          <template #default="{ row }"><MoneyText :amount="row.diffAmount" /></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><StatusTag :status="row.status" type="batch" /></template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="row.status === 'FINISHED'" @click="match(row as ReconBatch)">执行匹配</el-button>
            <el-button link @click="openDetails(row as ReconBatch)">差异明细</el-button>
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

    <el-dialog v-model="uploadVisible" title="上传渠道账单" width="520px">
      <el-form label-width="90px">
        <el-form-item label="对账渠道" required>
          <el-select v-model="uploadForm.channel" style="width: 100%">
            <el-option v-for="(label, key) in CHANNEL_LABEL" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="账单日期" required>
          <el-date-picker
            v-model="uploadForm.billDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择账单日期（T-1）"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="账单文件" required>
          <el-upload
            drag
            :auto-upload="false"
            :limit="1"
            :on-change="onFileChange"
            :on-remove="onFileRemove"
            :file-list="fileList"
            accept=".csv"
            style="width: 100%"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽 CSV 到此处，或 <em>点击选择</em></div>
            <template #tip>
              <div class="muted upload-tip">
                UTF-8 编码，表头：订单号,渠道流水号,金额；金额支持千分位
              </div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="submitUpload">上传并创建批次</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" :title="`差异明细 · ${activeBatch?.batchNo || ''}`" size="900px">
      <div class="toolbar drawer-toolbar">
        <el-select v-model="detailQuery.diffType" placeholder="全部差异类型" clearable style="width: 180px" @change="loadDetails">
          <el-option v-for="(meta, key) in DIFF_TYPE" :key="key" :label="meta.label" :value="key" />
        </el-select>
        <el-button :icon="Refresh" @click="loadDetails">刷新</el-button>
      </div>

      <el-table v-loading="detailLoading" :data="details" empty-text="暂无明细" size="small" stripe>
        <el-table-column prop="orderNo" label="订单号" min-width="170" />
        <el-table-column prop="channelTradeNo" label="渠道流水号" min-width="160" />
        <el-table-column label="本地金额" width="110" align="right">
          <template #default="{ row }">{{ row.localAmount ? formatAmount(row.localAmount) : '-' }}</template>
        </el-table-column>
        <el-table-column label="渠道金额" width="110" align="right">
          <template #default="{ row }">{{ row.channelAmount ? formatAmount(row.channelAmount) : '-' }}</template>
        </el-table-column>
        <el-table-column label="差异类型" width="130">
          <template #default="{ row }"><StatusTag :status="row.diffType" type="diff" /></template>
        </el-table-column>
        <el-table-column label="处理状态" width="100">
          <template #default="{ row }">{{ HANDLE_STATUS[row.handleStatus] || row.handleStatus }}</template>
        </el-table-column>
        <el-table-column prop="handleRemark" label="处理说明" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button
              v-if="row.diffType !== 'MATCH' && row.handleStatus === 'UNHANDLED' && activeBatch?.status !== 'FINISHED'"
              link
              type="primary"
              @click="openHandle(row as ReconDetail)"
            >
              核销
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="detailQuery.pageNum"
          v-model:page-size="detailQuery.pageSize"
          :total="detailTotal"
          layout="total, prev, pager, next"
          background
          @current-change="loadDetails"
        />
      </div>
    </el-drawer>

    <el-dialog v-model="handleVisible" title="人工核销" width="460px">
      <el-form label-width="90px">
        <el-form-item label="订单号">{{ handleTarget?.orderNo || '-' }}</el-form-item>
        <el-form-item label="差异类型">
          <StatusTag v-if="handleTarget" :status="handleTarget.diffType" type="diff" />
        </el-form-item>
        <el-form-item label="处理结果" required>
          <el-radio-group v-model="handleForm.handleStatus">
            <el-radio label="HANDLED">已处理</el-radio>
            <el-radio label="IGNORED">忽略</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理说明">
          <el-input v-model="handleForm.handleRemark" type="textarea" :rows="3" maxlength="255" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" :loading="handling" @click="submitHandle">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type UploadFile, type UploadFiles } from 'element-plus'
import { Refresh, Upload, UploadFilled } from '@element-plus/icons-vue'
import MoneyText from '@/components/MoneyText.vue'
import StatusTag from '@/components/StatusTag.vue'
import * as reconApi from '@/api/recon'
import { formatAmount, formatDate } from '@/utils/format'
import { CHANNEL_LABEL, DIFF_TYPE, HANDLE_STATUS } from '@/utils/status'
import type { ReconBatch, ReconDetail } from '@/types'

const loading = ref(false)
const list = ref<ReconBatch[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

const uploadVisible = ref(false)
const uploading = ref(false)
const uploadForm = reactive({ channel: 'MOCK', billDate: formatDate(new Date().toISOString()) })
const billFile = ref<File | null>(null)
const fileList = ref<UploadFile[]>([])

const detailVisible = ref(false)
const detailLoading = ref(false)
const activeBatch = ref<ReconBatch | null>(null)
const details = ref<ReconDetail[]>([])
const detailTotal = ref(0)
const detailQuery = reactive({ diffType: '', pageNum: 1, pageSize: 10 })

const handleVisible = ref(false)
const handling = ref(false)
const handleTarget = ref<ReconDetail | null>(null)
const handleForm = reactive({ handleStatus: 'HANDLED', handleRemark: '' })

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await reconApi.pageBatches({ pageNum: pageNum.value, pageSize: pageSize.value })
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

function openUpload(): void {
  uploadForm.channel = 'MOCK'
  uploadForm.billDate = formatDate(new Date().toISOString())
  billFile.value = null
  fileList.value = []
  uploadVisible.value = true
}

function onFileChange(file: UploadFile, _files: UploadFiles): void {
  fileList.value = [file]
  billFile.value = file.raw ?? null
}

function onFileRemove(): void {
  fileList.value = []
  billFile.value = null
}

async function submitUpload(): Promise<void> {
  if (!billFile.value) {
    ElMessage.warning('请选择账单 CSV 文件')
    return
  }
  uploading.value = true
  try {
    await reconApi.createBatch(uploadForm.channel, uploadForm.billDate, billFile.value)
    ElMessage.success('批次已创建，请执行自动匹配')
    uploadVisible.value = false
    load()
  } finally {
    uploading.value = false
  }
}

async function match(batch: ReconBatch): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定对批次 ${batch.batchNo} 执行自动匹配吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  const result = await reconApi.matchBatch(batch.batchNo)
  ElMessage.success(`匹配完成：一致 ${result.matchedCount} 笔，差异 ${result.diffCount} 笔`)
  load()
}

async function openDetails(batch: ReconBatch): Promise<void> {
  activeBatch.value = batch
  detailQuery.diffType = ''
  detailQuery.pageNum = 1
  detailVisible.value = true
  await loadDetails()
}

async function loadDetails(): Promise<void> {
  if (!activeBatch.value) {
    return
  }
  detailLoading.value = true
  try {
    const page = await reconApi.pageDetails(activeBatch.value.batchNo, {
      pageNum: detailQuery.pageNum,
      pageSize: detailQuery.pageSize,
      diffType: detailQuery.diffType || undefined
    })
    details.value = page.list
    detailTotal.value = page.total
  } finally {
    detailLoading.value = false
  }
}

function openHandle(detail: ReconDetail): void {
  handleTarget.value = detail
  handleForm.handleStatus = 'HANDLED'
  handleForm.handleRemark = ''
  handleVisible.value = true
}

async function submitHandle(): Promise<void> {
  if (!handleTarget.value) {
    return
  }
  handling.value = true
  try {
    await reconApi.handleDetail(handleTarget.value.id, {
      handleStatus: handleForm.handleStatus,
      handleRemark: handleForm.handleRemark || undefined
    })
    ElMessage.success('核销结果已保存')
    handleVisible.value = false
    await loadDetails()
    await load()
  } finally {
    handling.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.drawer-toolbar {
  margin-bottom: 12px;
}

.upload-tip {
  margin-top: 6px;
}
</style>
