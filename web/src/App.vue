<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, RouterView, useRouter } from 'vue-router'

import { useAuth } from './auth'
import { saveLocale, type SupportedLocale } from './i18n'

const { locale, t } = useI18n()
const router = useRouter()
const auth = useAuth()
const nextLocaleLabel = computed(() => locale.value === 'zh-CN' ? 'EN' : '中')

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
      <RouterLink to="/">{{ t('common.catalog') }}</RouterLink>
      <RouterLink v-if="auth.currentUser.value?.role === 'ADMIN'" to="/admin">
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

  <footer id="about">
    {{ t('footer') }}
  </footer>
</template>
