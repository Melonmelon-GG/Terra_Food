<script setup lang="ts">
import axios from 'axios'
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'

import { createFoodComment, getFood, getFoodComments } from '../api'
import { useAuth } from '../auth'
import type { Food, FoodComment } from '../types'

const route = useRoute()
const auth = useAuth()
const currentUser = auth.currentUser
const foodId = Number(route.params.id)
const food = ref<Food>()
const comments = ref<FoodComment[]>([])
const commentContent = ref('')
const error = ref('')
const commentError = ref('')
const commentsLoading = ref(false)
const submittingComment = ref(false)
const { t, locale } = useI18n()

const heroStyle = computed(() => ({
  backgroundImage: food.value?.imageUrl
    ? 'linear-gradient(90deg, rgba(25, 12, 8, 0.78), rgba(25, 12, 8, 0.12)), url("' + food.value.imageUrl + '")'
    : 'linear-gradient(135deg, #79483a, #211512)',
}))

function avatarInitial(name: string): string {
  return Array.from(name.trim())[0]?.toUpperCase() || '·'
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat(locale.value === 'zh-CN' ? 'zh-CN' : 'en-US', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

async function loadComments() {
  commentsLoading.value = true
  commentError.value = ''
  try {
    comments.value = await getFoodComments(foodId)
  } catch {
    commentError.value = t('detail.commentLoadError')
  } finally {
    commentsLoading.value = false
  }
}

async function submitComment() {
  const content = commentContent.value.trim()
  if (!content) {
    commentError.value = t('detail.commentRequired')
    return
  }

  submittingComment.value = true
  commentError.value = ''
  try {
    const comment = await createFoodComment(foodId, { content })
    comments.value.unshift(comment)
    commentContent.value = ''
  } catch (requestError) {
    commentError.value = axios.isAxiosError(requestError)
      ? requestError.response?.data?.message || t('detail.commentSubmitError')
      : t('detail.commentSubmitError')
  } finally {
    submittingComment.value = false
  }
}

onMounted(async () => {
  try {
    food.value = await getFood(foodId)
    await loadComments()
  } catch {
    error.value = t('detail.notFound')
  }
})
</script>

<template>
  <div v-if="food" class="detail">
    <RouterLink to="/" class="back">
      {{ t('detail.back') }}
    </RouterLink>

    <div class="detail-hero" :style="heroStyle">
      <div>
        <small>{{ food.region.province }} · {{ food.region.name }}</small>
        <h1>{{ food.name }}</h1>
        <p>{{ food.summary }}</p>
      </div>
    </div>

    <section class="food-creator">
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
    </section>

    <article>
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
        <span>{{ t('detail.commentCount', { count: comments.length }) }}</span>
      </div>

      <form v-if="currentUser" class="comment-form" @submit.prevent="submitComment">
        <div class="user-avatar comment-form-avatar">
          <img
            v-if="currentUser.avatarUrl"
            :src="currentUser.avatarUrl"
            :alt="t('detail.userAvatar', { name: currentUser.displayName })"
          >
          <span v-else>{{ avatarInitial(currentUser.displayName) }}</span>
        </div>
        <div>
          <label for="food-comment">{{ t('detail.commentAs', { name: currentUser.displayName }) }}</label>
          <textarea
            id="food-comment"
            v-model="commentContent"
            maxlength="500"
            :placeholder="t('detail.commentPlaceholder')"
            required
          />
          <div class="comment-form-actions">
            <small>{{ commentContent.length }}/500</small>
            <button :disabled="submittingComment">
              {{ submittingComment ? t('detail.commentSubmitting') : t('detail.commentSubmit') }}
            </button>
          </div>
        </div>
      </form>

      <p v-if="commentError" class="form-error comment-message">{{ commentError }}</p>
      <p v-if="commentsLoading" class="comment-state">{{ t('detail.commentsLoading') }}</p>
      <p v-else-if="comments.length === 0" class="comment-state">{{ t('detail.commentsEmpty') }}</p>

      <div v-else class="comment-list">
        <div v-for="comment in comments" :key="comment.id" class="comment-card">
          <div class="user-avatar comment-avatar">
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
                <strong>{{ comment.author.displayName }}</strong>
                <span>@{{ comment.author.username }}</span>
              </div>
              <time :datetime="comment.createdAt">{{ formatDate(comment.createdAt) }}</time>
            </div>
            <p>{{ comment.content }}</p>
          </div>
        </div>
      </div>
    </section>
  </div>
  <p v-else class="state">
    {{ error || t('detail.loading') }}
  </p>
</template>
