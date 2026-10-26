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
        <el-table-column label="适用范围" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="isAllScope(row as PaymentItem)" class="muted">全校</span>
            <span v-else>{{ row.scope?.text }}</span>
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
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT'" link type="primary" @click="openEdit(row as PaymentItem)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="success" @click="publish(row as PaymentItem)">发布</el-button>
            <el-button v-if="row.status === 'PUBLISHED'" link type="warning" @click="close(row as PaymentItem)">下架</el-button>
            <el-button v-if="row.status === 'CLOSED'" link type="success" @click="reopen(row as PaymentItem)">重新上架</el-button>
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

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑缴费项目' : '新建缴费项目'" width="680px" destroy-on-close>
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

        <!-- 适用范围：四个维度可任意组合，非空维度取交集 -->
        <el-form-item label="适用范围">
          <el-radio-group v-model="scopeMode" @change="onModeChange">
            <el-radio-button value="ALL">全校学生</el-radio-button>
            <el-radio-button value="LIMIT">按条件限定</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <template v-if="scopeMode === 'LIMIT'">
          <el-form-item label="年级">
            <el-select
              v-model="form.scope.grades"
              multiple
              filterable
              :loading="optionsLoading"
              placeholder="不限年级"
              :no-data-text="scopeEmptyText('GRADE')"
              style="width: 100%"
            >
              <el-option v-for="opt in gradeOptions" :key="opt.value" :label="opt.label" :value="opt.value">
                <span>{{ opt.label }}</span>
                <el-tag v-if="opt.orphan" size="small" type="danger" effect="plain" class="opt-orphan">档案中已不存在</el-tag>
              </el-option>
            </el-select>
          </el-form-item>

          <el-form-item label="学院">
            <el-select
              v-model="form.scope.colleges"
              multiple
              filterable
              :loading="optionsLoading"
              placeholder="不限学院"
              :no-data-text="scopeEmptyText('COLLEGE')"
              style="width: 100%"
              @change="onCollegesChange"
            >
              <el-option v-for="opt in collegeOptions" :key="opt.value" :label="opt.label" :value="opt.value">
                <span>{{ opt.label }}</span>
                <el-tag v-if="opt.orphan" size="small" type="danger" effect="plain" class="opt-orphan">档案中已不存在</el-tag>
              </el-option>
            </el-select>
          </el-form-item>

          <el-form-item label="专业">
            <el-select
              v-model="form.scope.majors"
              multiple
              filterable
              :loading="optionsLoading"
              :placeholder="form.scope.colleges.length ? '所选学院下的专业' : '不限专业'"
              :no-data-text="scopeEmptyText('MAJOR')"
              style="width: 100%"
            >
              <el-option v-for="opt in majorOptions" :key="opt.value" :label="opt.label" :value="opt.value">
                <span>{{ opt.label }}</span>
                <span v-if="opt.college" class="opt-sub">{{ opt.college }}</span>
                <el-tag v-if="opt.orphan" size="small" type="danger" effect="plain" class="opt-orphan">档案中已不存在</el-tag>
              </el-option>
            </el-select>
          </el-form-item>

          <el-form-item label="指定学生">
            <el-select
              v-model="form.scope.studentIds"
              multiple
              filterable
              remote
              reserve-keyword
              :remote-method="remoteSearchStudents"
              :loading="studentsLoading"
              placeholder="不限学生；可搜索学号 / 姓名精确指定"
              no-data-text="未找到匹配的学生"
              style="width: 100%"
            >
              <el-option v-for="opt in studentSelectOptions" :key="opt.value" :label="opt.label" :value="opt.value">
                <span>{{ opt.label }}</span>
                <el-tag v-if="opt.orphan" size="small" type="danger" effect="plain" class="opt-orphan">档案中已不存在</el-tag>
              </el-option>
            </el-select>
          </el-form-item>

          <el-form-item label="条件说明">
            <div class="scope-summary">
              <div class="scope-rule">{{ scopeSummary }}</div>
              <div class="scope-hint">各条件之间为「并且」关系，例如「2023 级 + 计算机学院 + 软件工程」只覆盖该学院该专业 2023 级学生。</div>
              <div v-if="orphanCount > 0" class="scope-warn">
                有 {{ orphanCount }} 个已选值在当前学籍档案中不存在，学生端将无法匹配，建议重新选择。
              </div>
            </div>
          </el-form-item>
        </template>

        <el-form-item label="覆盖人数">
          <div v-loading="previewing" class="scope-preview">
            <template v-if="preview">
              <span v-if="preview.studentCount > 0" class="scope-count">预计覆盖 {{ preview.studentCount }} 名学生</span>
              <span v-else class="scope-count danger">当前条件下没有任何学生，发布后无人能看到</span>
            </template>
            <span v-else class="muted">—</span>
          </div>
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
        <el-descriptions-item label="适用范围">{{ activeItem.scope?.text || '全校' }}</el-descriptions-item>
        <el-descriptions-item v-if="activeItem.scope?.studentIds?.length" label="指定学生">
          <span v-if="detailStudents.length">{{ detailStudents.join('、') }}</span>
          <span v-else class="muted">{{ detailStudentsLoading ? '加载中…' : '学生档案中已不存在' }}</span>
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
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import MoneyText from '@/components/MoneyText.vue'
import StatusTag from '@/components/StatusTag.vue'
import * as itemApi from '@/api/item'
import { formatAmount, formatDate, formatDateTime } from '@/utils/format'
import { CATEGORY_LABEL, ITEM_STATUS } from '@/utils/status'
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

