<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'

import { getUserPublic } from '../api'
import type { UserPublic } from '../types'

const route = useRoute()
const { t } = useI18n()
const profile = ref<UserPublic>()
const loading = ref(true)
const error = ref('')

function avatarInitial(name: string): string {
  return name?.trim().charAt(0) || '食'
}

let loadSequence = 0

async function loadProfile(userId: number) {
  const requestSequence = ++loadSequence
  loading.value = true
  error.value = ''
  try {
    const response = await getUserPublic(userId)
    if (requestSequence === loadSequence) {
      profile.value = response
    }
  } catch {
    if (requestSequence === loadSequence) {
      profile.value = undefined
      error.value = t('user.notFound')
    }
  } finally {
    if (requestSequence === loadSequence) {
      loading.value = false
    }
  }
}

watch(
  () => route.params.id,
  (id) => {
    if (id) void loadProfile(Number(id))
  },
  { immediate: true },
)
</script>

<template>
  <section class="user-page">
    <p v-if="loading" class="user-state">{{ t('user.loading') }}</p>
    <p v-else-if="error" class="user-state error">{{ error }}</p>

    <template v-else-if="profile">
      <header class="user-hero">
        <div class="user-avatar">
          <img
            v-if="profile.avatarUrl"
            :src="profile.avatarUrl"
            :alt="t('user.avatarAlt', { name: profile.displayName })"
          >
          <span v-else>{{ avatarInitial(profile.displayName) }}</span>
        </div>
        <div>
          <small>{{ t('user.eyebrow') }}</small>
          <h1>{{ profile.displayName }}</h1>
          <p>@{{ profile.username }}</p>
          <p v-if="profile.signature" class="user-signature">{{ profile.signature }}</p>
        </div>
      </header>

      <section class="user-foods">
        <div class="user-foods-heading">
          <small>{{ t('user.foodsEyebrow') }}</small>
          <h2>{{ t('user.foods') }}</h2>
          <span>{{ profile.foods.length }}</span>
        </div>

        <p v-if="!profile.foods.length" class="user-state">{{ t('user.foodsEmpty') }}</p>
        <div v-else class="user-food-grid">
          <RouterLink
            v-for="food in profile.foods"
            :key="food.id"
            :to="`/foods/${food.id}`"
            class="user-food-card"
          >
            <div
              class="user-food-photo"
              :class="{ 'no-cover': !food.imageUrl }"
              :style="{ backgroundImage: food.imageUrl ? `url(${food.imageUrl})` : undefined }"
            >
              <span>{{ food.region.province }} · {{ food.region.name }}</span>
            </div>
            <div class="user-food-body">
              <h3>{{ food.name }}</h3>
              <p>{{ food.summary }}</p>
              <b>{{ t('home.heat', { value: food.heat }) }}</b>
            </div>
          </RouterLink>
        </div>
      </section>
    </template>
  </section>
</template>

<style scoped>
.user-page {
  max-width: 960px;
  margin: 0 auto;
  padding: 36px 20px 56px;
}

.user-state {
  padding: 48px 16px;
  color: #796759;
  font-size: 13px;
  text-align: center;
}

.user-state.error {
  color: #9e3028;
}

.user-hero {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  align-items: center;
  gap: 22px;
  padding: 24px;
  color: #3e2b23;
  background:
    radial-gradient(circle at 85% 15%, #a7493324, transparent 34%),
    #f6efe1;
  border: 1px solid #d4c5ae;
}

.user-avatar {
  display: grid;
  width: 96px;
  height: 96px;
  overflow: hidden;
  place-items: center;
  color: #fff;
  font-size: 38px;
  background: #5b463a;
  border: 3px solid #fff8ec;
  border-radius: 50%;
  box-shadow: 0 8px 22px #3a241a33;
}

.user-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-hero small,
.user-foods-heading small {
  color: #9a352e;
  font-size: 10px;
  letter-spacing: 3px;
}

.user-hero h1 {
  margin: 5px 0 2px;
  font-size: 27px;
}

.user-hero p:not(.user-signature) {
  margin: 0;
  color: #907b6d;
  font-size: 13px;
}

.user-signature {
  margin: 10px 0 0;
  color: #5f4739;
  font-size: 14px;
  line-height: 1.7;
}

.user-foods {
  margin-top: 30px;
}

.user-foods-heading {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 14px;
  padding-bottom: 10px;
  border-bottom: 1px solid #dccbb1;
}

.user-foods-heading h2 {
  margin: 0;
  font-size: 21px;
}

.user-foods-heading span {
  color: #907b6d;
  font-size: 12px;
}

.user-food-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 14px;
}

.user-food-card {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  overflow: hidden;
  color: inherit;
  text-decoration: none;
  background: #fff;
  border: 1px solid #d8c8af;
}

.user-food-card:hover {
  border-color: #9d574d;
  box-shadow: 0 6px 18px #46302214;
  transform: translateY(-1px);
}

.user-food-photo {
  display: grid;
  min-height: 96px;
  place-items: center;
  color: #fff;
  font-size: 11px;
  text-align: center;
  background: #eee2d0;
  background-size: cover;
  background-position: center;
}

.user-food-photo.no-cover {
  color: #a28a75;
  font-size: 10px;
}

.user-food-body {
  padding: 12px;
}

.user-food-body h3 {
  margin: 0 0 6px;
  font-size: 15px;
}

.user-food-body p {
  display: -webkit-box;
  margin: 0 0 8px;
  overflow: hidden;
  color: #756357;
  font-size: 12px;
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.user-food-body b {
  color: #9a352e;
  font-size: 11px;
  font-weight: 600;
}

@media (max-width: 600px) {
  .user-hero {
    grid-template-columns: 72px minmax(0, 1fr);
    gap: 14px;
    padding: 18px;
  }

  .user-avatar {
    width: 72px;
    height: 72px;
    font-size: 30px;
  }

  .user-food-grid {
    grid-template-columns: 1fr;
  }
}
</style>