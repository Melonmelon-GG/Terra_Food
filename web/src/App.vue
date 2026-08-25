<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'

import { isAdminRole, useAuth } from './auth'
import { saveLocale, type SupportedLocale } from './i18n'
import BackgroundMusic from './components/BackgroundMusic.vue'
import AchievementToast from './components/AchievementToast.vue'

const { locale, t } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuth()
const nextLocaleLabel = computed(() => locale.value === 'zh-CN' ? 'EN' : '中')
const isLoginPage = computed(() => route.path === '/login')

function toggleLocale() {
  const nextLocale: SupportedLocale = locale.value === 'zh-CN' ? 'en-US' : 'zh-CN'
  locale.value = nextLocale
  saveLocale(nextLocale)
}

async function logout() {
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

    <nav>
      <RouterLink v-if="!isLoginPage" to="/">{{ t('common.catalog') }}</RouterLink>
      <RouterLink v-if="auth.currentUser.value && !isLoginPage" to="/profile">
        {{ t('common.profile') }}
      </RouterLink>
      <RouterLink v-if="isAdminRole(auth.currentUser.value?.role)" to="/admin">
        {{ t('common.admin') }}
      </RouterLink>
      <a href="#about">{{ t('common.about') }}</a>
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

  <footer id="about">
    {{ t('footer') }}
  </footer>
</template>
