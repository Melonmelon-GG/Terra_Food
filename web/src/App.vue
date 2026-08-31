<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'

import { isAdminRole, useAuth } from './auth'
import { saveLocale, type SupportedLocale } from './i18n'
import BackgroundMusic from './components/BackgroundMusic.vue'
import AchievementToast from './components/AchievementToast.vue'
import AgentPanel from './components/AgentPanel.vue'

const { locale, t } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuth()
const mobileNavOpen = ref(false)
const nextLocaleLabel = computed(() => locale.value === 'zh-CN' ? 'EN' : '中')
// 不参与常规导航展示的页面：登录/注册/关于。珍馐图鉴与关于在这些页面仍保持可见。
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
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', closeMenuOnOutside)
  document.removeEventListener('keydown', closeMenuOnKeydown)
})

function toggleLocale() {
  const nextLocale: SupportedLocale = locale.value === 'zh-CN' ? 'en-US' : 'zh-CN'
  locale.value = nextLocale
  saveLocale(nextLocale)
  mobileNavOpen.value = false
}

async function logout() {
  mobileNavOpen.value = false
  await auth.logout()
  await router.push('/login')
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
      <RouterLink to="/" @click.prevent="navigateHome">{{ t('common.catalog') }}</RouterLink>
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
    </nav>
  </header>

  <main>
    <RouterView />
  </main>

  <BackgroundMusic />
  <AchievementToast />
  <AgentPanel v-if="auth.currentUser.value" />

  <footer id="about">
    {{ t('footer') }}
  </footer>
</template>