/** 适用范围模式：全校 / 按条件限定 */
const scopeMode = ref<'ALL' | 'LIMIT'>('ALL')

const form = reactive({
  itemName: '',
  category: 'TUITION',
  term: '',
  amount: '',
  allowPartial: false,
  scope: {
    grades: [] as string[],
    colleges: [] as string[],
    majors: [] as string[],
    studentIds: [] as string[]
  },
  timeRange: [] as string[],
  description: ''
})

/* ---------------- 适用范围选项：全部来自学籍档案，禁止手填 ---------------- */

const optionsLoading = ref(false)
const studentsLoading = ref(false)
const scopeOptions = reactive<itemApi.ScopeOptions>({ grades: [], colleges: [], majors: [] })
/** 学生 id -> 学籍精简信息，用于把「指定学生」的 id 还原成学号姓名 */
const studentStore = reactive(new Map<string, itemApi.StudentOption>())
/** 当前这一次搜索命中的学生 id，只作为下拉候选，不污染已选值 */
const studentResultIds = ref<string[]>([])

interface ScopeOption {
  value: string
  label: string
  /** 专业选项副标题：所属学院 */
  college?: string
  /** 该值在学籍档案中已不存在（历史数据） */
  orphan?: boolean
}

/** 档案现存值 + 已选的历史值（历史值保留展示并标红，不会静默丢失） */
function withOrphans(base: ScopeOption[], selected: string[]): ScopeOption[] {
  const exist = new Set(base.map((o) => o.value))
  const orphans = selected.filter((v) => !exist.has(v))
  return [...base, ...orphans.map((v) => ({ value: v, label: v, orphan: true }))]
}

const gradeOptions = computed<ScopeOption[]>(() =>
  withOrphans(
    scopeOptions.grades.map((g) => ({ value: g, label: `${g} 级` })),
    form.scope.grades
  )
)

const collegeOptions = computed<ScopeOption[]>(() =>
  withOrphans(
    scopeOptions.colleges.map((c) => ({ value: c, label: c })),
    form.scope.colleges
  )
)

/** 专业范围跟着已选学院收窄；没选学院时列出全部专业并标注所属学院 */
const majorOptions = computed<ScopeOption[]>(() => {
  const colleges = new Set(form.scope.colleges)
  const list = colleges.size > 0 ? scopeOptions.majors.filter((m) => colleges.has(m.college)) : scopeOptions.majors
  const seen = new Set<string>()
  const base = list
    .filter((m) => !seen.has(m.major) && seen.add(m.major))
    .map((m) => ({ value: m.major, label: m.major, college: m.college }))
  return withOrphans(base, form.scope.majors)
})

const studentSelectOptions = computed<ScopeOption[]>(() => {
  const ids = [...new Set([...form.scope.studentIds, ...studentResultIds.value])]
  return ids.map((id) => {
    const stu = studentStore.get(id)
    if (!stu) {
      return { value: id, label: `学生 #${id}`, orphan: true }
    }
    return { value: id, label: `${stu.studentNo} ${stu.realName}（${stu.major}）` }
  })
})

const orphanCount = computed(
  () => [...gradeOptions.value, ...collegeOptions.value, ...majorOptions.value, ...studentSelectOptions.value].filter((o) => o.orphan).length
)

const scopeSummary = computed(() => {
  const parts: string[] = []
  if (form.scope.grades.length) {
    parts.push(`年级：${form.scope.grades.map((g) => `${g} 级`).join('、')}`)
  }
  if (form.scope.colleges.length) {
    parts.push(`学院：${form.scope.colleges.join('、')}`)
  }
  if (form.scope.majors.length) {
    parts.push(`专业：${form.scope.majors.join('、')}`)
  }
  if (form.scope.studentIds.length) {
    parts.push(`指定 ${form.scope.studentIds.length} 名学生`)
  }
  return parts.length ? parts.join(' ／ ') : '尚未选择任何条件（等同全校）'
})

