import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import './styles/main.css'

/**
 * 应用入口。
 * Element Plus 使用中文语言包；全局样式负责统一品牌色、卡片和响应式布局。
 */
createApp(App)
  .use(router)
  .use(ElementPlus, { locale: zhCn })
  .mount('#app')
