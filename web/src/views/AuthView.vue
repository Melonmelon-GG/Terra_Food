<script setup lang="ts">
import { computed, onUnmounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import { getCaptcha, register, sendRegistrationCode } from '../api'
import { isAdminRole, useAuth } from '../auth'
import { apiErrorMessage } from '../apiError'
import PasswordResetForm from '../components/PasswordResetForm.vue'
import type { CaptchaChallenge, LoginPayload, RegisterPayload } from '../types'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuth()
const mode = computed(() => route.path === '/register' ? 'register' : 'login')
const busy = ref(false)
const error = ref('')
const resetSucceeded = ref(false)
const sendingCode = ref(false)
const codeCooldown = ref(0)
const captcha = ref<CaptchaChallenge>()
const captchaAnswer = ref('')
let timer: number | undefined

const loginForm = reactive<LoginPayload>({
  username: typeof route.query.username === 'string' ? route.query.username : '',
  password: '', role: route.query.role === 'ADMIN' ? 'ADMIN' : 'USER',
})
const registerForm = reactive<RegisterPayload & { confirmPassword: string }>({
  username: '', displayName: '', email: '', verificationCode: '', password: '', confirmPassword: '',
})

function clearSecrets() {
  resetSucceeded.value = false
  loginForm.password = ''
  registerForm.password = ''
  registerForm.confirmPassword = ''
  registerForm.verificationCode = ''
  captchaAnswer.value = ''
  error.value = ''
}

function handlePasswordReset(username: string) {
  loginForm.username = username
  loginForm.password = ''
  error.value = ''
  resetSucceeded.value = true
}

async function loadCaptcha() {
  try { captcha.value = await getCaptcha() }
  catch (cause) { error.value = apiErrorMessage(cause, t('register.captchaLoadError')) }
}

watch(mode, (next) => {
  clearSecrets()
  if (next === 'register') void loadCaptcha()
}, { immediate: true })

function safeRedirect(): string {
  const value = typeof route.query.redirect === 'string' ? route.query.redirect : ''
  if (!value.startsWith('/') || value.startsWith('//') || /[\\\x00-\x1f]/.test(value)) return '/'
  try {
    const destination = new URL(value, window.location.origin)
    if (destination.origin !== window.location.origin || /^\/(login|register)(\/|$)/.test(destination.pathname)) return '/'
    return destination.pathname + destination.search + destination.hash
  } catch { return '/' }
}

async function submitLogin() {
  busy.value = true; error.value = ''
  try {
    const user = await auth.login({ ...loginForm, username: loginForm.username.trim(), password: loginForm.password.normalize('NFKC').trim() })
    loginForm.password = ''
    await router.replace(isAdminRole(user.role) ? '/admin' : safeRedirect())
  } catch (cause) { error.value = apiErrorMessage(cause, t('login.error')) }
  finally { busy.value = false }
}

async function sendCode() {
  if (!captcha.value || !captchaAnswer.value.trim() || sendingCode.value || codeCooldown.value > 0) return
  sendingCode.value = true; error.value = ''
  try {
    await sendRegistrationCode({ email: registerForm.email.trim(), captchaId: captcha.value.captchaId, captchaAnswer: captchaAnswer.value.trim() })
    codeCooldown.value = 60
    timer = window.setInterval(() => {
      codeCooldown.value--
      if (codeCooldown.value <= 0) { window.clearInterval(timer); timer = undefined; void loadCaptcha() }
    }, 1000)
    captcha.value = undefined
  } catch (cause) { error.value = apiErrorMessage(cause, t('register.codeError')); await loadCaptcha() }
  finally { sendingCode.value = false }
}

async function submitRegister() {
  if (registerForm.password !== registerForm.confirmPassword) { error.value = t('register.passwordMismatch'); return }
  busy.value = true; error.value = ''
  try {
    await register({ username: registerForm.username.trim(), displayName: registerForm.displayName.trim(), email: registerForm.email.trim(), verificationCode: registerForm.verificationCode.trim(), password: registerForm.password })
    loginForm.username = registerForm.username.trim()
    await router.replace({ path: '/login', query: { registered: '1', username: loginForm.username } })
  } catch (cause) { error.value = apiErrorMessage(cause, t('register.error')) }
  finally { busy.value = false }
}

async function switchAccount() {
  try { await auth.logout() } catch { error.value = t('audit.sessionLogoutFailed') }
  finally { loginForm.password = ''; registerForm.password = ''; registerForm.verificationCode = '' }
}
onUnmounted(() => window.clearInterval(timer))
</script>

<template>
  <section class="login-page auth-page">
    <div class="login-art"><span class="login-seal">炎</span><p>{{ t('common.tagline') }}</p><h1>{{ t('common.appName') }}</h1></div>
    <div class="login-panel auth-card">
      <nav class="auth-mode-tabs" :aria-label="t('login.actions')">
        <RouterLink to="/login" :class="{ active: mode === 'login' }">{{ t('login.submit') }}</RouterLink>
        <RouterLink to="/register" :class="{ active: mode === 'register' }">{{ t('register.submit') }}</RouterLink>
      </nav>

      <div v-if="mode === 'login'">
        <div class="login-heading"><small>{{ t('login.welcome') }}</small><h2>{{ t('login.title') }}</h2></div>
        <div v-if="auth.currentUser.value" class="remembered-account">
          <p>{{ auth.currentUser.value.displayName }} · @{{ auth.currentUser.value.username }}</p>
          <RouterLink class="login-submit" :to="isAdminRole(auth.currentUser.value.role) ? '/admin' : safeRedirect()">继续进入</RouterLink>
          <button type="button" @click="switchAccount">切换账号</button>
        </div>
        <form v-else class="login-form" @submit.prevent="submitLogin">
          <div class="login-role-tabs"><button type="button" :class="{ active: loginForm.role === 'USER' }" @click="loginForm.role='USER'">{{ t('login.user') }}</button><button type="button" :class="{ active: loginForm.role === 'ADMIN' }" @click="loginForm.role='ADMIN'">{{ t('login.admin') }}</button></div>
          <label>{{ t('login.username') }}<input v-model.trim="loginForm.username" autocomplete="username" required autofocus></label>
          <label>{{ t('login.password') }}<input v-model="loginForm.password" type="password" autocomplete="current-password" required></label>
          <p v-if="route.query.registered === '1'" class="form-success">{{ t('login.registrationSuccess') }}</p>
          <p v-if="resetSucceeded" class="form-success" role="status">{{ t('login.resetSuccess') }}</p>
          <p v-if="error" class="form-error" role="alert">{{ error }}</p>
          <button class="login-submit" :disabled="busy">{{ busy ? t('login.loggingIn') : t('login.submit') }}</button>
        </form>
        <PasswordResetForm v-if="!auth.currentUser.value" @reset="handlePasswordReset" />
      </div>

      <form v-else class="login-form register-compact-form" @submit.prevent="submitRegister">
        <div class="login-heading"><small>{{ t('register.welcome') }}</small><h2>{{ t('register.title') }}</h2></div>
        <div class="auth-field-grid">
          <label>{{ t('register.username') }}<input v-model.trim="registerForm.username" minlength="3" maxlength="50" pattern="[A-Za-z0-9_]+" autocomplete="username" required></label>
          <label>{{ t('register.displayName') }}<input v-model.trim="registerForm.displayName" minlength="2" maxlength="50" required></label>
        </div>
        <label>{{ t('register.email') }}<input v-model.trim="registerForm.email" type="email" maxlength="254" autocomplete="email" required></label>
        <div class="captcha-block"><span>{{ captcha?.question || t('register.captchaLoading') }}</span><input v-model.trim="captchaAnswer" inputmode="numeric" :disabled="!captcha"><button type="button" :disabled="!captcha || sendingCode || codeCooldown > 0" @click="sendCode">{{ codeCooldown > 0 ? t('register.resendCode', { seconds: codeCooldown }) : t('register.sendCode') }}</button></div>
        <label>{{ t('register.verificationCode') }}<input v-model.trim="registerForm.verificationCode" inputmode="numeric" maxlength="6" required></label>
        <div class="auth-field-grid">
          <label>{{ t('register.password') }}<input v-model="registerForm.password" type="password" minlength="8" maxlength="16" pattern="^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]+$" autocomplete="new-password" required></label>
          <label>{{ t('register.confirmPassword') }}<input v-model="registerForm.confirmPassword" type="password" minlength="8" maxlength="16" autocomplete="new-password" required></label>
        </div>
        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
        <button class="login-submit" :disabled="busy">{{ busy ? t('register.registering') : t('register.submit') }}</button>
      </form>
    </div>
  </section>
</template>
