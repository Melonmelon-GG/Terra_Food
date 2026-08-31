<script setup lang="ts">
import axios from 'axios'
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { deleteEtching, getAchievements, getMyEtchings, getMyFoods, getMyFootprints, getRegions, selectAchievement, selectEtching, updateAvatar, updateMyDisplayName, updateMySignature, uploadImage } from '../api'
import { useAuth } from '../auth'
import FoodEditModal from '../components/FoodEditModal.vue'
import EtchingStudio from '../components/EtchingStudio.vue'
import HexEtching from '../components/HexEtching.vue'
import type { Achievement, EtchingDesign, Food, FoodFootprint, FoodReviewStatus, Region, SignatureStatus } from '../types'

const { locale, t } = useI18n()
const auth = useAuth()
const foods = ref<Food[]>([])
const footprints = ref<FoodFootprint[]>([])
const regions = ref<Region[]>([])
const achievements = ref<Achievement[]>([])
const etchings = ref<EtchingDesign[]>([])
const studioOpen = ref(false)
const editingEtching = ref<EtchingDesign>()
const selectedFood = ref<Food>()
const loading = ref(true)
const error = ref('')
const avatarInput = ref<HTMLInputElement>()
const avatarSaving = ref(false)
const avatarError = ref('')
const achievementSaving = ref<number>()
const achievementError = ref('')
const signatureEditing = ref(false)
const signatureDraft = ref('')
const signatureSaving = ref(false)
const signatureError = ref('')
const displayNameEditing = ref(false)
const displayNameDraft = ref('')
const displayNameSaving = ref(false)
const displayNameError = ref('')

const user = computed(() => auth.currentUser.value)
const pendingSignature = computed(() => user.value?.pendingReviews?.find((item) => item.field === 'SIGNATURE'))
const pendingDisplayName = computed(() => user.value?.pendingReviews?.find((item) => item.field === 'DISPLAY_NAME'))
const selectedAchievement = computed(() => achievements.value.find((achievement) => achievement.selected))
const selectedEtching = computed(() => etchings.value.find((etching) => etching.selected))
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

