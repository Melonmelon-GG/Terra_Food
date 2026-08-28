<script setup lang="ts">
import axios from 'axios'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { updateMyFood, uploadImage } from '../api'
import {
  cacheDraftImage,
  clearDraft,
  forgetDraftImage,
  getCachedDraftImage,
  readDraft,
  saveDraft,
  type DraftImageMeta,
} from '../drafts'
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
const imageMeta = ref<DraftImageMeta>()
const previewUrl = ref('')
const coverInput = ref<HTMLInputElement>()
const saving = ref(false)
const error = ref('')
const regionDrawerOpen = ref(false)
const selectedRegion = computed(() => props.regions.find((region) => region.id === form.regionId))

// 草稿缓存：按菜品区分，误关弹窗再打开不丢失填写内容；地区/坐标始终以菜品档案为准。
const DRAFT_KEY = `foodEdit:${props.food.id}`

interface EditDraft {
  name: string
  summary: string
  ingredients: string
  story: string
  remark: string
  image?: DraftImageMeta
}

const draft = readDraft<EditDraft>(DRAFT_KEY)
if (draft) {
  form.name = draft.name
  form.summary = draft.summary
  form.ingredients = draft.ingredients
  form.story = draft.story
  form.remark = draft.remark
  if (draft.image) {
    imageMeta.value = draft.image
    const cachedImage = getCachedDraftImage(DRAFT_KEY)
    if (cachedImage) {
      image.value = cachedImage
      previewUrl.value = URL.createObjectURL(cachedImage)
    }
  }
}

function persistDraft() {
  saveDraft(DRAFT_KEY, {
    name: form.name,
    summary: form.summary,
    ingredients: form.ingredients,
    story: form.story,
    remark: form.remark,
    image: imageMeta.value,
  })
}

watch(
  () => [form.name, form.summary, form.ingredients, form.story, form.remark, imageMeta.value],
  persistDraft,
)

onBeforeUnmount(() => {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
})

function selectImage(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  // 重置 input 使同一文件可再次触发 change；取消选择时不覆盖已选图片。
  input.value = ''
  if (!file) return

  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  image.value = file
  imageMeta.value = { name: file.name, type: file.type, size: file.size }
  previewUrl.value = URL.createObjectURL(file)
  cacheDraftImage(DRAFT_KEY, file)
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
    clearDraft(DRAFT_KEY)
    forgetDraftImage(DRAFT_KEY)
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
        <div class="cover-field">
          <span>{{ t('profile.replaceCover') }}</span>
          <div class="cover-row">
            <input
              ref="coverInput"
              class="hidden-file-input"
              type="file"
              accept="image/jpeg,image/png,image/webp"
              @change="selectImage"
            >
            <button type="button" class="cover-pick" @click="coverInput?.click()">
              {{ image ? t('upload.changeCover') : t('upload.pickCover') }}
            </button>
          </div>
          <img v-if="previewUrl" class="cover-preview" :src="previewUrl" :alt="t('profile.replaceCover')">
          <small v-if="imageMeta && !image" class="cover-warning">{{ t('upload.imageNeedsReselect') }}</small>
          <small>{{ food.imageUrl ? t('profile.keepCover') : t('upload.imageTip') }}</small>
        </div>

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
