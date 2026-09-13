<script setup lang="ts">
import axios from 'axios'
import { defineAsyncComponent, computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import {
  addFavorite,
  addWishlistItem,
  createFoodComment,
  createFoodCheckin,
  getFood,
  getFoodCommentsPage,
  getFavoriteStatus,
  getFoodLikeStatus,
  getWishlistStatus,
  likeFood,
  removeFavorite,
  unlikeFood,
} from '../api'
import { useAuth } from '../auth'
import { apiErrorMessage } from '../apiError'
import type { Food, FoodComment, FoodLikeStatus } from '../types'
import LandmarkBackdrop from '../components/LandmarkBackdrop.vue'
import '../archive.css'

const FoodShareModal = defineAsyncComponent(() => import('../components/FoodShareModal.vue'))
const shareOpen = ref(false)

const route = useRoute()
const router = useRouter()
const auth = useAuth()
const currentUser = auth.currentUser
const food = ref<Food>()
const comments = ref<FoodComment[]>([])
const commentsTotal = ref(0)
const commentsPage = ref(1)
const commentContent = ref('')
const checkinMode = ref(false)
function localDateInputValue(date = new Date()) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
const checkinDate = ref(localDateInputValue())
const checkinVisibility = ref<'PUBLIC' | 'PRIVATE'>('PUBLIC')
const checkinIdempotencyKey = ref(crypto.randomUUID())
watch([checkinDate, checkinVisibility, commentContent], () => {
  if (checkinMode.value && !submittingComment.value) checkinIdempotencyKey.value = crypto.randomUUID()
})
const error = ref('')
const commentError = ref('')
const commentsLoading = ref(false)
const submittingComment = ref(false)
const likeStatus = ref<FoodLikeStatus>({ likeCount: 0, likedByMe: false })
const liking = ref(false)
const favorited = ref<boolean | null>(null)
const wishlistListed = ref(false)
const collectionSaving = ref<'favorite' | 'wishlist'>()
const collectionError = ref('')
const statusLoading = ref(true)
const { t, locale } = useI18n()

let foodLoadController: AbortController | undefined

const imageFailed = ref(false)
watch(() => food.value?.imageUrl, () => { imageFailed.value = false })
const ingredientTags = computed(() => [...new Set((food.value?.ingredients || '').split(/[、，,；;\n]+/).map(value => value.trim()).filter(Boolean))].slice(0, 5))

function avatarInitial(name: string): string {
  return Array.from(name.trim())[0]?.toUpperCase() || '·'
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat(locale.value === 'zh-CN' ? 'zh-CN' : 'en-US', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

async function loadComments(foodIdValue: number, append = false) {
  const signal = foodLoadController?.signal
  commentsLoading.value = true
  commentError.value = ''
  try {
    const result = await getFoodCommentsPage(foodIdValue, append ? commentsPage.value + 1 : 1, 20, signal)
    if (signal?.aborted || Number(route.params.id) !== foodIdValue) return
    comments.value = append ? [...comments.value, ...result.items] : result.items
    commentsPage.value = result.page
    commentsTotal.value = result.total
  } catch {
    if (signal?.aborted) return
    commentError.value = t('detail.commentLoadError')
  } finally {
    if (!signal?.aborted) commentsLoading.value = false
  }
}

async function submitComment() {
  const content = commentContent.value.trim()
  if (!content) {
    commentError.value = t('detail.commentRequired')
    return
  }

  const signal = foodLoadController?.signal
  submittingComment.value = true
  commentError.value = ''
  try {
    const comment = await createFoodComment(Number(route.params.id), { content })
    if (signal?.aborted) return
    comments.value.unshift(comment)
    commentsTotal.value += 1
    commentContent.value = ''
  } catch (requestError) {
    if (signal?.aborted) return
    commentError.value = axios.isAxiosError(requestError)
      ? requestError.response?.data?.message || t('detail.commentSubmitError')
      : t('detail.commentSubmitError')
  } finally {
    if (!signal?.aborted) submittingComment.value = false
  }
}

async function submitCheckin() {
  if (!currentUser.value || submittingComment.value) return
  submittingComment.value = true
  commentError.value = ''
  try {
    const expectedFoodId = Number(route.params.id)
    const expectedUserId = currentUser.value.id
    await createFoodCheckin(expectedFoodId, { eatenOn: checkinDate.value, note: commentContent.value.trim() || undefined, visibility: checkinVisibility.value, timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Shanghai' }, checkinIdempotencyKey.value)
    if (Number(route.params.id) !== expectedFoodId || currentUser.value?.id !== expectedUserId) return
    commentContent.value = ''
    checkinMode.value = false
    checkinIdempotencyKey.value = crypto.randomUUID()
    await loadComments(Number(route.params.id))
  } catch (requestError) {
    commentError.value = apiErrorMessage(requestError, '打卡保存失败，请稍后重试。')
  } finally { submittingComment.value = false }
}

function handleAgentCommentPublished(event: Event) {
  const publishedFoodId = Number((event as CustomEvent<{ foodId?: number }>).detail?.foodId)
  if (publishedFoodId === Number(route.params.id)) void loadComments(publishedFoodId)
}

async function toggleLike() {
  if (!currentUser.value) {
    await router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (liking.value) return

  const activeFoodId = Number(route.params.id)
  const signal = foodLoadController?.signal
  liking.value = true
  try {
    const result = likeStatus.value.likedByMe
      ? await unlikeFood(activeFoodId)
      : await likeFood(activeFoodId)
    if (!signal?.aborted) likeStatus.value = result
  } catch {
    if (!signal?.aborted) commentError.value = t('detail.likeError')
  } finally {
    if (!signal?.aborted) liking.value = false
  }
}

async function toggleFavorite() {
  if (!currentUser.value) {
    await router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (collectionSaving.value) return
  const foodId = Number(route.params.id)
  const signal = foodLoadController?.signal
  collectionSaving.value = 'favorite'
  collectionError.value = ''
  try {
    const previous = favorited.value ?? (await getFavoriteStatus(foodId)).favorited
    if (signal?.aborted) return
    const result = previous ? await removeFavorite(foodId) : await addFavorite(foodId)
    if (!signal?.aborted) favorited.value = result.favorited
  } catch (cause) {
    if (!signal?.aborted) collectionError.value = apiErrorMessage(cause, t('detail.favoriteError'))
  } finally {
    if (!signal?.aborted) collectionSaving.value = undefined
  }
}

async function addToWishlist() {
  if (!currentUser.value) {
    await router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (wishlistListed.value) {
    await router.push({ path: '/profile', query: { tab: 'wishlist' } })
    return
  }
  if (collectionSaving.value) return
  const signal = foodLoadController?.signal
  collectionSaving.value = 'wishlist'
  collectionError.value = ''
  try {
    await addWishlistItem({ foodId: Number(route.params.id) })
    if (!signal?.aborted) wishlistListed.value = true
  } catch (cause) {
    if (!signal?.aborted) collectionError.value = apiErrorMessage(cause, t('detail.wishlistError'))
  } finally {
    if (!signal?.aborted) collectionSaving.value = undefined
  }
}
// 组件复用时随路由 id 变化重新加载（安全报告 6.6），旧请求用 AbortController 丢弃。
watch(
  () => [route.params.id, currentUser.value?.id],
  async ([id]) => {
    const foodIdValue = Number(id)
    shareOpen.value = false
    foodLoadController?.abort()
    foodLoadController = new AbortController()
    const signal = foodLoadController.signal
    statusLoading.value = true
    food.value = undefined
    comments.value = []
    likeStatus.value = { likeCount: 0, likedByMe: false }
    favorited.value = null
    wishlistListed.value = false
    collectionSaving.value = undefined
    liking.value = false
    submittingComment.value = false
    commentContent.value = ''
    commentError.value = ''
    collectionError.value = ''
    error.value = ''

    try {
      const loaded = await getFood(foodIdValue)
      if (signal.aborted) return
      food.value = loaded
    } catch {
      if (!signal.aborted) error.value = t('detail.notFound')
      return
    }
    // Failures in optional status endpoints must not erase successful responses.
    const results = await Promise.allSettled([
      getFoodLikeStatus(foodIdValue),
      currentUser.value ? getFavoriteStatus(foodIdValue) : Promise.resolve({ favorited: false }),
      currentUser.value ? getWishlistStatus(foodIdValue) : Promise.resolve({ listed: false }),
      loadComments(foodIdValue),
    ])
    if (signal.aborted) return
    statusLoading.value = false
    const [likes, favorite, wishlist] = results
    if (likes.status === 'fulfilled' && !liking.value) likeStatus.value = likes.value
    if (favorite.status === 'fulfilled' && !collectionSaving.value) favorited.value = favorite.value.favorited
    if (wishlist.status === 'fulfilled' && !collectionSaving.value) wishlistListed.value = wishlist.value.listed
    if (favorite.status === 'rejected') collectionError.value = apiErrorMessage(favorite.reason, t('detail.favoriteError'))
    else if (wishlist.status === 'rejected') collectionError.value = apiErrorMessage(wishlist.reason, t('detail.wishlistError'))
  },
  { immediate: true },
)

onMounted(() => {
  window.addEventListener('agent:comment-published', handleAgentCommentPublished)
})

onBeforeUnmount(() => {
  foodLoadController?.abort()
  window.removeEventListener('agent:comment-published', handleAgentCommentPublished)
})
</script>

<template>
  <div v-if="food" class="detail archive-detail">
    <div class="archive-breadcrumb">
    <RouterLink to="/" class="back">
      {{ t('detail.back') }}
    </RouterLink>
    <span>{{ t('archive.motto') }}</span>
    </div>

    <div class="detail-hero">
      <figure class="archive-food-visual">
        <img v-if="food.imageUrl && !imageFailed" :src="food.imageUrl" :alt="food.name" fetchpriority="high" @error="imageFailed = true">
        <div v-else class="archive-no-cover"><span>食</span><p>{{ t('archive.noCover') }}</p></div>
      <button type="button" class="food-share-button" @click="shareOpen = true">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><path d="m9 8 6-3m-6 11 6 3"/><circle cx="6" cy="12" r="4"/><circle cx="18" cy="4" r="3"/><circle cx="18" cy="20" r="3"/></svg>
        {{ t('share.open') }}
      </button>
      </figure>
      <div class="archive-dossier">
        <LandmarkBackdrop :province="food.region.province" />
        <div class="archive-dossier-heading"><span>{{ t('archive.foodDossier') }}</span><small>NO. {{ String(food.id).padStart(5, '0') }}</small></div>
        <div class="archive-dossier-copy">
        <small>{{ food.region.province }} · {{ food.region.name }}</small>
        <h1>{{ food.name }}</h1>
        <p>{{ food.summary }}</p>
        <p v-if="food.address" class="archive-address">{{ food.address }}</p>
        <div class="archive-ingredient-tags" :aria-label="t('detail.ingredients')"><span v-for="ingredient in ingredientTags" :key="ingredient">{{ ingredient }}</span></div>
        <div class="collection-actions">
          <button
            class="like-button"
            :class="{ liked: likeStatus.likedByMe }"
            :disabled="statusLoading || liking"
            type="button"
            @click="toggleLike"
          >
            <span aria-hidden="true">{{ likeStatus.likedByMe ? '♥' : '♡' }}</span>
            {{ t('detail.likeCount', { count: likeStatus.likeCount }) }}
          </button>
          <button
            class="collection-button"
            :class="{ active: favorited }"
            :disabled="statusLoading || collectionSaving !== undefined"
            type="button"
            @click="toggleFavorite"
          >
            <span aria-hidden="true">{{ favorited ? '★' : '☆' }}</span>
            {{ favorited ? t('detail.favorited') : t('detail.favorite') }}
          </button>
          <button
            class="collection-button"
            :class="{ active: wishlistListed }"
            :disabled="statusLoading || collectionSaving !== undefined"
            type="button"
            @click="addToWishlist"
          >
            <span aria-hidden="true">＋</span>
            {{ wishlistListed ? t('detail.wishlistAdded') : t('detail.addWishlist') }}
          </button>
        </div>
        <p v-if="collectionError" class="collection-action-error" aria-live="polite">{{ collectionError }}</p>
      </div>
    <section class="food-creator">
      <RouterLink
        v-if="food.creator.id"
        :to="`/users/${food.creator.id}`"
        class="food-creator-profile"
      >
        <div class="user-avatar food-creator-avatar">
          <img
            v-if="food.creator.avatarUrl"
            :src="food.creator.avatarUrl"
            :alt="t('detail.userAvatar', { name: food.creator.displayName })"
          >
          <span v-else>{{ avatarInitial(food.creator.displayName) }}</span>
        </div>
        <div>
          <small>{{ t('detail.uploadedBy') }}</small>
          <strong>{{ food.creator.displayName }}</strong>
          <span>@{{ food.creator.username }}</span>
        </div>
      </RouterLink>
      <div v-else class="food-creator-profile">
        <div class="user-avatar food-creator-avatar">
          <span>{{ avatarInitial(food.creator.displayName) }}</span>
        </div>
        <div>
          <small>{{ t('detail.uploadedBy') }}</small>
          <strong>{{ food.creator.displayName }}</strong>
          <span>@{{ food.creator.username }}</span>
        </div>
      </div>
    </section>
      </div>
    </div>

    <FoodShareModal v-if="shareOpen" :food="food" @close="shareOpen = false" />

    <article class="archive-reading">
      <section>
        <small>{{ t('detail.ingredientsEyebrow') }}</small>
        <h2>{{ t('detail.ingredients') }}</h2>
        <p>{{ food.ingredients }}</p>
      </section>
      <section>
        <small>{{ t('detail.storyEyebrow') }}</small>
        <h2>{{ t('detail.story') }}</h2>
        <p>{{ food.story }}</p>
      </section>
      <section v-if="food.remark">
        <small>{{ t('detail.remarkEyebrow') }}</small>
        <h2>{{ t('detail.remark') }}</h2>
        <p>{{ food.remark }}</p>
      </section>
    </article>

    <section class="comment-section">
      <div class="comment-heading">
        <div>
          <small>{{ t('detail.commentsEyebrow') }}</small>
          <h2>{{ t('detail.comments') }}</h2>
        </div>
        <span>{{ t('detail.commentCount', { count: commentsTotal }) }}</span>
      </div>

    <form v-if="currentUser" class="comment-form" @submit.prevent="checkinMode ? submitCheckin() : submitComment()">
        <div class="user-avatar comment-form-avatar">
          <img
            v-if="currentUser.avatarUrl"
            :src="currentUser.avatarUrl"
            :alt="t('detail.userAvatar', { name: currentUser.displayName })"
          >
          <span v-else>{{ avatarInitial(currentUser.displayName) }}</span>
        </div>
        <div>
          <div class="comment-mode-switch">
            <button type="button" :class="{ active: !checkinMode }" @click="checkinMode = false">{{ t('detail.commentMode') }}</button>
            <button type="button" :class="{ active: checkinMode }" @click="checkinMode = true">{{ t('detail.checkinMode') }}</button>
          </div>
          <label for="food-comment">{{ checkinMode ? t('detail.checkinAs', { name: currentUser.displayName }) : t('detail.commentAs', { name: currentUser.displayName }) }}</label>
          <div v-if="checkinMode" class="checkin-options">
            <label>{{ t('detail.checkinDate') }} <input v-model="checkinDate" type="date" :max="localDateInputValue()" required></label>
            <label>{{ t('detail.checkinVisibility') }} <select v-model="checkinVisibility"><option value="PUBLIC">{{ t('detail.checkinPublic') }}</option><option value="PRIVATE">{{ t('detail.checkinPrivate') }}</option></select></label>
          </div>
          <textarea
            id="food-comment"
            v-model="commentContent"
            maxlength="500"
            :placeholder="t('detail.commentPlaceholder')"
            :required="!checkinMode"
          />
          <div class="comment-form-actions">
            <small>{{ commentContent.length }}/500</small>
            <button :disabled="submittingComment">
              {{ submittingComment ? t('detail.commentSubmitting') : (checkinMode ? t('detail.checkinSubmit') : t('detail.commentSubmit')) }}
            </button>
          </div>
        </div>
      </form>
      <p v-else class="comment-login-hint">
        <RouterLink to="/login" :query="{ redirect: route.fullPath }">{{ t('detail.loginToComment') }}</RouterLink>
      </p>

      <p v-if="commentError" class="form-error comment-message">{{ commentError }}</p>
      <p v-if="commentsLoading" class="comment-state">{{ t('detail.commentsLoading') }}</p>
      <p v-else-if="comments.length === 0" class="comment-state">{{ t('detail.commentsEmpty') }}</p>

      <div v-else class="comment-list">
        <div v-for="comment in comments" :key="comment.id" class="comment-card">
          <RouterLink
            v-if="comment.author.id"
            :to="`/users/${comment.author.id}`"
            class="comment-profile-link"
          >
            <div class="user-avatar comment-avatar">
              <img
                v-if="comment.author.avatarUrl"
                :src="comment.author.avatarUrl"
                :alt="t('detail.userAvatar', { name: comment.author.displayName })"
              >
              <span v-else>{{ avatarInitial(comment.author.displayName) }}</span>
            </div>
          </RouterLink>
          <div v-else class="user-avatar comment-avatar">
            <img
              v-if="comment.author.avatarUrl"
              :src="comment.author.avatarUrl"
              :alt="t('detail.userAvatar', { name: comment.author.displayName })"
            >
            <span v-else>{{ avatarInitial(comment.author.displayName) }}</span>
          </div>
          <div class="comment-body">
            <div class="comment-meta">
              <div>
                <RouterLink
                  v-if="comment.author.id"
                  :to="`/users/${comment.author.id}`"
                  class="comment-author-link"
                >
                  <strong>{{ comment.author.displayName }}</strong>
                </RouterLink>
                <strong v-else>{{ comment.author.displayName }}</strong>
                <span>@{{ comment.author.username }}</span>
              </div>
              <time :datetime="comment.createdAt">{{ formatDate(comment.createdAt) }}</time>
            </div>
            <small v-if="comment.checkinId" class="checkin-badge">{{ t('detail.checkinBadge', { date: comment.eatenOn }) }}</small>
            <p>{{ comment.content || t('detail.checkinWithoutNote') }}</p>
          </div>
        </div>
      </div>
      <button v-if="comments.length < commentsTotal" type="button" :disabled="commentsLoading" @click="loadComments(Number(route.params.id), true)">{{ t('home.loadMoreFavorites') }}</button>
    </section>
  </div>
  <p v-else class="state">
    {{ error || t('detail.loading') }}
  </p>
</template>