function scopeEmptyText(kind: 'GRADE' | 'COLLEGE' | 'MAJOR'): string {
  const map = {
    GRADE: '学籍档案中暂无年级数据，请先导入学生名单',
    COLLEGE: '学籍档案中暂无学院数据，请先导入学生名单',
    MAJOR: '学籍档案中暂无专业数据，请先导入学生名单'
  }
  return map[kind]
}

async function loadScopeOptions(): Promise<void> {
  optionsLoading.value = true
  try {
    const data = await itemApi.getScopeOptions()
    scopeOptions.grades = data.grades || []
    scopeOptions.colleges = data.colleges || []
    scopeOptions.majors = data.majors || []
  } catch {
    ElMessage.error('加载学籍档案选项失败，请刷新后重试')
  } finally {
    optionsLoading.value = false
  }
}

async function remoteSearchStudents(keyword: string): Promise<void> {
  studentsLoading.value = true
  try {
    const list = await itemApi.searchScopeStudents({ keyword: keyword?.trim() || undefined })
    list.forEach((s) => studentStore.set(String(s.id), s))
    studentResultIds.value = list.map((s) => String(s.id))
  } finally {
    studentsLoading.value = false
  }
}

/** 编辑时把已选学生 id 还原成学号姓名 */
async function preloadSelectedStudents(): Promise<void> {
  const missing = form.scope.studentIds.filter((id) => !studentStore.has(id))
  if (missing.length === 0) {
    return
  }
  try {
    const list = await itemApi.searchScopeStudents({ ids: missing.join(',') })
    list.forEach((s) => studentStore.set(String(s.id), s))
  } catch {
    // 回显失败不影响展示，未命中的 id 会以「档案中已不存在」呈现
  }
}

/* ---------------- 覆盖人数预览 ---------------- */

const preview = ref<itemApi.ScopePreview | null>(null)
const previewing = ref(false)
let previewTimer: ReturnType<typeof setTimeout> | undefined

function currentScope(): itemApi.ScopeRule {
  if (scopeMode.value === 'ALL') {
    return {}
  }
  return {
    grades: form.scope.grades,
    colleges: form.scope.colleges,
    majors: form.scope.majors,
    studentIds: form.scope.studentIds
  }
}

async function runPreview(): Promise<void> {
  previewing.value = true
  try {
    preview.value = await itemApi.previewScope(currentScope())
  } catch {
    preview.value = null
  } finally {
    previewing.value = false
  }
}

function schedulePreview(): void {
  if (!dialogVisible.value) {
    return
  }
  clearTimeout(previewTimer)
  previewTimer = setTimeout(runPreview, 300)
}

watch(scopeMode, schedulePreview)
watch(() => form.scope, schedulePreview, { deep: true })

/** 改了学院后，把不再属于所选学院的专业剔掉，避免出现「学院 A + 专业 B」的矛盾组合 */
function onCollegesChange(): void {
  if (form.scope.colleges.length === 0) {
    return
  }
  const allowed = new Set(
    scopeOptions.majors.filter((m) => form.scope.colleges.includes(m.college)).map((m) => m.major)
  )
  form.scope.majors = form.scope.majors.filter((m) => allowed.has(m))
}

function onModeChange(): void {
  if (scopeMode.value === 'LIMIT' && form.scope.studentIds.length === 0) {
    remoteSearchStudents('')
  }
}

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

function hasScopeValue(): boolean {
  return (
    form.scope.grades.length > 0 ||
    form.scope.colleges.length > 0 ||
    form.scope.majors.length > 0 ||
    form.scope.studentIds.length > 0
  )
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
  form.scope.grades = []
  form.scope.colleges = []
  form.scope.majors = []
  form.scope.studentIds = []
  form.timeRange = []
  form.description = ''
  scopeMode.value = 'ALL'
  preview.value = null
  formRef.value?.clearValidate()
}

function openCreate(): void {
  editingId.value = null
  resetForm()
  studentResultIds.value = []
  dialogVisible.value = true
  loadScopeOptions()
  runPreview()
}

