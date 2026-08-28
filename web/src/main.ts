import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'

import App from './App.vue'
import AdminView from './views/AdminView.vue'
import FoodDetailView from './views/FoodDetailView.vue'
import HomeView from './views/HomeView.vue'
import LoginView from './views/LoginView.vue'
import NotFoundView from './views/NotFoundView.vue'
import ProfileView from './views/ProfileView.vue'
import RegisterView from './views/RegisterView.vue'
import { isAdminRole, useAuth } from './auth'
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
import './responsive.css'
import './profile.css'
import './agent.css'

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

  if ((to.path === '/login' || to.path === '/register') && auth.currentUser.value) {
    return isAdminRole(auth.currentUser.value.role) ? '/admin' : '/'
  }
})

saveLocale(i18n.global.locale.value)

createApp(App)
  .use(i18n)
  .use(router)
  .mount('#app')
