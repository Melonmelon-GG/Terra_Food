<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getFood } from '../api'
import type { Food } from '../types'
const route = useRoute()
const food = ref<Food>()
const error = ref('')

onMounted(async () => {
  try {
    food.value = await getFood(Number(route.params.id))
  } catch {
    error.value = '没有找到这篇珍馐记录。'
  }
})
</script>

<template>
  <div v-if="food" class="detail">
    <RouterLink to="/" class="back">
      ← 返回珍馐图鉴
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
        <small>食之有物</small>
        <h2>主要食材</h2>
        <p>{{ food.ingredients }}</p>
      </section>
      <section>
        <small>味外之味</small>
        <h2>珍馐掌故</h2>
        <p>{{ food.story }}</p>
      </section>
    </article>
  </div>
  <p v-else class="state">
    {{ error || '正在展开卷册……' }}
  </p>
</template>
