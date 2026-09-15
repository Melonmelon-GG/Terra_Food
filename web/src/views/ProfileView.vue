<script setup lang="ts">
import axios from 'axios'
import { computed, defineAsyncComponent, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'

import { addWishlistItem, deleteEtching, deleteMyCheckin, deleteWishlistItem, getAchievements, getMyEtchings, getMyFavoritesPage, getMyFoods, getMyFootprints, getMyCheckins, getMyWishlistPage, getRegions, removeFavorite, selectAchievement, selectEtching, updateAvatar, updateMyCheckin, updateMyDisplayName, updateMySignature, uploadImage } from '../api'
import { useAuth } from '../auth'
import { apiErrorMessage } from '../apiError'
const FoodEditModal = defineAsyncComponent(() => import('../components/FoodEditModal.vue'))
const EtchingStudio = defineAsyncComponent(() => import('../components/EtchingStudio.vue'))
import HexEtching from '../components/HexEtching.vue'
import LandmarkBackdrop from '../components/LandmarkBackdrop.vue'
import '../archive.css'
import type { Achievement, EtchingDesign, Food, FoodCheckin, FoodFootprint, FoodReviewStatus, Region, SignatureStatus, WishlistItem, WishlistMatchField } from '../types'

const { locale, t } = useI18n()
const route = useRoute()
const auth = useAuth()
const foods = ref<Food[]>([])
const footprints = ref<FoodFootprint[]>([])
const checkins = ref<FoodCheckin[]>([])
const checkinPage = ref(1)
const checkinTotal = ref(0)
const checkinLoadingMore = ref(false)
const checkinEditing = ref<number>()
const checkinSaving = ref<number>()
const checkinError = ref('')
const checkinDraft = ref({ eatenOn: '', note: '', visibility: 'PUBLIC' as 'PUBLIC' | 'PRIVATE', version: 0 })
const favorites = ref<Food[]>([])
const wishlist = ref<WishlistItem[]>([])
const favoritesTotal = ref(0)
const wishlistTotal = ref(0)
const favoritesPage = ref(1)
const wishlistPage = ref(1)
const collectionsLoading = ref(false)
const collectionTab = ref<'favorites' | 'wishlist'>(route.query.tab === 'wishlist' ? 'wishlist' : 'favorites')
type ArchiveTab = 'records' | 'favorites' | 'wishlist' | 'diary' | 'seals' | 'footprints'
const archiveTabs: ArchiveTab[] = ['records', 'favorites', 'wishlist', 'diary', 'seals', 'footprints']
const archiveTab = ref<ArchiveTab>(route.query.tab === 'wishlist' ? 'wishlist' : 'records')
function selectArchiveTab(tab: ArchiveTab) {
  archiveTab.value = tab
  if (tab === 'favorites' || tab === 'wishlist') collectionTab.value = tab
}
watch(() => route.query.tab, value => {
  if (archiveTabs.includes(value as ArchiveTab)) selectArchiveTab(value as ArchiveTab)
})
const wishlistDraft = ref('')
const wishlistSaving = ref(false)
const collectionRemoving = ref<string>()
const collectionError = ref('')
const regions = ref<Region[]>([])
const achievements = ref<Achievement[]>([])
const etchings = ref<EtchingDesign[]>([])
const studioOpen = ref(false)
const editingEtching = ref<EtchingDesign>()
const selectedFood = ref<Food>()
const editSubmitted = ref(false)
const loading = ref(true)
const error = ref('')
const loadFailures = ref<string[]>([])
const sealLoadFailed = computed(() => loadFailures.value.includes('achievements') || loadFailures.value.includes('etchings'))
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

function matchReason(fields: WishlistMatchField[]) {
  return fields.map((field) => t(`profile.matchField.${field.toLowerCase()}`)).join(' · ')
}

function editCheckin(item: FoodCheckin) {
  checkinEditing.value = item.id
  checkinError.value = ''
  checkinDraft.value = { eatenOn: item.eatenOn, note: item.note || '', visibility: item.visibility, version: item.version }
}

async function saveCheckin(item: FoodCheckin) {
  checkinSaving.value = item.id
  checkinError.value = ''
  try {
    const updated = await updateMyCheckin(item.id, { ...checkinDraft.value, note: checkinDraft.value.note.trim() || undefined, timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Shanghai' })
    checkins.value = checkins.value.map((candidate) => candidate.id === updated.id ? updated : candidate)
    checkinEditing.value = undefined
  } catch (cause) { checkinError.value = apiErrorMessage(cause, '打卡修改失败，请刷新后重试。') }
  finally { checkinSaving.value = undefined }
}

async function removeCheckin(item: FoodCheckin) {
  if (!window.confirm(`删除“${item.foodName}”的这次打卡？`)) return
  checkinSaving.value = item.id
  checkinError.value = ''
  try { await deleteMyCheckin(item.id, item.version); checkins.value = checkins.value.filter((candidate) => candidate.id !== item.id) }
  catch (cause) { checkinError.value = apiErrorMessage(cause, '打卡删除失败，请重试。') }
  finally { checkinSaving.value = undefined }
}

async function loadMoreCheckins() {
  if (checkinLoadingMore.value || checkins.value.length >= checkinTotal.value) return
  checkinLoadingMore.value = true
  checkinError.value = ''
  try {
    const result = await getMyCheckins(checkinPage.value + 1)
    checkinPage.value = result.page
    checkinTotal.value = result.total
    checkins.value.push(...result.items.filter((item) => !checkins.value.some((existing) => existing.id === item.id)))
  } catch (cause) { checkinError.value = apiErrorMessage(cause, '打卡加载失败，请重试。') }
  finally { checkinLoadingMore.value = false }
}

async function createWishlistItem() {
  const content = wishlistDraft.value.trim()
  if (content.length < 2) {
    collectionError.value = t('profile.wishlistRequired')
    return
  }
  wishlistSaving.value = true
  collectionError.value = ''
  try {
    const created = await addWishlistItem({ content })
    wishlist.value.unshift(created)
    wishlistTotal.value += 1
    wishlistDraft.value = ''
  } catch (requestError) {
    collectionError.value = axios.isAxiosError(requestError)
      ? requestError.response?.data?.message || t('profile.collectionError')
      : t('profile.collectionError')
  } finally {
    wishlistSaving.value = false
  }
}

async function removeFavoriteItem(food: Food) {
  if (!window.confirm(t('profile.removeFavoriteConfirm', { name: food.name }))) return
  collectionRemoving.value = `favorite-${food.id}`
  collectionError.value = ''
  try {
    await removeFavorite(food.id)
    favorites.value = favorites.value.filter((item) => item.id !== food.id)
    favoritesTotal.value = Math.max(0, favoritesTotal.value - 1)
  } catch {
    collectionError.value = t('profile.collectionError')
  } finally {
    collectionRemoving.value = undefined
  }
}

async function removeWishlistItem(item: WishlistItem) {
  if (!window.confirm(t('profile.wishlistDeleteConfirm', { name: item.content }))) return
  collectionRemoving.value = `wishlist-${item.id}`
  collectionError.value = ''
  try {
    await deleteWishlistItem(item.id)
    wishlist.value = wishlist.value.filter((candidate) => candidate.id !== item.id)
    wishlistTotal.value = Math.max(0, wishlistTotal.value - 1)
  } catch {
    collectionError.value = t('profile.collectionError')
  } finally {
    collectionRemoving.value = undefined
  }
}

function handleSaved(updated: Food) {
  editSubmitted.value = true
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
  const revision = auth.getSessionRevision()
  const userId = user.value?.id
  try {
    const updatedUser = await updateMySignature(draft)
    if (!auth.setCurrentUser(updatedUser, revision, userId)) return
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
  const revision = auth.getSessionRevision()
  const userId = user.value?.id
  try {
    const updatedUser = await updateMyDisplayName(draft)
    if (!auth.setCurrentUser(updatedUser, revision, userId)) return
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
  if (achievementSaving.value !== undefined) return
  achievementSaving.value = design.id
  achievementError.value = ''
  try {
    await deleteEtching(design.id)
    etchings.value = etchings.value.filter((etching) => etching.id !== design.id)
  } catch (cause) {
    achievementError.value = apiErrorMessage(cause, t('etching.deleteError'))
  } finally { achievementSaving.value = undefined }
}

async function changeAvatar(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return

  avatarSaving.value = true
  avatarError.value = ''
  const revision = auth.getSessionRevision()
  const userId = user.value?.id
  try {
    const avatarUrl = await uploadImage(file)
    if (revision !== auth.getSessionRevision() || userId !== user.value?.id) return
    const updatedUser = await updateAvatar(avatarUrl)
    auth.setCurrentUser(updatedUser, revision, userId)
  } catch {
    avatarError.value = t('profile.avatarError')
  } finally {
    avatarSaving.value = false
    ;(event.target as HTMLInputElement).value = ''
  }
}

let profileRequestRunning = false
async function loadProfile() {
  if (profileRequestRunning) return
  profileRequestRunning = true
  loading.value = true
  error.value = ''
  loadFailures.value = []
  // Each section commits independently; a missing optional endpoint must not hide
  // successfully fetched etchings, foods, or favorites.
  async function section(key: string, request: () => Promise<void>) {
    try {
      await request()
    } catch {
      loadFailures.value.push(key)
      if (key === 'foods') error.value = t('profile.loadError')
    }
  }
  try {
    await Promise.all([
      section('foods', async () => { foods.value = await getMyFoods() }),
      section('footprints', async () => { footprints.value = await getMyFootprints() }),
      section('checkins', async () => {
        const result = await getMyCheckins()
        checkins.value = result.items
        checkinPage.value = result.page
        checkinTotal.value = result.total
      }),
      section('regions', async () => { regions.value = await getRegions() }),
      section('achievements', async () => { achievements.value = await getAchievements() }),
      section('etchings', async () => { etchings.value = await getMyEtchings() }),
    ])
  } finally {
    loading.value = false
    profileRequestRunning = false
  }
}
onMounted(loadProfile)

let collectionLoadSequence = 0
async function loadCurrentCollection(reset = true) {
  if (!reset && collectionsLoading.value) return
  const sequence = ++collectionLoadSequence
  const requestedTab = collectionTab.value
  collectionsLoading.value = true
  collectionError.value = ''
  try {
    if (requestedTab === 'favorites') {
      const page = reset ? 1 : favoritesPage.value + 1
      const result = await getMyFavoritesPage(page, 20)
      if (sequence !== collectionLoadSequence) return
      if (!result || !Array.isArray(result.items)) throw new Error('Invalid favorites page')
      favorites.value = reset ? result.items : [...favorites.value, ...result.items]
      favoritesPage.value = result.page
      favoritesTotal.value = result.total
    } else {
      const page = reset ? 1 : wishlistPage.value + 1
      const result = await getMyWishlistPage(page, 20)
      if (sequence !== collectionLoadSequence) return
      wishlist.value = reset ? result.items : [...wishlist.value, ...result.items]
      wishlistPage.value = result.page
      wishlistTotal.value = result.total
    }
  } catch (cause) {
    if (sequence === collectionLoadSequence) collectionError.value = apiErrorMessage(cause, t('profile.loadError'))
  } finally {
    if (sequence === collectionLoadSequence) collectionsLoading.value = false
  }
}

watch(collectionTab, () => { void loadCurrentCollection(true) }, { immediate: true })

</script>

<template>
  <div class="profile-page archive-profile">
    <p v-if="loadFailures.length" class="form-error" role="status">
      {{ t('profile.partialLoadError') }}
      <button type="button" :disabled="loading" @click="loadProfile">{{ t('share.retry') }}</button>
    </p>
    <section class="profile-hero">
      <LandmarkBackdrop />
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
      <div class="archive-featured-seal">
        <small>{{ t('archive.seals') }}</small>
        <p v-if="sealLoadFailed">{{ t('profile.sealLoadError') }}</p>
        <p v-else-if="loading">{{ t('profile.loading') }}</p>
        <template v-else-if="selectedEtching || selectedAchievement">
          <HexEtching v-if="selectedEtching" :layer-one="selectedEtching.layerOne" />
          <img v-else-if="selectedAchievement" :src="selectedAchievement.imageUrl" :alt="selectedAchievement.name">
          <strong>{{ selectedEtching?.name || selectedAchievement?.name }}</strong>
        </template>
        <p v-else>{{ t('archive.noSeal') }}</p>
        <button type="button" @click="selectArchiveTab('seals')">{{ t('archive.manageSeal') }} ↗</button>
      </div>
    </section>

    <nav class="archive-tabs" :aria-label="t('archive.profile')">
      <button v-for="tab in archiveTabs" :key="tab" type="button" :class="{ active: archiveTab === tab }"
        :aria-pressed="archiveTab === tab" aria-controls="archive-profile-content" @click="selectArchiveTab(tab)">
        {{ t('archive.' + tab) }}
      </button>
    </nav>
    <div id="archive-profile-content">
    <section v-show="archiveTab === 'favorites' || archiveTab === 'wishlist'" class="collection-panel">
      <div class="collection-heading">
        <div>
          <small>{{ t('profile.collectionsEyebrow') }}</small>
          <h2>{{ t('profile.collectionsTitle') }}</h2>
        </div>
        <p>{{ t('profile.collectionsHint') }}</p>
      </div>
      <div class="collection-tabs" role="tablist" :aria-label="t('profile.collectionsTitle')">
        <button
          type="button"
          role="tab"
          :aria-selected="collectionTab === 'favorites'"
          :class="{ active: collectionTab === 'favorites' }"
          @click="collectionTab = 'favorites'"
        >
          {{ t('profile.favoritesTab') }} <span>{{ favoritesTotal }}</span>
        </button>
        <button
          type="button"
          role="tab"
          :aria-selected="collectionTab === 'wishlist'"
          :class="{ active: collectionTab === 'wishlist' }"
          @click="collectionTab = 'wishlist'"
        >
          {{ t('profile.wishlistTab') }} <span>{{ wishlistTotal }}</span>
        </button>
      </div>

      <p v-if="collectionError" class="collection-error" aria-live="polite">{{ collectionError }}</p>
      <template v-if="collectionTab === 'favorites'">
        <div v-if="favorites.length" class="favorite-grid">
          <article v-for="food in favorites" :key="food.id" class="favorite-card">
            <RouterLink :to="`/foods/${food.id}`" class="favorite-cover">
              <img v-if="food.imageUrl" :src="food.imageUrl" :alt="food.name">
              <span v-else>{{ food.name.slice(0, 1) }}</span>
            </RouterLink>
            <div>
              <small>{{ food.region.province }} · {{ food.region.name }}</small>
              <h3><RouterLink :to="`/foods/${food.id}`">{{ food.name }}</RouterLink></h3>
              <button
                type="button"
                :disabled="collectionRemoving === `favorite-${food.id}`"
                @click="removeFavoriteItem(food)"
              >{{ t('profile.removeFavorite') }}</button>
            </div>
          </article>
        </div>
        <div v-else-if="!loading && !loadFailures.includes('favorites')" class="collection-empty">
          <p>{{ t('profile.favoriteEmpty') }}</p>
          <RouterLink to="/">{{ t('profile.browseFoods') }}</RouterLink>
        </div>
        <button v-if="favorites.length < favoritesTotal" type="button" :disabled="collectionsLoading" @click="loadCurrentCollection(false)">{{ t('home.loadMoreFavorites') }}</button>
      </template>

      <template v-else>
        <form class="wishlist-form" @submit.prevent="createWishlistItem">
          <label for="wishlist-content">{{ t('profile.wishlistAddHint') }}</label>
          <div>
            <input
              id="wishlist-content"
              v-model="wishlistDraft"
              maxlength="100"
              :placeholder="t('profile.wishlistPlaceholder')"
            >
            <button type="submit" :disabled="wishlistSaving">
              {{ wishlistSaving ? t('profile.wishlistSaving') : t('profile.wishlistAdd') }}
            </button>
          </div>
        </form>
        <div v-if="wishlist.length" class="wishlist-list">
          <article v-for="item in wishlist" :key="item.id" class="wishlist-card">
            <header>
              <div>
                <h3>{{ item.content }}</h3>
                <time>{{ formatDate(item.createdAt) }}</time>
              </div>
              <button
                type="button"
                :disabled="collectionRemoving === `wishlist-${item.id}`"
                @click="removeWishlistItem(item)"
              >{{ t('profile.wishlistDelete') }}</button>
            </header>
            <div v-if="item.matches?.length" class="wishlist-matches">
              <strong>{{ t('profile.wishlistMatchTitle') }}</strong>
              <RouterLink v-for="match in item.matches || []" :key="match.food.id" :to="`/foods/${match.food.id}`">
                <img v-if="match.food.imageUrl" :src="match.food.imageUrl" :alt="match.food.name">
                <span v-else class="wishlist-match-fallback">{{ match.food.name.slice(0, 1) }}</span>
                <span>
                  <b>{{ match.food.name }}</b>
                  <small>{{ match.food.region.province }} · {{ match.food.region.name }}</small>
                  <em>{{ t('profile.wishlistMatchReason', { fields: matchReason(match.matchedFields) }) }}</em>
                </span>
                <i aria-hidden="true">→</i>
              </RouterLink>
            </div>
            <p v-else class="wishlist-no-match">{{ t('profile.wishlistNoMatch') }}</p>
          </article>
        </div>
        <div v-else-if="!loading && !loadFailures.includes('wishlist')" class="collection-empty"><p>{{ t('profile.wishlistEmpty') }}</p></div>
        <button v-if="wishlist.length < wishlistTotal" type="button" :disabled="collectionsLoading" @click="loadCurrentCollection(false)">{{ t('home.loadMoreFavorites') }}</button>
      </template>
    </section>

    <section v-show="archiveTab === 'diary'" class="collection-panel checkin-panel">
      <div class="collection-heading"><div><small>味觉日记</small><h2>我的打卡</h2></div><p>记录真正吃过的珍馐，公开或仅自己可见。</p></div>
      <div v-if="checkins.length" class="checkin-list">
        <article v-for="item in checkins" :key="item.id" class="wishlist-card">
          <header><div><h3>{{ item.foodName }}</h3><time>{{ item.eatenOn }}</time></div><span>{{ item.visibility === 'PRIVATE' ? '仅自己可见' : '公开' }}</span></header>
          <form v-if="checkinEditing === item.id" class="checkin-edit-form" @submit.prevent="saveCheckin(item)">
            <input v-model="checkinDraft.eatenOn" type="date" required>
            <select v-model="checkinDraft.visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">仅自己可见</option></select>
            <textarea v-model="checkinDraft.note" maxlength="500" placeholder="这次有什么新的味觉记忆？"></textarea>
            <div><button :disabled="checkinSaving === item.id">保存</button><button type="button" @click="checkinEditing = undefined">取消</button></div>
          </form>
          <template v-else><p v-if="item.note">{{ item.note }}</p><div class="checkin-actions"><button type="button" @click="editCheckin(item)">编辑</button><button type="button" :disabled="checkinSaving === item.id" @click="removeCheckin(item)">删除</button></div></template>
        </article>
      </div>
      <p v-if="checkinError" class="collection-error">{{ checkinError }}</p>
      <button v-if="checkins.length < checkinTotal" type="button" :disabled="checkinLoadingMore" @click="loadMoreCheckins">
        {{ checkinLoadingMore ? '加载中…' : '加载更多' }}
      </button>
      <p v-else-if="!checkins.length && !loading && !loadFailures.includes('checkins')" class="collection-empty">还没有打卡，去菜品详情记录第一次体验吧。</p>
    </section>

    <section v-show="archiveTab === 'records' || archiveTab === 'seals'" class="profile-layout">
      <div v-show="archiveTab === 'records'" class="profile-foods">
        <div class="profile-section-title">
          <div>
            <small>{{ t('profile.recordsEyebrow') }}</small>
            <h2>{{ t('profile.myFoods') }}</h2>
          </div>
          <p>{{ t('profile.recordsHint') }}</p>
        </div>

        <p v-if="editSubmitted" class="profile-review-tip" role="status">{{ t('profile.editSubmitted') }}</p>
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
                  <button type="button" @click="editSubmitted = false; selectedFood = food">{{ t('profile.complete') }}</button>
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

      <aside v-show="archiveTab === 'seals'" class="etching-panel">
        <small>{{ t('profile.sealEyebrow') }}</small>
        <h2>{{ t('profile.sealTitle') }}</h2>
        <p v-if="loading" role="status">{{ t('profile.loading') }}</p>
        <p v-else-if="sealLoadFailed" class="etching-error" role="status">{{ t('profile.sealLoadError') }}</p>

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
        <template v-else-if="!loading && !sealLoadFailed">
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

    <section v-show="archiveTab === 'footprints'" class="footprint-panel">
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
      <p v-else-if="!loading && !loadFailures.includes('footprints')" class="footprint-empty">{{ t('profile.noFootprints') }}</p>
    </section>

    </div>
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
