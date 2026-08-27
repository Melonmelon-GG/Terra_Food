<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import {
  deleteFood,
  deleteUser,
  getAdminFoods,
  getRegions,
  getUsers,
  importFoodSpreadsheet,
  reviewUserSignature,
  setUserActive,
  setUserRole,
  reviewFood,
} from '../api'
import { useAuth } from '../auth'
import type { AuthUser, Food, FoodImportResult, FoodReviewStatus, Region, SignatureStatus } from '../types'

type AdminTab = 'foods' | 'reviews' | 'users'
type PageSize = 10 | 20 | 50

const PAGE_SIZE_OPTIONS = [10, 20, 50] as const

const { t } = useI18n()
const auth = useAuth()
const activeTab = ref<AdminTab>('foods')
const foods = ref<Food[]>([])
const regions = ref<Region[]>([])
const users = ref<AuthUser[]>([])
const foodsTotal = ref(0)
const pendingFoodTotal = ref(0)
const totalHeat = ref(0)
const usersTotal = ref(0)
const loading = ref(true)
const error = ref('')
const usersError = ref('')
const fileInput = ref<HTMLInputElement | null>(null)
const importing = ref(false)
const importError = ref('')
const importResult = ref<FoodImportResult | null>(null)

const foodsPage = ref(1)
const foodsPageSize = ref<PageSize>(10)
const usersPage = ref(1)
const usersPageSize = ref<PageSize>(10)
const usersLoading = ref(false)

const foodsPageTotal = computed(() => {
  return Math.max(1, Math.ceil(foodsTotal.value / foodsPageSize.value))
})

const viewingFood = ref<Food | null>(null)

const usersPageTotal = computed(() => Math.max(1, Math.ceil(usersTotal.value / usersPageSize.value)))

const visibleFoods = computed(() => foods.value)
const canPrevFoods = computed(() => foodsPage.value > 1)
const canNextFoods = computed(() => foodsPage.value < foodsPageTotal.value)
const canPrevUsers = computed(() => usersPage.value > 1)
const canNextUsers = computed(() => usersPage.value < usersPageTotal.value)
const usersPageStart = computed(() => users.value.length ? (usersPage.value - 1) * usersPageSize.value + 1 : 0)
const usersPageEnd = computed(() => Math.min(usersPage.value * usersPageSize.value, usersTotal.value))

const canManageRoles = computed(() => auth.currentUser.value?.role === 'ADMIN')

const foodsPageStart = computed(() => foods.value.length ? (foodsPage.value - 1) * foodsPageSize.value + 1 : 0)
const foodsPageEnd = computed(() => Math.min(foodsPage.value * foodsPageSize.value, foodsTotal.value))

function switchTab(tab: AdminTab) {
  activeTab.value = tab
  if (tab === 'users' && !users.value.length && !usersLoading.value && !loading.value) {
    void loadUsersPage(1, usersPageSize.value)
  } else if (tab !== 'users') {
    void loadFoodsPage(1, foodsPageSize.value)
  }
}

function formatDateTime(value: string) {
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    return '-'
  }

  return parsed.toLocaleString()
}

function roleLabel(role: AuthUser['role']) {
  if (role === 'ADMIN') return t('admin.adminRole')
  if (role === 'SUB_ADMIN') return t('admin.subAdminRole')
  return t('admin.userRole')
}

function clampPageSize(size: number): PageSize {
  return size === 20 || size === 50 ? size : 10
}

async function loadFoodsAndMeta() {
  loading.value = true
  error.value = ''

  try {
    const [loadedFoods, loadedRegions, loadedUsers] = await Promise.all([
      getAdminFoods(foodsPage.value, foodsPageSize.value),
      getRegions(),
      getUsers(usersPage.value, usersPageSize.value),
    ])

    applyFoodsPage(loadedFoods)
    regions.value = loadedRegions
    users.value = loadedUsers.items
    usersTotal.value = loadedUsers.total
    usersPage.value = loadedUsers.page
    usersPageSize.value = clampPageSize(loadedUsers.pageSize)
  } catch {
    error.value = t('admin.loadError')
  } finally {
    loading.value = false
  }
}

