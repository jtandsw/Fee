<template>
  <!-- Element Plus 按需引入模式下，用 ElConfigProvider 统一中文语言包（分页器/日期选择器/空数据等） -->
  <el-config-provider :locale="zhCn">
    <router-view />
  </el-config-provider>
</template>

<script setup lang="ts">
import zhCn from 'element-plus/es/locale/lang/zh-cn'
</script>
