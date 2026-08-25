<script setup lang="ts">
import { onUnmounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { register, sendRegistrationCode } from '../api'
import type { RegisterPayload } from '../types'

const { t } = useI18n()
const router = useRouter()

const form = reactive<RegisterPayload & { confirmPassword: string }>({
  username: '',
  displayName: '',
  email: '',
  verificationCode: '',
  password: '',
  confirmPassword: '',
})
const emailInput = ref<HTMLInputElement | null>(null)
const loading = ref(false)
const sendingCode = ref(false)
const codeCooldown = ref(0)
const codeSent = ref(false)
const error = ref('')
let cooldownTimer: number | undefined

function startCooldown() {
  codeCooldown.value = 60
  window.clearInterval(cooldownTimer)
  cooldownTimer = window.setInterval(() => {
    codeCooldown.value -= 1
    if (codeCooldown.value <= 0) {
      window.clearInterval(cooldownTimer)
      cooldownTimer = undefined
    }
  }, 1000)
}

async function requestCode() {
  error.value = ''
  codeSent.value = false
  if (!emailInput.value?.reportValidity()) {
    return
  }

  sendingCode.value = true
  try {
    await sendRegistrationCode({ email: form.email })
    codeSent.value = true
    startCooldown()
  } catch {
    error.value = t('register.codeError')
  } finally {
    sendingCode.value = false
  }
}

async function submit() {
  error.value = ''
  if (form.password !== form.confirmPassword) {
    error.value = t('register.passwordMismatch')
    return
  }

  loading.value = true
  try {
    await register({
      username: form.username,
      displayName: form.displayName,
      email: form.email,
      verificationCode: form.verificationCode,
      password: form.password,
    })
    await router.replace({
      path: '/login',
      query: {
        registered: '1',
        username: form.username,
      },
    })
  } catch {
    error.value = t('register.error')
  } finally {
    loading.value = false
  }
}

onUnmounted(() => window.clearInterval(cooldownTimer))
</script>

<template>
  <section class="login-page">
    <div class="login-art register-art">
      <span class="login-seal">炎</span>
      <p>{{ t('register.eyebrow') }}</p>
      <h1>{{ t('register.artTitle') }}</h1>
      <blockquote>{{ t('register.quote') }}</blockquote>
    </div>

    <div class="login-panel">
      <div class="login-heading">
        <small>{{ t('register.welcome') }}</small>
        <h2>{{ t('register.title') }}</h2>
        <p>{{ t('register.description') }}</p>
      </div>

      <form class="login-form" @submit.prevent="submit">
        <label>
          {{ t('register.username') }}
          <input
            v-model.trim="form.username"
            minlength="3"
            maxlength="50"
            pattern="[a-zA-Z0-9_]+"
            autocomplete="username"
            required
          >
          <small>{{ t('register.usernameHint') }}</small>
        </label>
        <label>
          {{ t('register.displayName') }}
          <input v-model.trim="form.displayName" minlength="2" maxlength="50" required>
        </label>
        <label>
          {{ t('register.email') }}
          <input
            ref="emailInput"
            v-model.trim="form.email"
            type="email"
            maxlength="254"
            autocomplete="email"
            required
          >
          <small>{{ t('register.emailHint') }}</small>
        </label>
        <label>
          {{ t('register.verificationCode') }}
          <span class="verification-code-row">
            <input
              v-model.trim="form.verificationCode"
              inputmode="numeric"
              maxlength="6"
              pattern="[0-9]{6}"
              autocomplete="one-time-code"
              required
            >
            <button
              type="button"
              :disabled="sendingCode || codeCooldown > 0"
              @click="requestCode"
            >
              {{
                sendingCode
                  ? t('register.sendingCode')
                  : codeCooldown > 0
                    ? t('register.resendCode', { seconds: codeCooldown })
                    : t('register.sendCode')
              }}
            </button>
          </span>
        </label>
        <p v-if="codeSent" class="verification-code-status">{{ t('register.codeSent') }}</p>
        <label>
          {{ t('register.password') }}
          <input v-model="form.password" type="password" minlength="6" maxlength="72" autocomplete="new-password" required>
        </label>
        <label>
          {{ t('register.confirmPassword') }}
          <input v-model="form.confirmPassword" type="password" minlength="6" maxlength="72" autocomplete="new-password" required>
        </label>

        <p v-if="error" class="form-error">{{ error }}</p>

        <button class="login-submit" :disabled="loading">
          {{ loading ? t('register.registering') : t('register.submit') }}
        </button>
        <div class="register-entry">
          <span>{{ t('register.hasAccount') }}</span>
          <RouterLink to="/login">{{ t('register.backToLogin') }}</RouterLink>
        </div>
      </form>
    </div>
  </section>
</template>
