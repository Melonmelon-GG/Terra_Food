<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { createFood, uploadImage } from '../api'
import RegionDrawer from './RegionDrawer.vue'
import type { Food, FoodCreatePayload, Region } from '../types'

const props = defineProps<{
  regions: Region[]
  latitude?: number
  longitude?: number
  regionId?: number
  address?: string
}>()

const emit = defineEmits<{
  close: []
  saved: [food: Food]
}>()

const form = reactive<FoodCreatePayload>({
  name: '',
  regionId: 0,
  latitude: 35.5,
  longitude: 104.2,
  address: '',
  summary: '',
  story: '',
  ingredients: '',
  remark: '',
})

const image = ref<File>()
const saving = ref(false)
const error = ref('')
const regionDrawerOpen = ref(false)
const { t } = useI18n()

const selectedRegion = computed(() => props.regions.find((region) => region.id === form.regionId))
const pickedCoordinatesUnchanged = computed(() => {
  return props.latitude != null
    && props.longitude != null
    && Math.abs(form.latitude - props.latitude) < 0.000001
    && Math.abs(form.longitude - props.longitude) < 0.000001
})
const regionMismatch = computed(() => {
  return pickedCoordinatesUnchanged.value
    && props.regionId != null
    && form.regionId > 0
    && form.regionId !== props.regionId
})

watch(
  () => [props.latitude, props.longitude],
  ([latitude, longitude]) => {
    if (latitude != null && longitude != null) {
      form.latitude = Number(latitude.toFixed(7))
      form.longitude = Number(longitude.toFixed(7))
    }
  },
  { immediate: true },
)

watch(
  () => props.regionId,
  (regionId) => {
    if (regionId != null) {
      form.regionId = regionId
    }
  },
  { immediate: true },
)

watch(
  () => props.address,
  (address) => {
    form.address = address || ''
  },
  { immediate: true },
)

function selectRegion(regionId?: number) {
  if (regionId != null) {
    form.regionId = regionId
    error.value = ''
  }
}

function selectImage(event: Event) {
  const input = event.target as HTMLInputElement
  image.value = input.files?.[0]
}

async function submit() {
  error.value = ''

  if (!form.regionId) {
    error.value = t('upload.regionRequired')
    return
  }

  if (regionMismatch.value) {
    error.value = t('upload.regionCoordinateMismatch')
    return
  }

  saving.value = true
  try {
    // 图片与菜品信息分两步提交：先取得资源 URL，再保存稳定的业务记录。
    if (image.value) {
      form.imageUrl = await uploadImage(image.value)
    }

    const food = await createFood(form)
    if (food.reviewStatus === 'PENDING') {
      window.alert(t('upload.pendingSuccess'))
    }
    emit('saved', food)
  } catch {
    error.value = t('upload.saveError')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="modal-mask" @click.self="emit('close')">
    <section class="upload-modal">
      <div class="modal-title">
        <div>
          <small>{{ t('upload.eyebrow') }}</small>
          <h2>{{ t('upload.title') }}</h2>
        </div>
        <button class="icon-button" @click="emit('close')">×</button>
      </div>

      <form @submit.prevent="submit">
        <div class="form-grid">
          <label>
            {{ t('upload.name') }}
            <input v-model="form.name" required maxlength="100">
          </label>
          <label>
            {{ t('upload.region') }}
            <button class="region-select-trigger" type="button" @click="regionDrawerOpen = true">
              <span>
                {{ selectedRegion
                  ? t('upload.regionPath', { province: selectedRegion.province, city: selectedRegion.name })
                  : t('upload.selectRegion')
                }}
              </span>
              <b>›</b>
            </button>
          </label>
          <label>
            {{ t('upload.latitude') }}
            <input v-model.number="form.latitude" type="number" min="-90" max="90" step="0.0000001" required>
          </label>
          <label>
            {{ t('upload.longitude') }}
            <input v-model.number="form.longitude" type="number" min="-180" max="180" step="0.0000001" required>
          </label>
        </div>

        <p class="coordinate-tip">{{ t('upload.coordinateTip') }}</p>
        <p v-if="regionMismatch" class="coordinate-warning">{{ t('upload.regionCoordinateMismatch') }}</p>

        <label>
          {{ t('upload.address') }}
          <input v-model.trim="form.address" maxlength="500" :placeholder="t('upload.addressPlaceholder')">
        </label>

        <label>
          {{ t('upload.summary') }}
          <textarea v-model="form.summary" required maxlength="1000" rows="2"></textarea>
        </label>
        <label>
          {{ t('upload.ingredients') }}
          <input v-model="form.ingredients" required maxlength="500">
        </label>
        <label>
          {{ t('upload.story') }}
          <textarea v-model="form.story" required rows="4"></textarea>
        </label>
        <label>
          {{ t('upload.remark') }}
          <textarea v-model="form.remark" maxlength="1000" rows="3" :placeholder="t('upload.remarkPlaceholder')"></textarea>
        </label>
        <label>
          {{ t('upload.cover') }}
          <input type="file" accept="image/jpeg,image/png,image/webp" @change="selectImage">
          <small>{{ t('upload.imageTip') }}</small>
        </label>

        <p v-if="error" class="form-error">{{ error }}</p>

        <div class="modal-actions">
          <button type="button" class="secondary-button" @click="emit('close')">
            {{ t('common.cancel') }}
          </button>
          <button class="primary-button" :disabled="saving">
            {{ saving ? t('upload.saving') : t('upload.submit') }}
          </button>
        </div>
      </form>
    </section>

    <RegionDrawer
      :open="regionDrawerOpen"
      :regions="regions"
      :model-value="form.regionId || undefined"
      @close="regionDrawerOpen = false"
      @select="selectRegion"
    />
  </div>
</template>
