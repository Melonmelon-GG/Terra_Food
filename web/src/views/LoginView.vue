<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import { isAdminRole, useAuth } from '../auth'
import PasswordResetForm from '../components/PasswordResetForm.vue'
import LoginLoadingScene from '../components/LoginLoadingScene.vue'
import type { LoginPayload, LoginRole, UserRole } from '../types'

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
const preloadProgress = ref(0)
const error = ref('')
const registrationSucceeded = route.query.registered === '1'
const resetSucceeded = ref(false)
const accessOpen = ref(registrationSucceeded || route.query.role === 'ADMIN')
const rememberedUser = computed(() => auth.currentUser.value)
const MIN_LOADING_DURATION_MS = 5_000
const READY_HOLD_DURATION_MS = 700
let preloadStartedAt = 0
let progressTimer: ReturnType<typeof setInterval> | undefined
let sceneWaitTimer: ReturnType<typeof setTimeout> | undefined
let resolveSceneWait: ((active: boolean) => void) | undefined
let disposed = false

function startPreloadProgress() {
  stopPreloadProgress()
  preloadStartedAt = performance.now()
  preloadProgress.value = 0
  progressTimer = setInterval(() => {
    // 展示进度不是后端百分比；请求未完成时最多到 94%，避免提前宣告就绪。
    const elapsed = performance.now() - preloadStartedAt
    preloadProgress.value = Math.min(94, (elapsed / MIN_LOADING_DURATION_MS) * 94)
  }, 50)
}

function stopPreloadProgress() {
  if (progressTimer) clearInterval(progressTimer)
  progressTimer = undefined
}

function waitForScene(milliseconds: number): Promise<boolean> {
  return new Promise((resolve) => {
    resolveSceneWait = resolve
    sceneWaitTimer = setTimeout(() => {
      sceneWaitTimer = undefined
      resolveSceneWait = undefined
      resolve(true)
    }, milliseconds)
  })
}

onBeforeUnmount(() => {
  disposed = true
  stopPreloadProgress()
  if (sceneWaitTimer) clearTimeout(sceneWaitTimer)
  resolveSceneWait?.(false)
})

async function enterFor(user: { role: UserRole }) {
  if (disposed) return
  // 数据加载与最短展示时间并行；慢请求不额外等待完整的五秒。
  const remaining = MIN_LOADING_DURATION_MS - (performance.now() - preloadStartedAt)
  if (remaining > 0 && !(await waitForScene(remaining))) return
  stopPreloadProgress()
  preloadProgress.value = 100
  if (!(await waitForScene(READY_HOLD_DURATION_MS))) return
  const requestedRedirect = typeof route.query.redirect === 'string' ? route.query.redirect : undefined
  await router.replace(isAdminRole(user.role) ? '/admin' : requestedRedirect || '/')
}

async function awaken() {
  if (loading.value) return
  const user = rememberedUser.value
  if (!user) {
    accessOpen.value = true
    return
  }

  loading.value = true
  startPreloadProgress()
  await nextTick()
  try {
    await enterFor(user)
  } finally {
    stopPreloadProgress()
    loading.value = false
  }
}

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
  if (loading.value) return
  loading.value = true
  error.value = ''
  startPreloadProgress()
  // 先让全屏初始化场景完成首帧渲染，再发起登录与 Redis 预热请求。
  await nextTick()

  try {
    // Login passwords only contain letters and numbers. Normalize full-width input
    // and trim invisible whitespace introduced by copy and paste.
    const user = await auth.login({
      ...form,
      username: form.username.trim(),
      password: form.password.normalize('NFKC').trim(),
    })
    await enterFor(user)
  } catch {
    error.value = t('login.error')
  } finally {
    stopPreloadProgress()
    loading.value = false
  }
}
</script>

<template>
  <section class="login-launch-page" :class="{ 'is-loading': loading }" :aria-busy="loading">
    <!-- 保持同一个球体实例，从顶部待机位置连续移动到加载位置。 -->
    <LoginLoadingScene :active="loading" :progress="preloadProgress" />

    <div class="login-launch-brand" :inert="loading">
      <span class="login-launch-seal">炎</span>
      <p>{{ t('login.gatewayEyebrow') }}</p>
      <h1>{{ t('common.appName') }}</h1>
      <blockquote>{{ t('login.quote') }}</blockquote>

      <button class="login-awaken-button" type="button" @click="awaken">
        <span>{{ t('login.awaken') }}</span>
      </button>

      <div class="login-launch-account">
        <b>{{ t('login.accountLabel') }}</b>
        <span>{{ rememberedUser?.displayName || t('login.accountUnbound') }}</span>
      </div>
    </div>

    <footer class="login-launch-footer" :inert="loading">
      <div class="login-launch-signature">
        <span>炎</span>
        <div>
          <b>{{ t('common.appName') }}</b>
          <small>{{ t('common.tagline') }}</small>
        </div>
      </div>
      <nav :aria-label="t('login.actions')">
        <RouterLink to="/register">{{ t('login.createAccount') }}</RouterLink>
        <RouterLink to="/">{{ t('login.guestBrowse') }}</RouterLink>
      </nav>
      <span class="login-launch-version">DAYAN TERMINAL · #1</span>
    </footer>

    <Transition name="login-access">
      <div v-if="accessOpen && !loading" class="login-access-backdrop" @click.self="accessOpen = false">
        <div class="login-access-panel">
          <button
            class="login-access-close"
            type="button"
            :aria-label="t('login.closeAccess')"
            @click="accessOpen = false"
          >×</button>

          <div class="login-heading">
            <span class="login-access-seal">炎</span>
            <small>{{ t('login.welcome') }}</small>
            <h2>{{ t('login.title') }}</h2>
            <p>{{ t('login.description') }}</p>
          </div>

          <div class="role-tabs">
            <button type="button" :class="{ active: form.role === 'USER' }" @click="selectRole('USER')">
              <b>{{ t('login.user') }}</b>
              <span>{{ t('login.userHint') }}</span>
            </button>
            <button type="button" :class="{ active: form.role === 'ADMIN' }" @click="selectRole('ADMIN')">
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
              <input v-model.trim="form.username" autocomplete="username" required autofocus>
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
    </Transition>
  </section>
</template>
