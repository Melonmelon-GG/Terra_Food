<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { getFoods, getRegions } from '../api'
import FoodMap from '../components/FoodMap.vue'
import FoodUploadModal from '../components/FoodUploadModal.vue'
import type { Food, Region } from '../types'

const foods = ref<Food[]>([])
const regions = ref<Region[]>([])
const keyword = ref('')
const selectedRegionId = ref<number>()
const loading = ref(true)
const error = ref('')
const uploadOpen = ref(false)
const pickedLatitude = ref<number>()
const pickedLongitude = ref<number>()

async function loadFoods() {
  loading.value = true
  error.value = ''

  try {
    foods.value = await getFoods({
      keyword: keyword.value || undefined,
      regionId: selectedRegionId.value,
    })
  } catch {
    error.value = '暂时无法取得珍馐档案，请确认后端服务已经启动。'
  } finally {
    loading.value = false
  }
}

function chooseRegion(regionId?: number) {
  selectedRegionId.value = regionId
  loadFoods()
}

function pickLocation(latitude: number, longitude: number) {
  pickedLatitude.value = latitude
  pickedLongitude.value = longitude
}

function handleSaved(food: Food) {
  uploadOpen.value = false
  foods.value = [food, ...foods.value]
}

onMounted(async () => {
  try {
    regions.value = await getRegions()
  } catch {
    error.value = '暂时无法取得地区信息。'
  }

  await loadFoods()
})
</script>

<template>
  <section class="hero">
    <p class="eyebrow">A COMPENDIUM OF CHINESE FLAVORS</p>
    <h1>山河有味，<em>烟火成诗</em></h1>
    <p>
      循着一方水土，寻访一道珍馐。收录食材、掌故与手艺，让每一种地域滋味都被看见。
    </p>
    <form @submit.prevent="loadFoods">
      <input v-model="keyword" placeholder="搜索一道菜、一座城……">
      <button>寻味</button>
    </form>
  </section>

  <section class="content">
    <div class="section-title">
      <div>
        <small>九州风土</small>
        <h2>地域寻味</h2>
      </div>
      <div class="section-actions">
        <p>一方水土，一方滋味</p>
        <button class="outline-action" @click="uploadOpen = true">
          ＋ 收录珍馐
        </button>
      </div>
    </div>

    <div class="regions">
      <button
        :class="{ active: selectedRegionId === undefined }"
        @click="chooseRegion()"
      >
        全部
      </button>
      <button
        v-for="region in regions"
        :key="region.id"
        :class="{ active: selectedRegionId === region.id }"
        @click="chooseRegion(region.id)"
      >
        {{ region.name }}
        <small>{{ region.province }}</small>
      </button>
    </div>

    <section class="map-journal">
      <div class="map-journal-title">
        <div>
          <small>TASTE ACROSS THE LAND</small>
          <h2>九州风味图卷</h2>
        </div>
        <p>点击地图选取位置，再将一道新的地方滋味收入志中</p>
      </div>

      <div class="map-frame">
        <FoodMap :foods="foods" @pick="pickLocation" />
        <div class="map-vignette"></div>
        <div class="map-hint">
          <span v-if="pickedLatitude">
            已选 {{ pickedLatitude.toFixed(3) }}, {{ pickedLongitude?.toFixed(3) }}
          </span>
          <span v-else>轻点地图，标记珍馐所在</span>
        </div>
      </div>
    </section>

    <div class="catalog-heading">
      <small>珍馐图鉴</small>
      <span>{{ foods.length }} 道记录</span>
    </div>

    <p v-if="loading" class="state">正在翻阅珍馐志……</p>
    <p v-else-if="error" class="state error">{{ error }}</p>

    <div v-else class="grid">
      <RouterLink
        v-for="food in foods"
        :key="food.id"
        :to="`/foods/${food.id}`"
        class="card"
      >
        <div
          class="photo"
          :style="{ backgroundImage: `url(${food.imageUrl})` }"
        >
          <span>{{ food.region.province }} · {{ food.region.name }}</span>
        </div>
        <div class="card-body">
          <h3>{{ food.name }}</h3>
          <p>{{ food.summary }}</p>
          <div>
            <b>热度 {{ food.heat }}</b>
            <span>阅其志 →</span>
          </div>
        </div>
      </RouterLink>
    </div>

    <p v-if="!loading && !error && foods.length === 0" class="state">
      此卷尚无记录，静候新的风味。
    </p>
  </section>

  <FoodUploadModal
    v-if="uploadOpen"
    :regions="regions"
    :latitude="pickedLatitude"
    :longitude="pickedLongitude"
    @close="uploadOpen = false"
    @saved="handleSaved"
  />
</template>
