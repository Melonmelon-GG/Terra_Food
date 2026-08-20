<script setup lang="ts">
import axios from 'axios'
import { reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { createFood, uploadImage } from '../api'
import type { Food, FoodCreatePayload, Region } from '../types'

const props = defineProps<{
  regions: Region[]
  latitude?: number
  longitude?: number
  regionId?: number
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
  summary: '',
  story: '',
  ingredients: '',
  imageUrl: '',
})

const image = ref<File>()
const saving = ref(false)
const error = ref('')
const { t } = useI18n()

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
  } catch (requestError) {
    error.value = axios.isAxiosError(requestError) && requestError.response?.status === 429
      ? t('upload.rateLimitError')
      : t('upload.saveError')
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
            <select v-model.number="form.regionId" required>
              <option :value="0" disabled>{{ t('upload.selectRegion') }}</option>
              <option v-for="region in regions" :key="region.id" :value="region.id">
                {{ region.province }} · {{ region.name }}
              </option>
            </select>
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
  </div>
</template>