function openEdit(item: PaymentItem): void {
  editingId.value = item.id
  form.itemName = item.itemName
  form.category = item.category
  form.term = item.term
  form.amount = item.amount
  form.allowPartial = item.allowPartial === 1
  form.timeRange = [item.startTime, item.endTime]
  form.description = item.description || ''

  // 后端已把历史单一维度数据统一翻译成 scope 四维度结构，这里直接回填
  const scope = item.scope
  form.scope.grades = scope?.grades ? [...scope.grades] : []
  form.scope.colleges = scope?.colleges ? [...scope.colleges] : []
  form.scope.majors = scope?.majors ? [...scope.majors] : []
  form.scope.studentIds = scope?.studentIds ? [...scope.studentIds] : []
  scopeMode.value = hasScopeValue() ? 'LIMIT' : 'ALL'

  studentResultIds.value = []
  dialogVisible.value = true
  loadScopeOptions()
  runPreview()
  if (form.scope.studentIds.length > 0) {
    remoteSearchStudents('')
    preloadSelectedStudents()
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
  if (scopeMode.value === 'LIMIT' && !hasScopeValue()) {
    ElMessage.warning('请至少选择一个限定条件，或切换为「全校学生」')
    return
  }
  if (scopeMode.value === 'LIMIT' && preview.value && preview.value.studentCount === 0) {
    try {
      await ElMessageBox.confirm('当前适用范围没有任何学生，发布后学生端看不到该项目。确定继续保存吗？', '覆盖人数为 0', {
        type: 'warning'
      })
    } catch {
      return
    }
  }
  submitting.value = true
  try {
    const payload: itemApi.ItemPayload = {
      itemName: form.itemName,
      category: form.category,
      term: form.term,
      amount: form.amount,
      allowPartial: form.allowPartial ? 1 : 0,
      scope: currentScope(),
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

/** 已下架项目恢复上架：避免误下架后不可挽回 */
async function reopen(item: PaymentItem): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定重新上架「${item.itemName}」吗？学生将再次看到该项目并可以继续缴费。`,
      '重新上架',
      { type: 'info' }
    )
  } catch {
    return
  }
  await itemApi.publishItem(item.id)
  ElMessage.success('项目已重新上架')
  load()
}

/**
 * 下架前先取影响预览：下架只停止收费，不会退已缴费用，
 * 若还有待支付/支付中的订单，后端会拒绝下架。
 */
async function close(item: PaymentItem): Promise<void> {
  const preview = await itemApi.getClosePreview(item.id)
  if (!preview.canClose) {
    ElMessage.warning(preview.blockReason || '当前不可下架')
    return
  }
  const message = `
    <div>已缴 <b>${preview.paidStudentCount}</b> 人，共 <b>${preview.paidOrderCount}</b> 笔，合计 <b>¥${formatAmount(preview.paidAmount)}</b></div>
    <div style="margin-top:8px;color:#e6a23c">下架后学生将无法继续缴费，已收费用不会自动退还；如需退款请在订单管理中逐笔处理。</div>
    <div style="margin-top:8px;color:#909399;font-size:12px">下架后在截止时间前仍可重新上架。</div>
  `
  try {
    await ElMessageBox.confirm(message, `下架「${item.itemName}」`, {
      type: 'warning',
      dangerouslyUseHTMLString: true,
      confirmButtonText: '确认下架',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await itemApi.closeItem(item.id)
  ElMessage.success('项目已下架')
  load()
}

const detailStudents = ref<string[]>([])
const detailStudentsLoading = ref(false)

function isAllScope(item: PaymentItem): boolean {
  const text = item.scope?.text
  return !text || text === '全校'
}

async function showDetail(item: PaymentItem): Promise<void> {
  activeItem.value = item
  detailVisible.value = true
  detailStudents.value = []
  const ids = item.scope?.studentIds || []
  if (!ids.length) {
    return
  }
  detailStudentsLoading.value = true
  try {
    const list = await itemApi.searchScopeStudents({ ids: ids.join(',') })
    const map = new Map(list.map((s) => [String(s.id), s]))
    detailStudents.value = ids.map((id) => {
      const stu = map.get(String(id))
      return stu ? `${stu.studentNo} ${stu.realName}` : `学生 #${id}（已不存在）`
    })
  } finally {
    detailStudentsLoading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.scope-summary {
  width: 100%;
  line-height: 1.6;
}

.scope-rule {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.scope-hint {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.scope-warn {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-color-danger);
}

.scope-preview {
  min-height: 22px;
  font-size: 13px;
}

.scope-count {
  color: var(--el-color-primary);
}

.scope-count.danger {
  color: var(--el-color-danger);
}

.opt-orphan {
  margin-left: 8px;
}

.opt-sub {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.switch-tip {
  margin-left: 10px;
}
</style>
