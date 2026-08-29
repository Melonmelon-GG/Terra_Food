<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import { isAdminRole, useAuth } from '../auth'
import PasswordResetForm from '../components/PasswordResetForm.vue'
import type { LoginPayload, LoginRole } from '../types'

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
const resetSucceeded = ref(false)

function selectRole(role: LoginRole) {
  form.role = role
  error.value = ''
}

function handlePasswordReset(username: string) {
  form.username = username
  form.password = ''
  resetSucceeded.value = true
}

async function submit() {
  loading.value = true
  error.value = ''

  try {
    // Login passwords only contain letters and numbers. Normalize full-width input
    // and trim invisible whitespace introduced by copy and paste.
    const user = await auth.login({
      ...form,
      username: form.username.trim(),
      password: form.password.normalize('NFKC').trim(),
    })
    const requestedRedirect = typeof route.query.redirect === 'string' ? route.query.redirect : undefined
    await router.replace(isAdminRole(user.role) ? '/admin' : requestedRedirect || '/')
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
        <p v-if="resetSucceeded" class="form-success">
          {{ t('login.resetSuccess') }}
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
      <PasswordResetForm @reset="handlePasswordReset" />
      </div>
    </div>
  </section>
</template>