function applyFoodsPage(response: Awaited<ReturnType<typeof getAdminFoods>>) {
  foods.value = response.items
  foodsTotal.value = response.total
  foodsPage.value = response.page
  foodsPageSize.value = clampPageSize(response.pageSize)
  totalHeat.value = response.totalHeat
  pendingFoodTotal.value = response.pendingTotal
}

async function loadFoodsPage(page = foodsPage.value, pageSize = foodsPageSize.value) {
  loading.value = true
  error.value = ''
  try {
    const status = activeTab.value === 'reviews' ? 'PENDING' : undefined
    applyFoodsPage(await getAdminFoods(page, pageSize, status))
  } catch {
    error.value = t('admin.loadError')
  } finally {
    loading.value = false
  }
}

async function reviewSubmission(food: Food, status: Extract<FoodReviewStatus, 'APPROVED' | 'REJECTED'>) {
  const action = status === 'APPROVED' ? t('admin.approve') : t('admin.reject')
  if (!window.confirm(t('admin.reviewConfirm', { action, name: food.name }))) {
    return
  }

  try {
    await reviewFood(food.id, { status })
    await loadFoodsPage(foodsPage.value, foodsPageSize.value)
  } catch {
    error.value = t('admin.reviewError')
  }
}

function reviewStatusLabel(status: FoodReviewStatus) {
  if (status === 'PENDING') return t('admin.pending')
  if (status === 'APPROVED') return t('admin.approved')
  return t('admin.rejected')
}

async function loadUsersPage(page = usersPage.value, pageSize = usersPageSize.value) {
  usersLoading.value = true
  usersError.value = ''

  try {
    const response = await getUsers(page, pageSize)
    users.value = response.items
    usersTotal.value = response.total
    usersPage.value = response.page
    usersPageSize.value = clampPageSize(response.pageSize)
  } catch {
    usersError.value = t('admin.userLoadError')
  } finally {
    usersLoading.value = false
  }
}

async function removeFood(food: Food) {
  if (!window.confirm(t('admin.deleteConfirm', { name: food.name }))) {
    return
  }

  try {
    await deleteFood(food.id)
    await loadFoodsPage(foodsPage.value, foodsPageSize.value)
  } catch {
    error.value = t('admin.deleteError')
  }
}

async function toggleUserActive(user: AuthUser) {
  const targetState = !user.active
  const action = targetState ? t('admin.enable') : t('admin.disable')

  if (!window.confirm(t('admin.toggleUserConfirm', { action, name: user.displayName || user.username }))) {
    return
  }

  usersLoading.value = true
  usersError.value = ''

  try {
    await setUserActive(user.id, { active: targetState })
    await loadUsersPage(usersPage.value, usersPageSize.value)
  } catch {
    usersError.value = t('admin.userActionError')
  } finally {
    usersLoading.value = false
  }
}

async function toggleSubAdmin(user: AuthUser) {
  const role = user.role === 'SUB_ADMIN' ? 'USER' : 'SUB_ADMIN'
  const action = role === 'SUB_ADMIN' ? t('admin.promoteSubAdmin') : t('admin.revokeSubAdmin')
  if (!window.confirm(t('admin.roleChangeConfirm', { action, name: user.displayName || user.username }))) {
    return
  }

  usersLoading.value = true
  usersError.value = ''
  try {
    await setUserRole(user.id, { role })
    await loadUsersPage(usersPage.value, usersPageSize.value)
  } catch {
    usersError.value = t('admin.roleActionError')
  } finally {
    usersLoading.value = false
  }
}

