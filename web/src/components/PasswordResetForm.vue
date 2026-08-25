<script setup lang="ts">
import axios from 'axios'
import { onUnmounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { resetPassword, sendPasswordResetCode } from '../api'
import type { PasswordResetPayload } from '../types'

const emit = defineEmits<{
  reset: [username: string]
}>()
const { t } = useI18n()

const open = ref(false)
const usernameInput = ref<HTMLInputElement | null>(null)
const emailInput = ref<HTMLInputElement | null>(null)
const loading = ref(false)
const sendingCode = ref(false)
const codeCooldown = ref(0)
const codeSent = ref(false)
const error = ref('')
const form = reactive<PasswordResetPayload & { confirmPassword: string }>({
  username: '',
  email: '',
  verificationCode: '',
  newPassword: '',
  confirmPassword: '',
})
let cooldownTimer: number | undefined

function toggle() {
  open.value = !open.value
  error.value = ''
}

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
  if (!usernameInput.value?.reportValidity() || !emailInput.value?.reportValidity()) {
    return
  }

  sendingCode.value = true
  try {
    await sendPasswordResetCode({ username: form.username, email: form.email })
    codeSent.value = true
    startCooldown()
  } catch (requestError) {
    error.value = axios.isAxiosError(requestError)
      ? requestError.response?.data?.message || t('login.resetCodeError')
      : t('login.resetCodeError')
  } finally {
    sendingCode.value = false
  }
}

async function submit() {
  error.value = ''
  if (form.newPassword !== form.confirmPassword) {
    error.value = t('login.resetPasswordMismatch')
    return
  }

  loading.value = true
  try {
    await resetPassword({
      username: form.username,
      email: form.email,
      verificationCode: form.verificationCode,
      newPassword: form.newPassword,
    })
    emit('reset', form.username)
    open.value = false
  } catch (requestError) {
    error.value = axios.isAxiosError(requestError)
      ? requestError.response?.data?.message || t('login.resetError')
      : t('login.resetError')
  } finally {
    loading.value = false
  }
}

onUnmounted(() => window.clearInterval(cooldownTimer))
</script>

<template>
  <section class="password-reset">
    <button type="button" class="password-reset-toggle" @click="toggle">
      {{ open ? t('login.cancelReset') : t('login.forgotPassword') }}
    </button>

    <form v-if="open" class="password-reset-form" @submit.prevent="submit">
      <div class="password-reset-heading">
        <b>{{ t('login.resetTitle') }}</b>
        <small>{{ t('login.resetDescription') }}</small>
      </div>
      <label>
        {{ t('login.resetUsername') }}
        <input
          ref="usernameInput"
          v-model.trim="form.username"
          minlength="3"
          maxlength="50"
          pattern="[a-zA-Z0-9_]+"
          autocomplete="username"
          required
        >
      </label>
      <label>
        {{ t('login.resetEmail') }}
        <input
          ref="emailInput"
          v-model.trim="form.email"
          type="email"
          maxlength="254"
          autocomplete="email"
          required
        >
      </label>
      <label>
        {{ t('login.resetVerificationCode') }}
        <span class="verification-code-row">
          <input
            v-model.trim="form.verificationCode"
            inputmode="numeric"
            maxlength="6"
            pattern="[0-9]{6}"
            autocomplete="one-time-code"
            required
          >
          <button type="button" :disabled="sendingCode || codeCooldown > 0" @click="requestCode">
            {{
              sendingCode
                ? t('login.resetSendingCode')
                : codeCooldown > 0
                  ? t('login.resetResendCode', { seconds: codeCooldown })
                  : t('login.resetSendCode')
            }}
          </button>
        </span>
      </label>
      <p v-if="codeSent" class="verification-code-status">{{ t('login.resetCodeSent') }}</p>
      <label>
        {{ t('login.newPassword') }}
        <input
          v-model="form.newPassword"
          type="password"
          minlength="8"
          maxlength="16"
          pattern="^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]+$"
          autocomplete="new-password"
          required
        >
        <small>{{ t('login.resetPasswordHint') }}</small>
      </label>
      <label>
        {{ t('login.confirmNewPassword') }}
        <input
          v-model="form.confirmPassword"
          type="password"
          minlength="8"
          maxlength="16"
          autocomplete="new-password"
          required
        >
      </label>
      <p v-if="error" class="form-error">{{ error }}</p>
      <button class="login-submit" :disabled="loading">
        {{ loading ? t('login.resetting') : t('login.resetSubmit') }}
      </button>
    </form>
  </section>
</template>

<style scoped>
.password-reset {
  margin-top: 14px;
  text-align: center;
}

.password-reset-toggle {
  padding: 3px 0;
  color: #8f332b;
  font: inherit;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.password-reset-form {
  display: grid;
  gap: 15px;
  margin-top: 18px;
  padding-top: 20px;
  text-align: left;
  border-top: 1px solid #d8cbb8;
}

.password-reset-heading {
  display: grid;
  gap: 5px;
  color: #503a2f;
}

.password-reset-heading small {
  color: #887165;
  font-weight: 400;
  line-height: 1.6;
}

.password-reset-form label {
  display: grid;
  gap: 7px;
  color: #594438;
  font-size: 13px;
}

.password-reset-form label > small {
  color: #8b7769;
  font-size: 11px;
  line-height: 1.5;
}

.password-reset-form input {
  width: 100%;
  padding: 11px 12px;
  color: #382a22;
  font: inherit;
  background: #fff;
  border: 1px solid #cdbfa9;
  outline: none;
}

.password-reset-form input:focus {
  border-color: #8b342b;
  box-shadow: 0 0 0 2px #8b342b1a;
}
</style>
