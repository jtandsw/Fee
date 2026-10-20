<template>
  <div class="page">
    <div class="card">
      <h3 class="card-title">数据字典</h3>
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="该模块尚未实现"
        description="README 3.1 的功能总览列出了「数据字典」，但 7.2 建表脚本中没有对应表，10.4 接口清单中也没有字典维护接口，因此本页仅展示前端枚举字典，未提供增删改能力。"
      />
    </div>

    <div class="card">
      <h3 class="card-title">当前系统内置字典（前端常量）</h3>
      <el-collapse v-model="active">
        <el-collapse-item title="订单状态 ORDER_STATUS" name="order">
          <el-table :data="toRows(ORDER_STATUS)" size="small">
            <el-table-column prop="key" label="编码" width="180" />
            <el-table-column prop="label" label="名称" />
          </el-table>
        </el-collapse-item>
        <el-collapse-item title="项目状态 ITEM_STATUS" name="item">
          <el-table :data="toRows(ITEM_STATUS)" size="small">
            <el-table-column prop="key" label="编码" width="180" />
            <el-table-column prop="label" label="名称" />
          </el-table>
        </el-collapse-item>
        <el-collapse-item title="缴费类别 CATEGORY" name="category">
          <el-table :data="toRows(CATEGORY_LABEL)" size="small">
            <el-table-column prop="key" label="编码" width="180" />
            <el-table-column prop="label" label="名称" />
          </el-table>
        </el-collapse-item>
        <el-collapse-item title="对账差异类型 DIFF_TYPE" name="diff">
          <el-table :data="toRows(DIFF_TYPE)" size="small">
            <el-table-column prop="key" label="编码" width="180" />
            <el-table-column prop="label" label="名称" />
          </el-table>
        </el-collapse-item>
      </el-collapse>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { CATEGORY_LABEL, DIFF_TYPE, ITEM_STATUS, ORDER_STATUS } from '@/utils/status'

const active = ref(['order'])

type DictValue = string | { label: string }

function toRows(dict: Record<string, DictValue>): Array<{ key: string; label: string }> {
  return Object.entries(dict).map(([key, value]) => ({
    key,
    label: typeof value === 'string' ? value : value.label
  }))
}
</script>
