<script setup lang="ts">
import axios from 'axios'
import { computed, onUnmounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { resetPassword, sendPasswordResetCode } from '../api'
import type { PasswordResetPayload } from '../types'

const emit = defineEmits<{
  reset: [username: string]
}>()
const { t } = useI18n()

const open = ref(false)
const formElement = ref<HTMLFormElement | null>(null)
type Field = keyof PasswordResetPayload | 'confirmPassword'
const fields: Field[] = ['username', 'email', 'verificationCode', 'newPassword', 'confirmPassword']
const errors = reactive<Partial<Record<Field, string>>>({})
const operation = ref<'send' | 'reset' | null>(null)
const loading = computed(() => operation.value === 'reset')
const sendingCode = computed(() => operation.value === 'send')
const codeSent = ref(false)
const error = ref('')
const uncertain = ref(false)
const form = reactive<PasswordResetPayload & { confirmPassword: string }>({
  username: '',
  email: '',
  verificationCode: '',
  newPassword: '',
  confirmPassword: '',
})
const identity = computed(() => JSON.stringify([form.username.trim(), form.email.trim().toLowerCase()]))
const cooldowns = reactive(new Map<string, number>())
const now = ref(Date.now())
const codeCooldown = computed(() => Math.max(0, Math.ceil(((cooldowns.get(identity.value) || 0) - now.value) / 1000)))
let generation = 0
let active = true
let controller: AbortController | undefined
const cooldownTimer = window.setInterval(() => {
  now.value = Date.now()
  for (const [key, until] of cooldowns) if (until <= now.value) cooldowns.delete(key)
}, 1000)

function invalidate() {
  generation++
  controller?.abort()
  controller = undefined
  operation.value = null
}
function clearSecrets() {
  form.verificationCode = ''; form.newPassword = ''; form.confirmPassword = ''
  for (const key of fields) delete errors[key]
  codeSent.value = false
}
watch(identity, () => {
  invalidate()
  form.verificationCode = ''
  codeSent.value = false
  error.value = ''
  delete errors.verificationCode
}, { flush: 'sync' })

function toggle() {
  if (open.value) {
    if (loading.value) uncertain.value = true
    invalidate()
    clearSecrets()
  }
  open.value = !open.value
  error.value = ''
}

function messageFor(field: Field): string | undefined {
  const value = form[field]
  if (field === 'username') {
    if (!/^[A-Za-z0-9_]{3,50}$/.test(value.trim())) return 'login.resetUsernameHint'
  } else if (field === 'email') {
    const email = formElement.value?.querySelector<HTMLInputElement>('[name=email]')
    if (!value.trim() || value.trim().length > 254 || email?.validity.typeMismatch) return 'login.resetEmailInvalid'
  } else if (field === 'verificationCode') {
    if (!/^[0-9]{6}$/.test(value.trim())) return 'login.resetCodeHint'
  } else if (field === 'newPassword') {
    if (value.length < 8 || value.length > 16) return 'login.resetPasswordLength'
    if (!/^[A-Za-z0-9]+$/.test(value)) return 'login.resetPasswordCharacters'
    if (!/[A-Za-z]/.test(value) || !/[0-9]/.test(value)) return 'login.resetPasswordCombination'
  } else if (!value || value !== form.newPassword) return 'login.resetPasswordMismatch'
  return undefined
}
function edited(field: Field) {
  if (errors[field]) errors[field] = messageFor(field)
  if (field === 'newPassword' && errors.confirmPassword) errors.confirmPassword = messageFor('confirmPassword')
}
function validate(targets: Field[]) {
  for (const field of targets) errors[field] = messageFor(field)
  const first = targets.find(field => errors[field])
  if (first) formElement.value?.querySelector<HTMLInputElement>(`[name=${first}]`)?.focus()
  return !first
}
function current(id: number) { return active && open.value && generation === id }
function serverMessage(cause: unknown, fallback: string) {
  const message = axios.isAxiosError(cause) ? cause.response?.data?.message : undefined
  return typeof message === 'string' ? message : t(fallback)
}

async function requestCode() {
  if (operation.value || codeCooldown.value > 0 || !validate(['username', 'email'])) return
  const key = identity.value
  const payload = { username: form.username.trim(), email: form.email.trim() }
  const id = ++generation
  controller = new AbortController()
  operation.value = 'send'
  error.value = ''
  codeSent.value = false
  // Aborting a client request cannot undo mail already sent by the server.
  cooldowns.set(key, Date.now() + 60_000)
  try {
    await sendPasswordResetCode(payload, controller.signal)
    if (!current(id)) return
    codeSent.value = true
    cooldowns.set(key, Date.now() + 60_000)
  } catch (cause) {
    if (!current(id)) return
    const status = axios.isAxiosError(cause) ? cause.response?.status : undefined
    if (status && status < 500 && status !== 429) cooldowns.delete(key)
    if (status === 429 && axios.isAxiosError(cause)) {
      const seconds = Number(cause.response?.headers['retry-after'])
      if (Number.isFinite(seconds) && seconds > 0) cooldowns.set(key, Date.now() + Math.min(seconds, 3600) * 1000)
    }
    error.value = !status || status >= 500 ? t('login.resetCodeUnknown') : serverMessage(cause, 'login.resetCodeError')
  } finally {
    if (current(id)) { operation.value = null; controller = undefined }
  }
}

async function submit() {
  if (operation.value || !validate(fields)) return
  const payload = Object.freeze({ username: form.username.trim(), email: form.email.trim(),
    verificationCode: form.verificationCode.trim(), newPassword: form.newPassword })
  const id = ++generation
  controller = new AbortController()
  operation.value = 'reset'
  error.value = ''
  uncertain.value = false
  try {
    await resetPassword(payload, controller.signal)
    if (!current(id)) return
    clearSecrets()
    open.value = false
    operation.value = null; controller = undefined
    generation++
    emit('reset', payload.username)
  } catch (cause) {
    if (!current(id)) return
    const status = axios.isAxiosError(cause) ? cause.response?.status : undefined
    if (!status || status >= 500) uncertain.value = true
    else error.value = serverMessage(cause, 'login.resetError')
  } finally {
    if (current(id)) { operation.value = null; controller = undefined }
  }
}

onUnmounted(() => {
  active = false
  invalidate()
  clearSecrets()
  window.clearInterval(cooldownTimer)
})
</script>

<template>
  <section class="password-reset">
    <button type="button" class="password-reset-toggle" @click="toggle">
      {{ open ? t('login.cancelReset') : t('login.forgotPassword') }}
    </button>

    <p v-if="uncertain" class="form-error" role="alert">{{ t('login.resetResultUnknown') }}</p>
    <form v-if="open" ref="formElement" class="password-reset-form" novalidate @submit.prevent="submit">
      <div class="password-reset-heading">
        <b>{{ t('login.resetTitle') }}</b>
        <small>{{ t('login.resetDescription') }}</small>
      </div>
      <label>
        {{ t('login.resetUsername') }}
        <input
          name="username"
          v-model.trim="form.username"
          :disabled="loading"
          :aria-invalid="!!errors.username"
          aria-describedby="reset-username-hint reset-username-error"
          @input="edited('username')"
          autocomplete="username"
          required
        >
        <small id="reset-username-hint">{{ t('login.resetUsernameHint') }}</small>
        <small v-if="errors.username" id="reset-username-error" class="field-error">{{ t(errors.username) }}</small>
      </label>
      <label>
        {{ t('login.resetEmail') }}
        <input
          name="email"
          v-model.trim="form.email"
          type="email"
          :disabled="loading"
          :aria-invalid="!!errors.email"
          aria-describedby="reset-email-error"
          @input="edited('email')"
          autocomplete="email"
          required
        >
        <small v-if="errors.email" id="reset-email-error" class="field-error">{{ t(errors.email) }}</small>
      </label>
      <label>
        {{ t('login.resetVerificationCode') }}
        <span class="verification-code-row">
          <input
            name="verificationCode"
            v-model.trim="form.verificationCode"
            inputmode="numeric"
            :disabled="loading"
            :aria-invalid="!!errors.verificationCode"
            aria-describedby="reset-code-hint reset-code-error"
            @input="edited('verificationCode')"
            autocomplete="one-time-code"
            required
          >
          <button type="button" :disabled="!!operation || codeCooldown > 0" @click="requestCode">
            {{
              sendingCode
                ? t('login.resetSendingCode')
                : codeCooldown > 0
                  ? t('login.resetResendCode', { seconds: codeCooldown })
                  : t('login.resetSendCode')
            }}
          </button>
        </span>
        <small id="reset-code-hint">{{ t('login.resetCodeHint') }}</small>
        <small v-if="errors.verificationCode" id="reset-code-error" class="field-error">{{ t(errors.verificationCode) }}</small>
      </label>
      <p v-if="codeSent" class="verification-code-status" role="status">{{ t('login.resetCodeSent') }}</p>
      <label>
        {{ t('login.newPassword') }}
        <input
          name="newPassword"
          v-model="form.newPassword"
          type="password"
          :disabled="loading"
          :aria-invalid="!!errors.newPassword"
          aria-describedby="reset-password-hint reset-password-error"
          @input="edited('newPassword')"
          autocomplete="new-password"
          required
        >
        <small id="reset-password-hint">{{ t('login.resetPasswordHint') }}</small>
        <small v-if="errors.newPassword" id="reset-password-error" class="field-error">{{ t(errors.newPassword) }}</small>
      </label>
      <label>
        {{ t('login.confirmNewPassword') }}
        <input
          name="confirmPassword"
          v-model="form.confirmPassword"
          type="password"
          :disabled="loading"
          :aria-invalid="!!errors.confirmPassword"
          aria-describedby="reset-confirm-error"
          @input="edited('confirmPassword')"
          autocomplete="new-password"
          required
        >
        <small v-if="errors.confirmPassword" id="reset-confirm-error" class="field-error">{{ t(errors.confirmPassword) }}</small>
      </label>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <button class="login-submit" :disabled="!!operation">
        {{ loading ? t('login.resetting') : t('login.resetSubmit') }}
      </button>
    </form>
  </section>
</template>

<style scoped>
.password-reset-form .field-error { color: var(--color-danger, #a22b27); }
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
