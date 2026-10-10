import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'

import App from './App.vue'
import { AUTH_SESSION_CHANGE_KEY, isAdminRole, useAuth } from './auth'
import { registerSessionRevisionProvider, registerSessionSnapshotProvider, registerUnauthorizedHandler } from './api'
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
// Theme overrides load last so legacy fixed colors cannot win the cascade.
import './theme.css'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: () => import('./views/HomeView.vue'),
    },
    {
      path: '/foods/:id',
      component: () => import('./views/FoodDetailView.vue'),
    },
    {
      path: '/login',
      component: () => import('./views/AuthView.vue'),
    },
    {
      path: '/register',
      component: () => import('./views/AuthView.vue'),
    },
    {
      path: '/about',
      component: () => import('./views/AboutView.vue'),
    },
    {
      path: '/users/:id',
      component: () => import('./views/UserPublicView.vue'),
    },
    {
      path: '/profile',
      component: () => import('./views/ProfileView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/admin',
      component: () => import('./views/AdminView.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
    },
    {
      path: '/:pathMatch(.*)*',
      component: () => import('./views/NotFoundView.vue'),
    },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuth()
  if (to.meta.requiresAuth) {
    await auth.restoreSession()
  } else {
    void auth.restoreSession()
  }

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

router.onError(() => {
  document.documentElement.dataset.routeLoadFailed = 'true'
  window.dispatchEvent(new CustomEvent('terra:route-load-error'))
})

saveLocale(i18n.global.locale.value)

// 统一 401 处理：清除登录态并带 redirect 跳登录（防重复跳转由调用频率与路径判定兜底）。
const auth = useAuth()
registerSessionRevisionProvider(() => auth.getSessionRevision())
registerSessionSnapshotProvider(() => ({ revision: auth.getSessionRevision(), userId: auth.currentUser.value?.id, confirmed: auth.sessionConfirmed.value }))
registerUnauthorizedHandler((requestRevision) => {
  if (requestRevision !== undefined && requestRevision !== auth.getSessionRevision()) return
  auth.clearSession()
  if (router.currentRoute.value.path !== '/login') {
    void router.push({
      path: '/login',
      query: { redirect: router.currentRoute.value.fullPath },
    })
  }
})

window.addEventListener('storage', (event) => {
  if (event.key === AUTH_SESSION_CHANGE_KEY) void auth.handleExternalSessionChange(event.newValue)
})

window.addEventListener('focus', () => { void auth.verifyForegroundSession() })
window.addEventListener('pageshow', () => { void auth.verifyForegroundSession() })
document.addEventListener('visibilitychange', () => { void auth.verifyForegroundSession() })

createApp(App)
  .use(i18n)
  .use(router)
  .mount('#app')

document.querySelector('#startup-shell')?.remove()
performance.mark('terra:app-mounted')
