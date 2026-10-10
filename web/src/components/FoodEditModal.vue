<script setup lang="ts">
import axios from 'axios'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { getFoodTagsForFood, updateMyFood, uploadImage } from '../api'
import { useAuth } from '../auth'
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
import FoodTagPicker from './FoodTagPicker.vue'

const props = defineProps<{ food: Food; regions: Region[] }>()
const emit = defineEmits<{ close: []; saved: [food: Food] }>()
const { t } = useI18n()
const auth = useAuth()

const form = reactive<FoodUpdatePayload>({
  name: props.food.name,
  regionId: props.food.region.id || undefined,
  latitude: props.food.latitude,
  longitude: props.food.longitude,
  address: props.food.address || '',
  summary: props.food.summary,
  story: props.food.story,
  ingredients: props.food.ingredients,
  imageUrl: props.food.imageUrl,
  remark: props.food.remark || '',
  tagIds: undefined,
})
const image = ref<File>()
const imageMeta = ref<DraftImageMeta>()
const previewUrl = ref('')
const coverInput = ref<HTMLInputElement>()
const saving = ref(false)
const error = ref('')
const draftStored = ref(true)
const regionDrawerOpen = ref(false)
const imageMode = ref<'keep' | 'replace'>('keep')
const tagsReady = ref(false)
const tagsError = ref(false)
const accountId = auth.currentUser.value?.id
let active = true
let uploadController: AbortController | undefined
const selectedRegion = computed(() => props.regions.find((region) => region.id === form.regionId))

// 草稿缓存：按菜品区分，误关弹窗再打开不丢失填写内容；地区/坐标始终以菜品档案为准。
const DRAFT_KEY = `foodEdit.v2:${auth.currentUser.value?.id}:${props.food.id}`

interface EditDraft {
  name: string
  summary: string
  ingredients: string
  story: string
  remark: string
  image?: DraftImageMeta
  imageUrl?: string
  imageMode?: 'keep' | 'replace'
  tagIds?: number[]
  expiresAt: number
}

let draft = readDraft<EditDraft>(DRAFT_KEY)
if (draft && (!Number.isFinite(draft.expiresAt) || draft.expiresAt <= Date.now())) {
  clearDraft(DRAFT_KEY)
  draft = undefined
}
if (draft) {
  form.name = draft.name
  form.summary = draft.summary
  form.ingredients = draft.ingredients
  form.story = draft.story
  form.remark = draft.remark
  imageMode.value = draft.imageMode || (draft.image ? 'replace' : 'keep')
  form.imageUrl = imageMode.value === 'replace' ? draft.imageUrl : (draft.imageUrl ?? form.imageUrl)
  form.tagIds = draft.tagIds
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
  draftStored.value = saveDraft(DRAFT_KEY, {
    name: form.name,
    summary: form.summary,
    ingredients: form.ingredients,
    story: form.story,
    remark: form.remark,
    image: imageMeta.value,
    imageUrl: form.imageUrl,
    imageMode: imageMode.value,
    tagIds: form.tagIds,
    expiresAt: Date.now() + 7 * 24 * 60 * 60 * 1000,
  })
}

watch(
  () => [{ ...form }, imageMeta.value, imageMode.value],
  persistDraft,
)

onBeforeUnmount(() => {
  active = false
  uploadController?.abort()
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
})

function selectImage(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  // 重置 input 使同一文件可再次触发 change；取消选择时不覆盖已选图片。
  input.value = ''
  if (!file) return

  if (file.size > 5 * 1024 * 1024) { error.value = t('upload.imageTooLarge'); return }
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) { error.value = t('upload.imageInvalidType'); return }

  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  image.value = file
  imageMode.value = 'replace'
  form.imageUrl = undefined
  imageMeta.value = { name: file.name, type: file.type, size: file.size }
  previewUrl.value = URL.createObjectURL(file)
  cacheDraftImage(DRAFT_KEY, file)
}

async function submit() {
  if (saving.value) return
  error.value = ''
  if (imageMode.value === 'replace' && !image.value && !form.imageUrl) { error.value = t('upload.imageNeedsReselect'); return }
  const revision = auth.getSessionRevision()
  const stillCurrent = () => active && revision === auth.getSessionRevision() && accountId === auth.currentUser.value?.id
  const payload = { ...form, tagIds: form.tagIds?.slice() }
  const selectedImage = image.value
  saving.value = true
  try {
    if (selectedImage && !payload.imageUrl) {
      uploadController = new AbortController()
      payload.imageUrl = await uploadImage(selectedImage, uploadController.signal)
      if (!stillCurrent()) return
      form.imageUrl = payload.imageUrl
      persistDraft()
    }
    if (!stillCurrent()) return
    const updated = await updateMyFood(props.food.id, payload)
    if (!stillCurrent()) return
    clearDraft(DRAFT_KEY)
    forgetDraftImage(DRAFT_KEY)
    emit('saved', updated)
  } catch (requestError) {
    if (!stillCurrent()) return
    error.value = axios.isAxiosError(requestError)
      ? requestError.response?.data?.message || t('profile.updateError')
      : t('profile.updateError')
  } finally {
    saving.value = false
  }
}

async function loadTags() {
  tagsError.value = false
  if (draft?.tagIds !== undefined) { tagsReady.value = true; return }
  try {
    const tags = await getFoodTagsForFood(props.food.id)
    if (!active) return
    if (saving.value) { tagsError.value = true; return }
    form.tagIds = tags.map((tag) => tag.id)
    tagsReady.value = true
  } catch { if (active) tagsError.value = true }
}
onMounted(loadTags)

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
        <fieldset class="submit-snapshot" :disabled="saving">
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
        <FoodTagPicker v-model="form.tagIds" :disabled="saving || !tagsReady" />
        <p v-if="tagsError" class="form-error">{{ t('tagPicker.loadFailed') }} <button type="button" :disabled="saving" @click="loadTags">{{ t('share.retry') }}</button></p>
        <label>
          {{ t('upload.story') }}
          <textarea v-model.trim="form.story" required maxlength="10000" rows="4"></textarea>
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
          <img v-if="previewUrl || form.imageUrl" class="cover-preview" :src="previewUrl || form.imageUrl" :alt="t('profile.replaceCover')">
          <small v-if="imageMeta && !image && !form.imageUrl" class="cover-warning">{{ t('upload.imageNeedsReselect') }}</small>
          <small>{{ food.imageUrl ? t('profile.keepCover') : t('upload.imageTip') }}</small>
        </div>
        </fieldset>

        <p v-if="!draftStored" class="form-error" role="status">{{ t('audit.draftNotSaved') }}</p>
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
