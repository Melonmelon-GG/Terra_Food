<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { getFood } from '../api'
import type { Food } from '../types'
const route = useRoute()
const food = ref<Food>()
const error = ref('')
const { t } = useI18n()

onMounted(async () => {
  try {
    food.value = await getFood(Number(route.params.id))
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

    <div
      class="detail-hero"
      :style="{ backgroundImage: `linear-gradient(90deg, rgba(25, 12, 8, 0.78), rgba(25, 12, 8, 0.12)), url(${food.imageUrl})` }"
    >
      <div>
        <small>{{ food.region.province }} · {{ food.region.name }}</small>
        <h1>{{ food.name }}</h1>
        <p>{{ food.summary }}</p>
      </div>
    </div>
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
    </article>
  </div>
  <p v-else class="state">
    {{ error || t('detail.loading') }}
  </p>
</template>