function signatureStatusText(status: SignatureStatus | undefined) {
  if (status === 'PENDING') return t('profile.signaturePending')
  if (status === 'REJECTED') return t('profile.signatureRejected')
  return ''
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

function startSignatureEdit() {
  signatureDraft.value = pendingSignature.value?.pendingValue
    || user.value?.signature
    || ''
  signatureError.value = ''
  signatureEditing.value = true
}

async function saveSignature() {
  const draft = signatureDraft.value.trim()
  if (!draft) {
    signatureError.value = t('profile.signatureRequired')
    return
  }

  signatureSaving.value = true
  signatureError.value = ''
  try {
    const updatedUser = await updateMySignature(draft)
    auth.setCurrentUser(updatedUser)
    signatureEditing.value = false
  } catch (requestError) {
    signatureError.value = axios.isAxiosError(requestError)
      ? requestError.response?.data?.message || t('profile.signatureError')
      : t('profile.signatureError')
  } finally {
    signatureSaving.value = false
  }
}

function startDisplayNameEdit() {
  displayNameDraft.value = pendingDisplayName.value?.pendingValue || user.value?.displayName || ''
  displayNameError.value = ''
  displayNameEditing.value = true
}

async function saveDisplayName() {
  const draft = displayNameDraft.value.trim()
  if (!draft) {
    displayNameError.value = t('profile.displayNameRequired')
    return
  }

  displayNameSaving.value = true
  displayNameError.value = ''
  try {
    const updatedUser = await updateMyDisplayName(draft)
    auth.setCurrentUser(updatedUser)
    displayNameEditing.value = false
  } catch (requestError) {
    displayNameError.value = axios.isAxiosError(requestError)
      ? requestError.response?.data?.message || t('profile.displayNameError')
      : t('profile.displayNameError')
  } finally {
    displayNameSaving.value = false
  }
}

async function chooseAchievement(achievementId: number) {
  if (selectedAchievement.value?.id === achievementId) return

  achievementSaving.value = achievementId
  achievementError.value = ''
  try {
    const selected = await selectAchievement(achievementId)
    achievements.value = achievements.value.map((achievement) => ({
      ...achievement,
      selected: achievement.id === selected.id,
    }))
    etchings.value = etchings.value.map((etching) => ({ ...etching, selected: false }))
  } catch {
    achievementError.value = t('profile.sealSelectionError')
  } finally {
    achievementSaving.value = undefined
  }
}

async function chooseEtching(etchingId: number) {
  achievementSaving.value = etchingId
  achievementError.value = ''
  try {
    const selected = await selectEtching(etchingId)
    etchings.value = etchings.value.map((etching) => ({ ...etching, selected: etching.id === selected.id }))
    achievements.value = achievements.value.map((achievement) => ({ ...achievement, selected: false }))
  } catch { achievementError.value = t('profile.sealSelectionError') }
  finally { achievementSaving.value = undefined }
}

function openStudio(design?: EtchingDesign) { editingEtching.value = design; studioOpen.value = true }
function handleEtchingSaved(saved: EtchingDesign) {
  const index = etchings.value.findIndex((etching) => etching.id === saved.id)
  if (index >= 0) etchings.value.splice(index, 1, saved)
  else etchings.value.unshift(saved)
  studioOpen.value = false
}
async function removeEtching(design: EtchingDesign) {
  if (!window.confirm(t('etching.deleteConfirm', { name: design.name }))) return
  await deleteEtching(design.id)
  etchings.value = etchings.value.filter((etching) => etching.id !== design.id)
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
    ;[foods.value, footprints.value, regions.value, achievements.value, etchings.value] = await Promise.all([
      getMyFoods(),
      getMyFootprints(),
      getRegions(),
      getAchievements(),
      getMyEtchings(),
    ])
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

          <template v-if="displayNameEditing">
            <div class="profile-display-name">
              <input
                v-model="displayNameDraft"
                maxlength="50"
                :placeholder="user?.username"
              >
              <button type="button" :disabled="displayNameSaving" @click="saveDisplayName">
                {{ displayNameSaving ? t('profile.savingName') : t('profile.saveName') }}
              </button>
              <button type="button" :disabled="displayNameSaving" @click="displayNameEditing = false">
                {{ t('common.cancel') }}
              </button>
            </div>
            <p v-if="displayNameError" class="signature-error">{{ displayNameError }}</p>
          </template>
          <template v-else>
            <h1>
              {{ pendingDisplayName?.pendingValue || user?.displayName }}
              <button
                v-if="pendingDisplayName"
                type="button"
                disabled
                class="signature-status"
              >{{ t('profile.signaturePending') }}</button>
            </h1>
            <button type="button" class="display-name-edit" @click="startDisplayNameEdit">
              {{ t('profile.editDisplayName') }}
            </button>
          </template>
          <p>@{{ user?.username }}<template v-if="user?.email"> · {{ user.email }}</template></p>

          <div class="profile-signature">
            <template v-if="signatureEditing">
              <textarea
                v-model="signatureDraft"
                maxlength="200"
                rows="2"
                :placeholder="t('profile.signaturePlaceholder')"
              />
              <span>{{ signatureDraft.length }}/200</span>
              <div class="profile-signature-actions">
                <button type="button" :disabled="signatureSaving" @click="saveSignature">
                  {{ t('profile.signatureSave') }}
                </button>
                <button type="button" :disabled="signatureSaving" @click="signatureEditing = false">
                  {{ t('common.cancel') }}
                </button>
              </div>
            </template>

            <template v-else>
              <p>
                {{ pendingSignature?.pendingValue || user?.signature || t('profile.signatureEmpty') }}
                <button v-if="pendingSignature" type="button" disabled class="signature-status">
                  {{ t('profile.signaturePending') }}
                </button>
              </p>
              <button type="button" class="signature-edit" @click="startSignatureEdit">
                {{ t('profile.signatureEdit') }}
              </button>
            </template>

            <p v-if="signatureError" class="signature-error">{{ signatureError }}</p>
          </div>
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

        <div v-if="selectedEtching" class="selected-etching">
          <div class="selected-etching-image"><HexEtching :layer-one="selectedEtching.layerOne" /></div>
          <strong>{{ selectedEtching.name }}</strong>
          <p>{{ t('etching.userMade') }}</p>
        </div>
        <div v-else-if="selectedAchievement" class="selected-etching">
          <div class="selected-etching-image">
            <img :src="selectedAchievement.imageUrl" :alt="selectedAchievement.name">
          </div>
          <strong>{{ selectedAchievement.name }}</strong>
          <p>{{ selectedAchievement.description }}</p>
        </div>
        <template v-else>
          <div class="etching-seal" aria-hidden="true">
            <div>
              <span>章</span>
              <small>LOCKED</small>
            </div>
          </div>
          <p>{{ achievements.length || etchings.length ? t('profile.sealChooseHint') : t('profile.sealEmpty') }}</p>
        </template>

        <button type="button" class="etching-create-button" @click="openStudio()">{{ t('etching.openStudio') }}</button>
        <div v-if="etchings.length" class="etching-picker custom-etching-picker">
          <small>{{ t('etching.myEtchings') }}</small>
          <div v-for="etching in etchings" :key="etching.id" class="custom-etching-item" :class="{ active: etching.selected }">
            <button type="button" :disabled="achievementSaving !== undefined" @click="chooseEtching(etching.id)">
              <HexEtching :layer-one="etching.layerOne" />
              <span><strong>{{ etching.name }}</strong><small>{{ etching.selected ? t('profile.sealSelected') : t('profile.sealSelect') }}</small></span>
            </button>
            <div><button type="button" @click="openStudio(etching)">{{ t('etching.edit') }}</button><button type="button" @click="removeEtching(etching)">{{ t('etching.delete') }}</button></div>
          </div>
        </div>

        <div v-if="achievements.length" class="etching-picker">
          <small>{{ t('profile.sealChoose') }}</small>
          <button
            v-for="achievement in achievements"
            :key="achievement.id"
            type="button"
            :class="{ active: achievement.selected }"
            :disabled="achievementSaving !== undefined"
            @click="chooseAchievement(achievement.id)"
          >
            <img :src="achievement.imageUrl" :alt="achievement.name">
            <span>
              <strong>{{ achievement.name }}</strong>
              <small>
                {{
                  achievement.selected
                    ? t('profile.sealSelected')
                    : achievementSaving === achievement.id
                      ? t('profile.sealSelecting')
                      : t('profile.sealSelect')
                }}
              </small>
            </span>
          </button>
        </div>
        <p v-if="achievementError" class="etching-error">{{ achievementError }}</p>
      </aside>
    </section>

    <section class="footprint-panel">
      <div class="profile-section-title">
        <div>
          <small>{{ t('profile.footprintEyebrow') }}</small>
          <h2>{{ t('profile.recentFootprints') }}</h2>
        </div>
        <p>{{ t('profile.footprintHint') }}</p>
      </div>
      <div v-if="footprints.length" class="footprint-list">
        <RouterLink
          v-for="footprint in footprints"
          :key="footprint.food.id + '-' + footprint.visitedAt"
          :to="'/foods/' + footprint.food.id"
          class="footprint-card"
        >
          <div
            class="footprint-cover"
            :style="footprint.food.imageUrl ? { backgroundImage: 'url(' + footprint.food.imageUrl + ')' } : undefined"
          >
            <span v-if="!footprint.food.imageUrl">{{ footprint.food.name.slice(0, 1) }}</span>
          </div>
          <div>
            <small>{{ footprint.food.region.province }} · {{ footprint.food.region.name }}</small>
            <strong>{{ footprint.food.name }}</strong>
            <time>{{ formatDate(footprint.visitedAt) }}</time>
          </div>
        </RouterLink>
      </div>
      <p v-else-if="!loading" class="footprint-empty">{{ t('profile.noFootprints') }}</p>
    </section>

    <FoodEditModal
      v-if="selectedFood"
      :food="selectedFood"
      :regions="regions"
      @close="selectedFood = undefined"
      @saved="handleSaved"
    />
    <EtchingStudio v-if="studioOpen" :design="editingEtching" @close="studioOpen = false" @saved="handleEtchingSaved" />
  </div>
</template>
