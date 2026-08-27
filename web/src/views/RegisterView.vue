<script setup lang="ts">
import axios from 'axios'
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { getCaptcha, register, sendRegistrationCode } from '../api'
import type { CaptchaChallenge, RegisterPayload } from '../types'

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
const captchaAnswer = ref('')
const captcha = ref<CaptchaChallenge>()
const captchaError = ref('')
const captchaPassed = ref(false)
const loading = ref(false)
const sendingCode = ref(false)
const codeCooldown = ref(0)
const codeSent = ref(false)
const error = ref('')
let cooldownTimer: number | undefined

const canSendCode = computed(() => {
  if (sendingCode.value || codeCooldown.value > 0) {
    return false
  }
  if (captchaPassed.value) {
    return false
  }
  return Boolean(captcha.value && captchaAnswer.value.trim())
})

async function loadCaptcha() {
  captchaPassed.value = false
  captchaError.value = ''
  captchaAnswer.value = ''
  try {
    captcha.value = await getCaptcha()
  } catch {
    captcha.value = undefined
    captchaError.value = t('register.captchaLoadError')
  }
}

async function onCooldownEnded() {
  // 冷却结束即可重发：重新展示人机题，与后端「每次发码都要过人机」一致。
  await loadCaptcha()
}

function startCooldown() {
  codeCooldown.value = 60
  window.clearInterval(cooldownTimer)
  cooldownTimer = window.setInterval(() => {
    codeCooldown.value -= 1
    if (codeCooldown.value <= 0) {
      window.clearInterval(cooldownTimer)
      cooldownTimer = undefined
      void onCooldownEnded()
    }
  }, 1000)
}

async function requestCode() {
  error.value = ''
  codeSent.value = false
  if (!emailInput.value?.reportValidity()) {
    return
  }
  if (!captcha.value || !captchaAnswer.value.trim()) {
    captchaError.value = t('register.captchaRequired')
    return
  }

  sendingCode.value = true
  try {
    await sendRegistrationCode({
      email: form.email,
      captchaId: captcha.value.captchaId,
      captchaAnswer: captchaAnswer.value.trim(),
    })
    codeSent.value = true
    captchaPassed.value = true
    captchaAnswer.value = ''
    captchaError.value = ''
    // 发码成功后不立刻换题，避免误导用户以为注册还要再做人机。
    captcha.value = undefined
    startCooldown()
  } catch (requestError) {
    error.value = axios.isAxiosError(requestError)
      ? requestError.response?.data?.message || t('register.codeError')
      : t('register.codeError')
    captchaPassed.value = false
    // 校验失败或过期时同步刷新一道新题。
    await loadCaptcha()
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
  } catch (requestError) {
    error.value = axios.isAxiosError(requestError)
      ? requestError.response?.data?.message || t('register.error')
      : t('register.error')
  } finally {
    loading.value = false
  }
}

onMounted(loadCaptcha)
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
        <div class="captcha-block">
          <span class="captcha-label">{{ t('register.captcha') }}</span>
          <template v-if="captchaPassed">
            <p class="verification-code-status">{{ t('register.captchaPassed') }}</p>
          </template>
          <template v-else>
            <span class="captcha-question">{{ captcha?.question || t('register.captchaLoading') }}</span>
            <span class="verification-code-row">
              <input
                v-model.trim="captchaAnswer"
                inputmode="numeric"
                maxlength="10"
                :disabled="!captcha"
                :placeholder="t('register.captchaPlaceholder')"
              >
              <button type="button" class="captcha-refresh" :disabled="!captcha" @click="loadCaptcha">
                {{ t('register.captchaRefresh') }}
              </button>
            </span>
            <small v-if="captchaError" class="captcha-error">{{ captchaError }}</small>
          </template>
        </div>
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
              :disabled="!canSendCode"
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
          <input v-model="form.password" type="password" minlength="8" maxlength="16" pattern="^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]+$" autocomplete="new-password" required>
          <small>{{ t('register.passwordHint') }}</small>
        </label>
        <label>
          {{ t('register.confirmPassword') }}
          <input v-model="form.confirmPassword" type="password" minlength="8" maxlength="16" autocomplete="new-password" required>
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
