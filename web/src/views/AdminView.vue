<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import {
  deleteFood,
  deleteUser,
  getFoods,
  getRegions,
  getUsers,
  importFoodSpreadsheet,
  setUserActive,
} from '../api'
import type { AuthUser, Food, FoodImportResult, Region } from '../types'

type AdminTab = 'foods' | 'users'
type PageSize = 10 | 20 | 50

const PAGE_SIZE_OPTIONS = [10, 20, 50] as const

const { t } = useI18n()
const activeTab = ref<AdminTab>('foods')
const foods = ref<Food[]>([])
const regions = ref<Region[]>([])
const users = ref<AuthUser[]>([])
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
  if (!foods.value.length) {
    return 1
  }

  return Math.max(1, Math.ceil(foods.value.length / foodsPageSize.value))
})

const usersPageTotal = computed(() => Math.max(1, Math.ceil(usersTotal.value / usersPageSize.value)))

const visibleFoods = computed(() => {
  const start = (foodsPage.value - 1) * foodsPageSize.value
  return foods.value.slice(start, start + foodsPageSize.value)
})

const totalHeat = computed(() => foods.value.reduce((sum, food) => sum + food.heat, 0))
const canPrevFoods = computed(() => foodsPage.value > 1)
const canNextFoods = computed(() => foodsPage.value < foodsPageTotal.value)
const canPrevUsers = computed(() => usersPage.value > 1)
const canNextUsers = computed(() => usersPage.value < usersPageTotal.value)

const foodsPageStart = computed(() => foods.value.length ? (foodsPage.value - 1) * foodsPageSize.value + 1 : 0)
const foodsPageEnd = computed(() => Math.min(foodsPage.value * foodsPageSize.value, foods.value.length))

function switchTab(tab: AdminTab) {
  activeTab.value = tab
  if (tab === 'users' && !users.value.length && !usersLoading.value && !loading.value) {
    void loadUsersPage(1, usersPageSize.value)
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
  return role === 'ADMIN' ? t('admin.adminRole') : t('admin.userRole')
}

function clampPageSize(size: number): PageSize {
  return size === 20 || size === 50 ? size : 10
}

async function loadFoodsAndMeta() {
  loading.value = true
  error.value = ''

  try {
    const [loadedFoods, loadedRegions, loadedUsers] = await Promise.all([
      getFoods(),
      getRegions(),
      getUsers(usersPage.value, usersPageSize.value),
    ])

    foods.value = loadedFoods
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
    foods.value = foods.value.filter((item) => item.id !== food.id)

    const maxPage = foodsPageTotal.value
    if (foodsPage.value > maxPage) {
      foodsPage.value = maxPage
    }
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

function foodsPageSizeChanged() {
  foodsPage.value = 1
}

async function usersPageSizeChanged() {
  usersPage.value = 1
  await loadUsersPage(usersPage.value, usersPageSize.value)
}

function previousFoodsPage() {
  if (canPrevFoods.value) {
    foodsPage.value -= 1
  }
}

function nextFoodsPage() {
  if (canNextFoods.value) {
    foodsPage.value += 1
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
      <article><span>{{ t('admin.foodCount') }}</span><strong>{{ foods.length }}</strong></article>
      <article><span>{{ t('admin.regionCount') }}</span><strong>{{ regions.length }}</strong></article>
      <article><span>{{ t('admin.userCount') }}</span><strong>{{ usersTotal }}</strong></article>
      <article><span>{{ t('admin.totalHeat') }}</span><strong>{{ totalHeat }}</strong></article>
    </div>

    <div class="admin-tabs">
      <button class="admin-tab" :class="{ active: activeTab === 'foods' }" type="button" @click="switchTab('foods')">
        {{ t('admin.tabFoods') }}
      </button>
      <button class="admin-tab" :class="{ active: activeTab === 'users' }" type="button" @click="switchTab('users')">
        {{ t('admin.tabUsers') }}
      </button>
    </div>

    <section class="admin-table-card">
      <template v-if="activeTab === 'foods'">
        <div class="admin-table-title">
          <div>
            <h2>{{ t('admin.foodManagement') }}</h2>
            <span>{{ t('admin.recordCount', { count: foods.length }) }}</span>
          </div>
          <div class="admin-import-actions">
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
                  <td>{{ new Date(food.createdAt).toLocaleDateString() }}</td>
                  <td><button class="danger-button" @click="removeFood(food)">{{ t('admin.delete') }}</button></td>
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
              {{ t('admin.pageInfo', { start: foodsPageStart, end: foodsPageEnd, total: foods.length, page: foodsPage, totalPages: foodsPageTotal }) }}
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
                  <td>{{ roleLabel(user.role) }}</td>
                  <td>
                    <span class="admin-user-status" :class="{ inactive: !user.active }">
                      {{ user.active ? t('admin.active') : t('admin.inactive') }}
                    </span>
                  </td>
                  <td>{{ formatDateTime(user.createdAt) }}</td>
                  <td>
                    <div class="admin-user-actions">
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
              {{ t('admin.pageInfo', { start: (usersPage - 1) * usersPageSize + 1, end: Math.min(usersPage * usersPageSize, usersTotal), total: usersTotal, page: usersPage, totalPages: usersPageTotal }) }}
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
  </section>
</template>
