<script setup lang="ts">
import { computed, ref, watch } from 'vue'
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
const isLoginPage = computed(() => route.path === '/login')
const mobileNavLabel = computed(() => mobileNavOpen.value
  ? t('common.closeMenu')
  : t('common.openMenu'))

watch(() => route.fullPath, () => {
  mobileNavOpen.value = false
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
      <RouterLink v-if="!isLoginPage" to="/">{{ t('common.catalog') }}</RouterLink>
      <RouterLink v-if="auth.currentUser.value && !isLoginPage" to="/profile">
        {{ t('common.profile') }}
      </RouterLink>
      <RouterLink v-if="isAdminRole(auth.currentUser.value?.role)" to="/admin">
        {{ t('common.admin') }}
      </RouterLink>
      <a href="#about">{{ t('common.about') }}</a>
      <RouterLink v-if="!auth.currentUser.value && !isLoginPage" to="/login">
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
