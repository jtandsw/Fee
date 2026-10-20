import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './styles/index.scss'

// ---------------------------------------------------------------------------
// Element Plus 按需引入的补充样式。
// 模板里的 <el-xxx> 由 unplugin-vue-components 自动注入样式，但「函数式」组件
// 没有模板标签（ElMessageBox / ElMessage / ElNotification），以及指令 v-loading，
// 插件扫描不到，必须在这里显式引入，否则弹窗会失去定位与布局（跑到左上角、
// 按钮竖排），消息提示和加载遮罩也会不可见。
// ---------------------------------------------------------------------------
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/notification/style/css'
import 'element-plus/es/components/loading/style/css'

createApp(App).use(createPinia()).use(router).mount('#app')
