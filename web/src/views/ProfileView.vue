<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { getMyFoods, getRegions, updateAvatar, uploadImage } from '../api'
import { useAuth } from '../auth'
import FoodEditModal from '../components/FoodEditModal.vue'
import type { Food, FoodReviewStatus, Region } from '../types'

const { locale, t } = useI18n()
const auth = useAuth()
const foods = ref<Food[]>([])
const regions = ref<Region[]>([])
const selectedFood = ref<Food>()
const loading = ref(true)
const error = ref('')
const avatarInput = ref<HTMLInputElement>()
const avatarSaving = ref(false)
const avatarError = ref('')

const user = computed(() => auth.currentUser.value)
const avatarText = computed(() => (user.value?.displayName || user.value?.username || '食').trim().slice(0, 1).toUpperCase())
const statusCounts = computed(() => ({
  total: foods.value.length,
  pending: foods.value.filter((food) => food.reviewStatus === 'PENDING').length,
  approved: foods.value.filter((food) => food.reviewStatus === 'APPROVED').length,
  rejected: foods.value.filter((food) => food.reviewStatus === 'REJECTED').length,
}))

function statusText(status: FoodReviewStatus) {
  return t(`profile.status.${status.toLowerCase()}`)
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat(locale.value === 'zh-CN' ? 'zh-CN' : 'en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  }).format(new Date(value))
}

function handleSaved(updated: Food) {
  const index = foods.value.findIndex((food) => food.id === updated.id)
  if (index >= 0) foods.value.splice(index, 1, updated)
  selectedFood.value = undefined
}

async function changeAvatar(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return

  avatarSaving.value = true
  avatarError.value = ''
  try {
    const avatarUrl = await uploadImage(file)
    const updatedUser = await updateAvatar(avatarUrl)
    auth.setCurrentUser(updatedUser)
  } catch {
    avatarError.value = t('profile.avatarError')
  } finally {
    avatarSaving.value = false
    ;(event.target as HTMLInputElement).value = ''
  }
}

onMounted(async () => {
  try {
    ;[foods.value, regions.value] = await Promise.all([getMyFoods(), getRegions()])
  } catch {
    error.value = t('profile.loadError')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="profile-page">
    <section class="profile-hero">
      <div class="profile-identity">
        <div class="profile-avatar-control">
          <button
            class="profile-avatar"
            type="button"
            :aria-label="t('profile.changeAvatar')"
            :disabled="avatarSaving"
            @click="avatarInput?.click()"
          >
            <img v-if="user?.avatarUrl" :src="user.avatarUrl" :alt="t('profile.avatarAlt')">
            <span v-else>{{ avatarText }}</span>
            <em>{{ avatarSaving ? t('profile.avatarSaving') : t('profile.changeAvatar') }}</em>
          </button>
          <input
            ref="avatarInput"
            class="avatar-file-input"
            type="file"
            accept="image/jpeg,image/png,image/webp"
            @change="changeAvatar"
          >
          <p v-if="avatarError" class="avatar-error">{{ avatarError }}</p>
        </div>
        <div>
          <small>{{ t('profile.eyebrow') }}</small>
          <h1>{{ user?.displayName }}</h1>
          <p>@{{ user?.username }}<template v-if="user?.email"> · {{ user.email }}</template></p>
        </div>
      </div>

      <div class="profile-stats">
        <article><strong>{{ statusCounts.total }}</strong><span>{{ t('profile.total') }}</span></article>
        <article><strong>{{ statusCounts.pending }}</strong><span>{{ t('profile.status.pending') }}</span></article>
        <article><strong>{{ statusCounts.approved }}</strong><span>{{ t('profile.status.approved') }}</span></article>
        <article><strong>{{ statusCounts.rejected }}</strong><span>{{ t('profile.status.rejected') }}</span></article>
      </div>
    </section>

    <section class="profile-layout">
      <div class="profile-foods">
        <div class="profile-section-title">
          <div>
            <small>{{ t('profile.recordsEyebrow') }}</small>
            <h2>{{ t('profile.myFoods') }}</h2>
          </div>
          <p>{{ t('profile.recordsHint') }}</p>
        </div>

        <p v-if="loading" class="state">{{ t('profile.loading') }}</p>
        <p v-else-if="error" class="state">{{ error }}</p>
        <div v-else-if="foods.length" class="profile-food-list">
          <article v-for="food in foods" :key="food.id" class="profile-food-card">
            <div
              class="profile-food-cover"
              :style="food.imageUrl ? { backgroundImage: `url(${food.imageUrl})` } : undefined"
            >
              <span v-if="!food.imageUrl">{{ food.name.slice(0, 1) }}</span>
            </div>
            <div class="profile-food-body">
              <div class="profile-food-heading">
                <div>
                  <small>{{ food.region.province }} / {{ food.region.name }}</small>
                  <h3>{{ food.name }}</h3>
                </div>
                <span class="review-status" :class="`is-${food.reviewStatus.toLowerCase()}`">
                  {{ statusText(food.reviewStatus) }}
                </span>
              </div>
              <p>{{ food.summary }}</p>
              <div class="food-remark">
                <b>{{ t('profile.remark') }}</b>
                <span>{{ food.remark || t('profile.noRemark') }}</span>
              </div>
              <footer>
                <time>{{ formatDate(food.createdAt) }}</time>
                <div>
                  <RouterLink v-if="food.reviewStatus === 'APPROVED'" :to="`/foods/${food.id}`">
                    {{ t('profile.view') }}
                  </RouterLink>
                  <button type="button" @click="selectedFood = food">{{ t('profile.complete') }}</button>
                </div>
              </footer>
            </div>
          </article>
        </div>
        <div v-else class="profile-empty">
          <span>味</span>
          <h3>{{ t('profile.emptyTitle') }}</h3>
          <p>{{ t('profile.emptyDescription') }}</p>
          <RouterLink to="/">{{ t('profile.goAdd') }}</RouterLink>
        </div>
      </div>

      <aside class="etching-panel">
        <small>{{ t('profile.sealEyebrow') }}</small>
        <h2>{{ t('profile.sealTitle') }}</h2>
        <div class="etching-seal" aria-hidden="true">
          <div>
            <span>章</span>
            <small>LOCKED</small>
          </div>
        </div>
        <p>{{ t('profile.sealPlaceholder') }}</p>
      </aside>
    </section>

    <FoodEditModal
      v-if="selectedFood"
      :food="selectedFood"
      :regions="regions"
      @close="selectedFood = undefined"
      @saved="handleSaved"
    />
  </div>
</template>
