<script setup lang="ts">
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'

import { isAdminRole, useAuth } from './auth'
import { saveLocale, type SupportedLocale } from './i18n'
const BackgroundMusic = defineAsyncComponent(() => import('./components/BackgroundMusic.vue'))
const AchievementToast = defineAsyncComponent(() => import('./components/AchievementToast.vue'))
const AgentPanel = defineAsyncComponent(() => import('./components/AgentPanel.vue'))
import { useTheme, type ThemeMode } from './theme'

const { locale, t } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuth()
const mobileNavOpen = ref(false)
const routeLoadFailed = ref(false)
const { themeMode, setTheme } = useTheme()
const nextLocaleLabel = computed(() => locale.value === 'zh-CN' ? 'EN' : '中')
// 不参与常规导航展示的页面：登录/注册/关于。
const isAuthFlowPage = computed(() => ['/login', '/register', '/about'].includes(route.path))
const mobileNavLabel = computed(() => mobileNavOpen.value
  ? t('common.closeMenu')
  : t('common.openMenu'))

watch(() => route.fullPath, () => {
  mobileNavOpen.value = false
})

function navigateHome() {
  mobileNavOpen.value = false
  if (route.path !== '/') {
    void router.push('/')
    return
  }
  // 已在首页：菜单里的"珍馐图鉴"变为"回到地图默认视角"，
  // 避免同路由 no-op 造成"点了没反应"的观感。
  if (route.fullPath !== '/?map=reset') {
    void router.push({ path: '/', query: { map: 'reset' } })
  }
}

function closeMenuOnOutside(event: PointerEvent) {
  if (!mobileNavOpen.value) return
  const header = document.querySelector('#app > header')
  if (header && !header.contains(event.target as Node)) {
    mobileNavOpen.value = false
  }
}

function closeMenuOnKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && mobileNavOpen.value) {
    mobileNavOpen.value = false
  }
}

onMounted(() => {
  document.addEventListener('pointerdown', closeMenuOnOutside)
  document.addEventListener('keydown', closeMenuOnKeydown)
  window.addEventListener('terra:route-load-error', handleRouteLoadError)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', closeMenuOnOutside)
  document.removeEventListener('keydown', closeMenuOnKeydown)
  window.removeEventListener('terra:route-load-error', handleRouteLoadError)
})

function handleRouteLoadError() {
  routeLoadFailed.value = true
}

function reloadPage() {
  window.location.reload()
}

function toggleLocale() {
  const nextLocale: SupportedLocale = locale.value === 'zh-CN' ? 'en-US' : 'zh-CN'
  locale.value = nextLocale
  saveLocale(nextLocale)
  mobileNavOpen.value = false
}

async function logout() {
  mobileNavOpen.value = false
  try {
    await auth.logout()
  } finally {
    await router.push('/login')
  }
}
</script>

<template>
  <header>
    <RouterLink to="/" class="brand">
      <span class="seal">炎</span>
      <div>
        <strong>{{ t('common.appName') }}</strong>
        <small>{{ t('common.tagline') }}</small>
      </div>
    </RouterLink>

    <button
      class="mobile-nav-toggle"
      type="button"
      :aria-label="mobileNavLabel"
      :aria-expanded="mobileNavOpen"
      aria-controls="primary-navigation"
      @click="mobileNavOpen = !mobileNavOpen"
    >
      <span></span>
      <span></span>
      <span></span>
    </button>

    <nav id="primary-navigation" :class="{ 'is-open': mobileNavOpen }" @click="mobileNavOpen = false">
      <RouterLink v-if="route.path !== '/login'" to="/" @click.prevent="navigateHome">
        {{ t('common.catalog') }}
      </RouterLink>
      <RouterLink v-if="auth.currentUser.value && !isAuthFlowPage" to="/profile">
        {{ t('common.profile') }}
      </RouterLink>
      <RouterLink v-if="isAdminRole(auth.currentUser.value?.role)" to="/admin">
        {{ t('common.admin') }}
      </RouterLink>
      <RouterLink to="/about">{{ t('common.about') }}</RouterLink>
      <RouterLink v-if="!auth.currentUser.value && !isAuthFlowPage" to="/login">
        {{ t('common.login') }}
      </RouterLink>
      <button v-if="auth.currentUser.value" class="account-link" @click="logout">
        {{ auth.currentUser.value.displayName }} · {{ t('common.logout') }}
      </button>
      <button class="language-switch" :aria-label="nextLocaleLabel" @click="toggleLocale">
        {{ nextLocaleLabel }}
      </button>
      <label class="theme-toggle">
        <span class="sr-only">{{ t('theme.label') }}</span>
        <select :value="themeMode" :aria-label="t('theme.label')" @change="setTheme(($event.target as HTMLSelectElement).value as ThemeMode)">
          <option value="system">{{ t('theme.system') }}</option>
          <option value="light">{{ t('theme.light') }}</option>
          <option value="dark">{{ t('theme.dark') }}</option>
        </select>
      </label>
    </nav>
  </header>

  <main>
    <div v-if="routeLoadFailed" class="route-load-error" role="alert">
      <span>{{ t('common.routeLoadFailed') }}</span>
      <button type="button" @click="reloadPage">{{ t('common.reload') }}</button>
    </div>
    <RouterView :key="`${route.fullPath}:${auth.getSessionRevision()}:${auth.currentUser.value?.id ?? 'anonymous'}`" />
  </main>

  <BackgroundMusic v-if="!isAuthFlowPage" :launcher-visible="route.path !== '/'" />
  <AchievementToast />
  <AgentPanel v-if="auth.currentUser.value" :launcher-visible="route.path !== '/'" />

  <footer id="about">
    {{ t('footer') }}
  </footer>
</template>
