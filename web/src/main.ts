import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'

import App from './App.vue'
import AboutView from './views/AboutView.vue'
import AdminView from './views/AdminView.vue'
import FoodDetailView from './views/FoodDetailView.vue'
import HomeView from './views/HomeView.vue'
import LoginView from './views/LoginView.vue'
import NotFoundView from './views/NotFoundView.vue'
import ProfileView from './views/ProfileView.vue'
import RegisterView from './views/RegisterView.vue'
import UserPublicView from './views/UserPublicView.vue'
import { isAdminRole, useAuth } from './auth'
import { registerUnauthorizedHandler } from './api'
import { i18n, saveLocale } from './i18n'

// 样式按功能域拆分：基础 → 各页面 → 响应式（顺序即级联优先级）
import './base.css'
import './login.css'
import './admin.css'
import './home.css'
import './detail.css'
import './map.css'
import './modal.css'
import './region-drawer.css'
import './profile.css'
import './agent.css'
// 响应式规则必须最后加载，确保窄屏覆盖所有功能域样式。
import './responsive.css'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: HomeView,
    },
    {
      path: '/foods/:id',
      component: FoodDetailView,
    },
    {
      path: '/login',
      component: LoginView,
    },
    {
      path: '/register',
      component: RegisterView,
    },
    {
      path: '/about',
      component: AboutView,
    },
    {
      path: '/users/:id',
      component: UserPublicView,
    },
    {
      path: '/profile',
      component: ProfileView,
      meta: { requiresAuth: true },
    },
    {
      path: '/admin',
      component: AdminView,
      meta: { requiresAuth: true, requiresAdmin: true },
    },
    {
      path: '/:pathMatch(.*)*',
      component: NotFoundView,
    },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuth()
  await auth.restoreSession()

  if (to.meta.requiresAuth && !auth.currentUser.value) {
    return {
      path: '/login',
      query: {
        ...(to.meta.requiresAdmin ? { role: 'ADMIN' } : {}),
        redirect: to.fullPath,
      },
    }
  }

  if (to.meta.requiresAdmin && !isAdminRole(auth.currentUser.value?.role)) {
    return '/'
  }

  // 登录页兼作“唤醒终端”：保留页面以展示浏览器持久会话对应的账号，
  // 用户点击开始唤醒后再进入；注册页仍避免已登录用户重复进入。
  if (to.path === '/register' && auth.currentUser.value) {
    return isAdminRole(auth.currentUser.value.role) ? '/admin' : '/'
  }
})

saveLocale(i18n.global.locale.value)

// 统一 401 处理：清除登录态并带 redirect 跳登录（防重复跳转由调用频率与路径判定兜底）。
registerUnauthorizedHandler(() => {
  const auth = useAuth()
  auth.clearSession()
  if (router.currentRoute.value.path !== '/login') {
    void router.push({
      path: '/login',
      query: { redirect: router.currentRoute.value.fullPath },
    })
  }
})

createApp(App)
  .use(i18n)
  .use(router)
  .mount('#app')