async function reviewSignatureSubmission(user: AuthUser, status: Extract<SignatureStatus, 'APPROVED' | 'REJECTED'>) {
  const action = status === 'APPROVED' ? t('admin.approve') : t('admin.reject')
  if (!window.confirm(t('admin.signatureReviewConfirm', { action, name: user.displayName || user.username }))) {
    return
  }

  usersLoading.value = true
  usersError.value = ''
  try {
    await reviewUserSignature(user.id, status)
    await loadUsersPage(usersPage.value, usersPageSize.value)
  } catch {
    usersError.value = t('admin.signatureReviewError')
  } finally {
    usersLoading.value = false
  }
}

async function removeUser(user: AuthUser) {
  if (!window.confirm(t('admin.deleteUserConfirm', { name: user.displayName || user.username }))) {
    return
  }

  usersLoading.value = true
  usersError.value = ''

  try {
    await deleteUser(user.id)

    const nextUsersTotal = usersTotal.value - 1
    const maxPageAfterDelete = Math.max(1, Math.ceil(nextUsersTotal / usersPageSize.value))
    const page = Math.min(usersPage.value, maxPageAfterDelete)
    await loadUsersPage(page, usersPageSize.value)
  } catch {
    usersError.value = t('admin.userActionError')
  } finally {
    usersLoading.value = false
  }
}

function chooseSpreadsheet() {
  fileInput.value?.click()
}

async function handleSpreadsheet(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) {
    return
  }

  importing.value = true
  importError.value = ''
  importResult.value = null
  try {
    importResult.value = await importFoodSpreadsheet(file)
    await loadFoodsAndMeta()
  } catch {
    importError.value = t('admin.importError')
  } finally {
    importing.value = false
    input.value = ''
  }
}

async function foodsPageSizeChanged() {
  foodsPage.value = 1
  await loadFoodsPage(1, foodsPageSize.value)
}

async function usersPageSizeChanged() {
  usersPage.value = 1
  await loadUsersPage(usersPage.value, usersPageSize.value)
}

async function previousFoodsPage() {
  if (canPrevFoods.value) {
    await loadFoodsPage(foodsPage.value - 1, foodsPageSize.value)
  }
}

async function nextFoodsPage() {
  if (canNextFoods.value) {
    await loadFoodsPage(foodsPage.value + 1, foodsPageSize.value)
  }
}

async function previousUsersPage() {
  if (!canPrevUsers.value) {
    return
  }

  await loadUsersPage(usersPage.value - 1, usersPageSize.value)
}

async function nextUsersPage() {
  if (!canNextUsers.value) {
    return
  }

  await loadUsersPage(usersPage.value + 1, usersPageSize.value)
}

onMounted(loadFoodsAndMeta)
</script>

