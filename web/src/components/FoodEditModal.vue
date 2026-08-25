<script setup lang="ts">
import axios from 'axios'
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { updateMyFood, uploadImage } from '../api'
import type { Food, FoodUpdatePayload, Region } from '../types'
import RegionDrawer from './RegionDrawer.vue'

const props = defineProps<{ food: Food; regions: Region[] }>()
const emit = defineEmits<{ close: []; saved: [food: Food] }>()
const { t } = useI18n()

const form = reactive<FoodUpdatePayload>({
  name: props.food.name,
  regionId: props.food.region.id,
  latitude: props.food.latitude,
  longitude: props.food.longitude,
  address: props.food.address || '',
  summary: props.food.summary,
  story: props.food.story,
  ingredients: props.food.ingredients,
  imageUrl: props.food.imageUrl,
  remark: props.food.remark || '',
})
const image = ref<File>()
const saving = ref(false)
const error = ref('')
const regionDrawerOpen = ref(false)
const selectedRegion = computed(() => props.regions.find((region) => region.id === form.regionId))

function selectImage(event: Event) {
  image.value = (event.target as HTMLInputElement).files?.[0]
}

async function submit() {
  error.value = ''
  if (!form.regionId) {
    error.value = t('upload.regionRequired')
    return
  }
  saving.value = true
  try {
    if (image.value) form.imageUrl = await uploadImage(image.value)
    const updated = await updateMyFood(props.food.id, form)
    emit('saved', updated)
  } catch (requestError) {
    error.value = axios.isAxiosError(requestError)
      ? requestError.response?.data?.message || t('profile.updateError')
      : t('profile.updateError')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="modal-mask profile-edit-mask" @click.self="emit('close')">
    <section class="upload-modal profile-edit-modal">
      <div class="modal-title">
        <div>
          <small>{{ t('profile.completeEyebrow') }}</small>
          <h2>{{ t('profile.completeTitle', { name: food.name }) }}</h2>
        </div>
        <button class="icon-button" type="button" @click="emit('close')">×</button>
      </div>

      <p class="profile-review-tip">{{ t('profile.reviewTip') }}</p>
      <form @submit.prevent="submit">
        <div class="form-grid">
          <label>
            {{ t('upload.name') }}
            <input v-model.trim="form.name" required maxlength="100">
          </label>
          <label>
            {{ t('upload.region') }}
            <button class="region-select-trigger" type="button" @click="regionDrawerOpen = true">
              <span>{{ selectedRegion ? t('upload.regionPath', { province: selectedRegion.province, city: selectedRegion.name }) : t('upload.selectRegion') }}</span>
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

        <label>
          {{ t('upload.address') }}
          <input v-model.trim="form.address" maxlength="500">
        </label>
        <label>
          {{ t('upload.summary') }}
          <textarea v-model.trim="form.summary" required maxlength="1000" rows="2"></textarea>
        </label>
        <label>
          {{ t('upload.ingredients') }}
          <input v-model.trim="form.ingredients" required maxlength="500">
        </label>
        <label>
          {{ t('upload.story') }}
          <textarea v-model.trim="form.story" required rows="4"></textarea>
        </label>
        <label>
          {{ t('upload.remark') }}
          <textarea v-model.trim="form.remark" maxlength="1000" rows="3" :placeholder="t('upload.remarkPlaceholder')"></textarea>
        </label>
        <label>
          {{ t('profile.replaceCover') }}
          <input type="file" accept="image/jpeg,image/png,image/webp" @change="selectImage">
          <small>{{ food.imageUrl ? t('profile.keepCover') : t('upload.imageTip') }}</small>
        </label>

        <p v-if="error" class="form-error">{{ error }}</p>
        <div class="modal-actions">
          <button type="button" class="secondary-button" @click="emit('close')">{{ t('common.cancel') }}</button>
          <button class="primary-button" :disabled="saving">
            {{ saving ? t('profile.updating') : t('profile.saveChanges') }}
          </button>
        </div>
      </form>
    </section>

    <RegionDrawer
      :open="regionDrawerOpen"
      :regions="regions"
      :model-value="form.regionId"
      @close="regionDrawerOpen = false"
      @select="(id) => { if (id) form.regionId = id }"
    />
  </div>
</template>
