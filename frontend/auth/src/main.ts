import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import AuthPage from './AuthPage.vue'
import './style.css'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/account' },
    { path: '/login', component: AuthPage, meta: { page: 'login', title: '登录' } },
    { path: '/register', component: AuthPage, meta: { page: 'register', title: '注册' } },
    { path: '/account', component: AuthPage, meta: { page: 'account', title: '我的账号' } },
    { path: '/password', component: AuthPage, meta: { page: 'password', title: '修改密码' } },
    { path: '/logout', component: AuthPage, meta: { page: 'logout', title: '退出登录' } },
    { path: '/:pathMatch(.*)*', component: AuthPage, meta: { page: 'not-found', title: '页面不存在' } },
  ],
})

router.afterEach((route) => { document.title = `${route.meta.title ?? '账号'} · Para Auth` })
createApp(App).use(router).mount('#app')
