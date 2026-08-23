<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import { useAuth } from '../auth'
import type { LoginPayload, UserRole } from '../types'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuth()

const form = reactive<LoginPayload>({
  username: typeof route.query.username === 'string' ? route.query.username : '',
  password: '',
  role: route.query.role === 'ADMIN' ? 'ADMIN' : 'USER',
})
const loading = ref(false)
const error = ref('')
const registrationSucceeded = route.query.registered === '1'

function selectRole(role: UserRole) {
  form.role = role
  error.value = ''
}

async function submit() {
  loading.value = true
  error.value = ''

  try {
    const user = await auth.login(form)
    const requestedRedirect = typeof route.query.redirect === 'string' ? route.query.redirect : undefined
    await router.replace(user.role === 'ADMIN' ? '/admin' : requestedRedirect || '/')
  } catch {
    error.value = t('login.error')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="login-page">
    <div class="login-art">
      <span class="login-seal">炎</span>
      <p>{{ t('login.eyebrow') }}</p>
      <h1>{{ t('login.artTitle') }}</h1>
      <blockquote>{{ t('login.quote') }}</blockquote>
    </div>

    <div class="login-stage">
      <div class="login-panel">
      <div class="login-heading">
        <small>{{ t('login.welcome') }}</small>
        <h2>{{ t('login.title') }}</h2>
        <p>{{ t('login.description') }}</p>
      </div>

      <div class="role-tabs">
        <button :class="{ active: form.role === 'USER' }" @click="selectRole('USER')">
          <b>{{ t('login.user') }}</b>
          <span>{{ t('login.userHint') }}</span>
        </button>
        <button :class="{ active: form.role === 'ADMIN' }" @click="selectRole('ADMIN')">
          <b>{{ t('login.admin') }}</b>
          <span>{{ t('login.adminHint') }}</span>
        </button>
      </div>

      <form class="login-form" @submit.prevent="submit">
        <p v-if="registrationSucceeded" class="form-success">
          {{ t('login.registrationSuccess') }}
        </p>
        <label>
          {{ t('login.username') }}
          <input v-model.trim="form.username" autocomplete="username" required>
        </label>
        <label>
          {{ t('login.password') }}
          <input v-model="form.password" type="password" autocomplete="current-password" required>
        </label>

        <p v-if="error" class="form-error">{{ error }}</p>

        <button class="login-submit" :disabled="loading">
          {{ loading ? t('login.loggingIn') : t('login.submit') }}
        </button>
        <div class="register-entry">
          <span>{{ t('login.noAccount') }}</span>
          <RouterLink to="/register">{{ t('login.registerNow') }}</RouterLink>
        </div>
      </form>
      </div>
    </div>
  </section>
</template>
