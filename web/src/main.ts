import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'

import App from './App.vue'
import AdminView from './views/AdminView.vue'
import FoodDetailView from './views/FoodDetailView.vue'
import HomeView from './views/HomeView.vue'
import LoginView from './views/LoginView.vue'
import RegisterView from './views/RegisterView.vue'
import { useAuth } from './auth'
import { i18n, saveLocale } from './i18n'

import './style.css'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: HomeView,
      meta: { requiresAuth: true },
    },
    {
      path: '/foods/:id',
      component: FoodDetailView,
      meta: { requiresAuth: true },
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
      path: '/admin',
      component: AdminView,
      meta: { requiresAuth: true, requiresAdmin: true },
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

  if (to.meta.requiresAdmin && auth.currentUser.value?.role !== 'ADMIN') {
    return '/'
  }

  if ((to.path === '/login' || to.path === '/register') && auth.currentUser.value) {
    return auth.currentUser.value.role === 'ADMIN' ? '/admin' : '/'
  }
})

saveLocale(i18n.global.locale.value)

createApp(App)
  .use(i18n)
  .use(router)
  .mount('#app')