<template>
  <section class="admin-page">
    <div class="admin-heading">
      <div>
        <small>{{ t('admin.eyebrow') }}</small>
        <h1>{{ t('admin.title') }}</h1>
        <p>{{ t('admin.description') }}</p>
      </div>
      <RouterLink to="/" class="outline-action">{{ t('admin.backToSite') }}</RouterLink>
    </div>

    <div class="admin-stats">
      <article><span>{{ t('admin.foodCount') }}</span><strong>{{ foodsTotal }}</strong></article>
      <article><span>{{ t('admin.regionCount') }}</span><strong>{{ regions.length }}</strong></article>
      <article><span>{{ t('admin.userCount') }}</span><strong>{{ usersTotal }}</strong></article>
      <article><span>{{ t('admin.totalHeat') }}</span><strong>{{ totalHeat }}</strong></article>
    </div>

    <div class="admin-tabs">
      <button class="admin-tab" :class="{ active: activeTab === 'foods' }" type="button" @click="switchTab('foods')">
        {{ t('admin.tabFoods') }}
      </button>
      <button class="admin-tab" :class="{ active: activeTab === 'reviews' }" type="button" @click="switchTab('reviews')">
        {{ t('admin.tabReviews') }}（{{ pendingFoodTotal }}）
      </button>
      <button class="admin-tab" :class="{ active: activeTab === 'users' }" type="button" @click="switchTab('users')">
        {{ t('admin.tabUsers') }}
      </button>
    </div>

    <section class="admin-table-card">
      <template v-if="activeTab !== 'users'">
        <div class="admin-table-title">
          <div>
            <h2>{{ activeTab === 'reviews' ? t('admin.reviewManagement') : t('admin.foodManagement') }}</h2>
            <span>{{ t('admin.recordCount', { count: foodsTotal }) }}</span>
          </div>
          <div v-if="activeTab === 'foods'" class="admin-import-actions">
            <span>{{ t('admin.importHint') }}</span>
            <input
              ref="fileInput"
              class="visually-hidden"
              type="file"
              accept=".xlsx,.xls"
              @change="handleSpreadsheet"
            />
            <button class="import-button" type="button" :disabled="importing" @click="chooseSpreadsheet">
              {{ importing ? t('admin.importing') : t('admin.importSpreadsheet') }}
            </button>
          </div>
        </div>

        <div v-if="importResult" class="import-result" role="status">
          <strong>{{ t('admin.importSuccess') }}</strong>
          <span>{{ t('admin.imported', { count: importResult.importedCount }) }}</span>
          <span>{{ t('admin.skipped', { count: importResult.skippedCount }) }}</span>
          <span>{{ t('admin.duplicates', { count: importResult.duplicateCount }) }}</span>
          <span>{{ t('admin.anonymous', { count: importResult.anonymousCount }) }}</span>
          <details v-if="importResult.issues.length">
            <summary>{{ t('admin.issueCount', { count: importResult.issues.length }) }}</summary>
            <ul>
              <li v-for="issue in importResult.issues" :key="`${issue.rowNumber}-${issue.reason}`">
                {{ t('admin.rowIssue', { row: issue.rowNumber, reason: issue.reason }) }}
              </li>
            </ul>
          </details>
        </div>

        <p v-if="importError" class="state error">{{ importError }}</p>

        <p v-if="loading" class="state">{{ t('admin.loading') }}</p>
        <p v-else-if="error" class="state error">{{ error }}</p>
        <template v-else>
          <p v-if="!visibleFoods.length" class="state">{{ t('admin.noFoods') }}</p>
          <div v-else class="admin-table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ t('admin.dish') }}</th>
                  <th>{{ t('admin.region') }}</th>
                  <th>{{ t('admin.creator') }}</th>
                  <th>{{ t('admin.heat') }}</th>
                  <th>{{ t('admin.reviewStatus') }}</th>
                  <th>{{ t('admin.createdAt') }}</th>
                  <th>{{ t('admin.actions') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="food in visibleFoods" :key="food.id">
                  <td><strong>{{ food.name }}</strong><small>{{ food.summary }}</small></td>
                  <td>{{ food.region.province }} · {{ food.region.name }}</td>
                  <td>{{ food.createdBy || t('admin.anonymousName') }}</td>
                  <td>{{ food.heat }}</td>
                  <td><span class="admin-user-status" :class="{ inactive: food.reviewStatus !== 'APPROVED' }">{{ reviewStatusLabel(food.reviewStatus) }}</span></td>
                  <td>{{ new Date(food.createdAt).toLocaleDateString() }}</td>
                  <td>
                    <div class="admin-user-actions">
                      <button class="admin-user-action" type="button" @click="viewingFood = food">{{ t('admin.view') }}</button>
                      <template v-if="food.reviewStatus === 'PENDING'">
                        <button class="admin-user-action" type="button" @click="reviewSubmission(food, 'APPROVED')">{{ t('admin.approve') }}</button>
                        <button class="danger-button" type="button" @click="reviewSubmission(food, 'REJECTED')">{{ t('admin.reject') }}</button>
                      </template>
                      <button class="danger-button" @click="removeFood(food)">{{ t('admin.delete') }}</button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="admin-pagination">
            <label class="admin-page-size">
              <span>{{ t('admin.pageSize') }}</span>
              <select v-model.number="foodsPageSize" @change="foodsPageSizeChanged">
                <option v-for="size in PAGE_SIZE_OPTIONS" :key="size" :value="size">{{ size }}</option>
              </select>
            </label>

            <p>
              {{ t('admin.pageInfo', { start: foodsPageStart, end: foodsPageEnd, total: foodsTotal, page: foodsPage, totalPages: foodsPageTotal }) }}
            </p>

            <div class="admin-page-controls">
              <button type="button" class="admin-page-button" :disabled="!canPrevFoods" @click="previousFoodsPage">{{ t('admin.prevPage') }}</button>
              <span>{{ t('admin.pageSummary', { page: foodsPage, totalPages: foodsPageTotal }) }}</span>
              <button type="button" class="admin-page-button" :disabled="!canNextFoods" @click="nextFoodsPage">{{ t('admin.nextPage') }}</button>
            </div>
          </div>
        </template>
      </template>

      <template v-else>
        <div class="admin-table-title">
          <div>
            <h2>{{ t('admin.userManagement') }}</h2>
            <span>{{ t('admin.recordCount', { count: usersTotal }) }}</span>
          </div>
        </div>

        <p v-if="loading || usersLoading" class="state">{{ t('admin.loading') }}</p>
        <p v-else-if="error" class="state error">{{ error }}</p>
        <p v-else-if="usersError" class="state error">{{ usersError }}</p>
        <template v-else>
          <p v-if="!users.length" class="state">{{ t('admin.noUsers') }}</p>
          <div v-else class="admin-table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ t('admin.userId') }}</th>
                  <th>{{ t('admin.username') }}</th>
                  <th>{{ t('admin.displayName') }}</th>
                  <th>{{ t('admin.email') }}</th>
                  <th>{{ t('admin.signature') }}</th>
                  <th>{{ t('admin.role') }}</th>
                  <th>{{ t('admin.status') }}</th>
                  <th>{{ t('admin.createdAt') }}</th>
                  <th>{{ t('admin.actions') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="user in users" :key="user.id">
                  <td>{{ user.id }}</td>
                  <td>{{ user.username }}</td>
                  <td>{{ user.displayName }}</td>
                  <td>{{ user.email || '—' }}</td>
                  <td>
                    <span v-if="user.signaturePending" class="admin-signature pending">{{ user.signaturePending }}</span>
                    <span v-else-if="user.signature">{{ user.signature }}</span>
                    <span v-else>—</span>
                  </td>
                  <td>{{ roleLabel(user.role) }}</td>
                  <td>
                    <span class="admin-user-status" :class="{ inactive: !user.active }">
                      {{ user.active ? t('admin.active') : t('admin.inactive') }}
                    </span>
                  </td>
                  <td>{{ formatDateTime(user.createdAt) }}</td>
                  <td>
                    <div class="admin-user-actions">
                      <template v-if="user.signatureStatus === 'PENDING'">
                        <button
                          class="admin-user-action"
                          type="button"
                          @click="reviewSignatureSubmission(user, 'APPROVED')"
                        >{{ t('admin.approve') }}</button>
                        <button
                          class="danger-button"
                          type="button"
                          @click="reviewSignatureSubmission(user, 'REJECTED')"
                        >{{ t('admin.reject') }}</button>
                      </template>
                      <button
                        v-if="canManageRoles && user.role !== 'ADMIN'"
                        class="admin-user-action"
                        type="button"
                        @click="toggleSubAdmin(user)"
                      >
                        {{ user.role === 'SUB_ADMIN' ? t('admin.revokeSubAdmin') : t('admin.promoteSubAdmin') }}
                      </button>
                      <button class="admin-user-action" type="button" @click="toggleUserActive(user)">
                        {{ user.active ? t('admin.disable') : t('admin.enable') }}
                      </button>
                      <button class="danger-button" type="button" @click="removeUser(user)">{{ t('admin.delete') }}</button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="admin-pagination">
            <label class="admin-page-size">
              <span>{{ t('admin.pageSize') }}</span>
              <select v-model.number="usersPageSize" @change="usersPageSizeChanged">
                <option v-for="size in PAGE_SIZE_OPTIONS" :key="size" :value="size">{{ size }}</option>
              </select>
            </label>

            <p>
              {{ t('admin.pageInfo', { start: usersPageStart, end: usersPageEnd, total: usersTotal, page: usersPage, totalPages: usersPageTotal }) }}
            </p>

            <div class="admin-page-controls">
              <button type="button" class="admin-page-button" :disabled="!canPrevUsers" @click="previousUsersPage">{{ t('admin.prevPage') }}</button>
              <span>{{ t('admin.pageSummary', { page: usersPage, totalPages: usersPageTotal }) }}</span>
              <button type="button" class="admin-page-button" :disabled="!canNextUsers" @click="nextUsersPage">{{ t('admin.nextPage') }}</button>
            </div>
          </div>
        </template>
      </template>
    </section>

    <div v-if="viewingFood" class="modal-mask" @click.self="viewingFood = null">
      <section class="admin-detail-modal">
        <div class="modal-title">
          <div>
            <small>{{ t('admin.reviewManagement') }}</small>
            <h2>{{ viewingFood.name }}</h2>
          </div>
          <button class="icon-button" @click="viewingFood = null">×</button>
        </div>

        <div
          v-if="viewingFood.imageUrl"
          class="admin-detail-photo"
          :style="{ backgroundImage: `url(${viewingFood.imageUrl})` }"
        ></div>

        <dl class="admin-detail-grid">
          <div>
            <dt>{{ t('admin.region') }}</dt>
            <dd>{{ viewingFood.region.province }} · {{ viewingFood.region.name }}</dd>
          </div>
          <div>
            <dt>{{ t('admin.creator') }}</dt>
            <dd>{{ viewingFood.creator.displayName || viewingFood.createdBy || t('admin.anonymousName') }}</dd>
          </div>
          <div>
            <dt>{{ t('admin.coordinates') }}</dt>
            <dd>{{ viewingFood.latitude }}, {{ viewingFood.longitude }}</dd>
          </div>
          <div>
            <dt>{{ t('upload.address') }}</dt>
            <dd>{{ viewingFood.address || '—' }}</dd>
          </div>
          <div>
            <dt>{{ t('upload.summary') }}</dt>
            <dd>{{ viewingFood.summary }}</dd>
          </div>
          <div>
            <dt>{{ t('upload.ingredients') }}</dt>
            <dd>{{ viewingFood.ingredients }}</dd>
          </div>
          <div>
            <dt>{{ t('upload.story') }}</dt>
            <dd>{{ viewingFood.story }}</dd>
          </div>
          <div>
            <dt>{{ t('upload.remark') }}</dt>
            <dd>{{ viewingFood.remark || '—' }}</dd>
          </div>
          <div>
            <dt>{{ t('admin.heat') }}</dt>
            <dd>{{ viewingFood.heat }}</dd>
          </div>
          <div>
            <dt>{{ t('admin.reviewStatus') }}</dt>
            <dd>{{ reviewStatusLabel(viewingFood.reviewStatus) }}</dd>
          </div>
          <div>
            <dt>{{ t('admin.createdAt') }}</dt>
            <dd>{{ formatDateTime(viewingFood.createdAt) }}</dd>
          </div>
          <div v-if="viewingFood.reviewedAt">
            <dt>{{ t('admin.reviewedAt') }}</dt>
            <dd>{{ viewingFood.reviewedBy || '—' }} · {{ formatDateTime(viewingFood.reviewedAt) }}</dd>
          </div>
        </dl>

        <div v-if="viewingFood.reviewStatus === 'PENDING'" class="modal-actions">
          <button
            class="admin-user-action"
            type="button"
            @click="reviewSubmission(viewingFood, 'APPROVED')"
          >{{ t('admin.approve') }}</button>
          <button class="danger-button" type="button" @click="reviewSubmission(viewingFood, 'REJECTED')">
            {{ t('admin.reject') }}
          </button>
        </div>
      </section>
    </div>
  </section>
</template>
