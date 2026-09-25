<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { createFood, uploadImage } from '../api'
import { apiErrorMessage } from '../apiError'
import { useAuth } from '../auth'
import { requestBrowserLocation, validCoordinate } from '../location'
import FoodTagPicker from './FoodTagPicker.vue'
import {
  cacheDraftImage,
  clearDraft,
  forgetDraftImage,
  getCachedDraftImage,
  readDraft,
  saveDraft,
  type DraftImageMeta,
} from '../drafts'
import type { Food, FoodCreatePayload, MapFocus, Region } from '../types'

const props = defineProps<{
  regions: Region[]
  latitude?: number
  longitude?: number
  regionId?: number
  address?: string
  province?: string
  city?: string
}>()

const emit = defineEmits<{
  close: []
  pickOnMap: [focus?: MapFocus]
  saved: [food: Food]
}>()

// No default coordinates: explicit map selection, browser location or manual entry is required.
type UploadForm = Omit<FoodCreatePayload, 'latitude' | 'longitude'> & {
  latitude?: number
  longitude?: number
}

const form = reactive<UploadForm>({
  name: '',
  regionId: props.regionId,
  province: props.province || '',
  city: props.city || '',
  latitude: props.latitude == null ? undefined : Number(props.latitude.toFixed(7)),
  longitude: props.longitude == null ? undefined : Number(props.longitude.toFixed(7)),
  address: props.address || '',
  summary: '',
  story: '',
  ingredients: '',
  remark: '',
  tagIds: [],
})

const image = ref<File>()
const imageMeta = ref<DraftImageMeta>()
const previewUrl = ref('')
const coverInput = ref<HTMLInputElement>()
const saving = ref(false)
const error = ref('')
const stage = ref<'idle' | 'uploading' | 'saving'>('idle')
const { t } = useI18n()
const auth = useAuth()
const accountId = auth.currentUser.value?.id
const DRAFT_KEY = `foodUpload.v2.${accountId}`
const DRAFT_TTL_MS = 7 * 24 * 60 * 60 * 1000
let uploadController: AbortController | undefined
let active = true

// Snapshot the chosen location. Late parent geocoding must not replace edits.
const locating = ref(false)
const locationMessage = ref('')
const cityId = ref('')
const mappedRegions = computed(() => props.regions.filter(region => validCoordinate(region.centerLatitude, region.centerLongitude)))
function findCity() {
  const region = mappedRegions.value.find(item => String(item.id) === cityId.value)
  if (!region) return
  stopLocation()
  // City centres are navigation hints, never submitted food coordinates.
  emit('pickOnMap', { latitude: region.centerLatitude!, longitude: region.centerLongitude!, zoom: 13 })
}
let cancelLocation: (() => void) | undefined
function stopLocation() {
  cancelLocation?.()
  locating.value = false
}
function useCurrentLocation() {
  stopLocation()
  locating.value = true
  locationMessage.value = ''
  const revision = auth.getSessionRevision()
  cancelLocation = requestBrowserLocation(coordinate => {
    locating.value = false
    if (!active || saving.value || revision !== auth.getSessionRevision() || accountId !== auth.currentUser.value?.id) return
    form.latitude = Number(coordinate.latitude.toFixed(7))
    form.longitude = Number(coordinate.longitude.toFixed(7))
    form.regionId = undefined
    form.province = ''; form.city = ''; form.address = ''
    locationMessage.value = 'location.verifyPosition'
  }, key => { locating.value = false; locationMessage.value = key })
}

// Explicit map selection wins; otherwise restore this account’s previously entered location.
interface UploadDraft {
  name: string
  summary: string
  ingredients: string
  story: string
  remark: string
  image?: DraftImageMeta
  imageUrl?: string
  idempotencyKey: string
  tagIds?: number[]
  expiresAt: number
  location?: { latitude?: number; longitude?: number; regionId?: number; province?: string; city?: string; address?: string }
}

let draft = readDraft<UploadDraft>(DRAFT_KEY)
if (draft && (!Number.isFinite(draft.expiresAt) || draft.expiresAt <= Date.now())) {
  clearDraft(DRAFT_KEY)
  draft = undefined
}
if (props.latitude == null && props.longitude == null && draft?.location
    && validCoordinate(draft.location.latitude, draft.location.longitude)) {
  const saved = draft.location
  form.latitude = saved.latitude; form.longitude = saved.longitude
  form.regionId = saved.regionId; form.province = saved.province || ''
  form.city = saved.city || ''; form.address = saved.address || ''
}
const idempotencyKey = ref(draft?.idempotencyKey || crypto.randomUUID())
const currentLocation = () => ({ latitude: form.latitude, longitude: form.longitude, regionId: form.regionId, province: form.province, city: form.city, address: form.address })
if (draft && JSON.stringify(draft.location) !== JSON.stringify(currentLocation())) idempotencyKey.value = crypto.randomUUID()
if (draft) {
  form.name = draft.name
  form.summary = draft.summary
  form.ingredients = draft.ingredients
  form.story = draft.story
  form.remark = draft.remark
  form.imageUrl = draft.imageUrl
  form.tagIds = draft.tagIds || []
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
    imageUrl: form.imageUrl,
    idempotencyKey: idempotencyKey.value,
    tagIds: form.tagIds,
    expiresAt: Date.now() + DRAFT_TTL_MS,
    location: currentLocation(),
  })
}

watch(
  () => [{ ...form, tagIds: [...(form.tagIds || [])] }, imageMeta.value],
  () => {
    idempotencyKey.value = crypto.randomUUID()
    persistDraft()
  },
)

onBeforeUnmount(() => {
  active = false
  stopLocation()
  uploadController?.abort()
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
})

function selectImage(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  // 重置 input 使同一文件可再次触发 change；取消选择时不覆盖已选图片。
  input.value = ''
  if (!file) return

  const allowedTypes = new Set(['image/jpeg', 'image/png', 'image/webp'])
  if (file.size > 5 * 1024 * 1024) {
    error.value = t('upload.imageTooLarge')
    return
  }
  if (!allowedTypes.has(file.type)) {
    error.value = t('upload.imageInvalidType')
    return
  }

  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  image.value = file
  form.imageUrl = undefined
  idempotencyKey.value = crypto.randomUUID()
  imageMeta.value = { name: file.name, type: file.type, size: file.size }
  previewUrl.value = URL.createObjectURL(file)
  cacheDraftImage(DRAFT_KEY, file)
}

async function submit() {
  if (saving.value) return
  error.value = ''

  if (!validCoordinate(form.latitude, form.longitude)) {
    error.value = t('upload.coordinateRequired')
    return
  }

  stopLocation()
  saving.value = true
  try {
    // 图片与菜品信息分两步提交：先取得资源 URL，再保存稳定的业务记录。
    if (image.value && !form.imageUrl) {
      stage.value = 'uploading'
      uploadController = new AbortController()
      form.imageUrl = await uploadImage(image.value, uploadController.signal)
      uploadController = undefined
      persistDraft()
    }

    stage.value = 'saving'
    const submissionKey = idempotencyKey.value
    const payload = {
      ...form,
      latitude: form.latitude as number,
      longitude: form.longitude as number,
    }
    const food = await createFood(payload, submissionKey)
    if (food.reviewStatus === 'PENDING') {
      window.alert(t('upload.pendingSuccess'))
    }
    if (submissionKey === idempotencyKey.value) {
      clearDraft(DRAFT_KEY)
      forgetDraftImage(DRAFT_KEY)
    }
    emit('saved', food)
  } catch (requestError) {
    error.value = apiErrorMessage(requestError, stage.value === 'uploading'
      ? t('upload.imageUploadError')
      : t('upload.saveError'))
  } finally {
    saving.value = false
    stage.value = 'idle'
    uploadController = undefined
  }
}

function cancelUpload() {
  uploadController?.abort()
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
        <p v-if="stage !== 'idle'" class="form-status" role="status">
          {{ stage === 'uploading' ? t('upload.uploadingImage') : t('upload.savingFood') }}
          <button v-if="stage === 'uploading'" type="button" @click="cancelUpload">{{ t('upload.cancelUpload') }}</button>
        </p>
        <fieldset class="submit-snapshot" :disabled="saving">
        <div class="location-entry" role="group" :aria-label="t('location.title')">
          <p>{{ t('location.entryHelp') }}</p>
          <button type="button" class="secondary-button" :disabled="locating" @click="useCurrentLocation">{{ t('location.useCurrent') }}</button>
          <button type="button" class="secondary-button" @click="stopLocation(); emit('pickOnMap')">{{ t('location.pickMap') }}</button>
          <label v-if="mappedRegions.length">
            {{ t('location.cityNavigation') }}
            <select v-model="cityId" @change="findCity">
              <option value="">{{ t('location.chooseCity') }}</option>
              <option v-for="region in mappedRegions" :key="region.id" :value="String(region.id)">{{ region.province }} · {{ region.name }}</option>
            </select>
          </label>
          <p v-if="locating" role="status">{{ t('home.geolocationLoading') }} <button type="button" @click="stopLocation">{{ t('common.cancel') }}</button></p>
          <p v-else-if="locationMessage" role="status">{{ t(locationMessage) }}</p>
        </div>
        <div class="form-grid">
          <label>
            {{ t('upload.name') }}
            <input v-model="form.name" required maxlength="100">
          </label>
          <label>
            {{ t('upload.cityLabel') }}
            <input v-model.trim="form.province" @input="stopLocation" maxlength="100" :placeholder="t('upload.provincePlaceholder')">
            <input v-model.trim="form.city" @input="stopLocation" maxlength="100" :placeholder="t('upload.cityPlaceholder')">
          </label>
          <label>
            {{ t('upload.latitude') }}
            <input v-model.number="form.latitude" @input="stopLocation" type="number" min="-90" max="90" step="0.0000001" required>
          </label>
          <label>
            {{ t('upload.longitude') }}
            <input v-model.number="form.longitude" @input="stopLocation" type="number" min="-180" max="180" step="0.0000001" required>
          </label>
        </div>

        <p class="coordinate-tip">{{ t('upload.coordinateTip') }}</p>

        <label>
          {{ t('upload.address') }}
          <input v-model.trim="form.address" @input="stopLocation" maxlength="500" :placeholder="t('upload.addressPlaceholder')">
        </label>

        <label>
          {{ t('upload.summary') }}
          <textarea v-model="form.summary" required maxlength="1000" rows="2"></textarea>
        </label>
        <label>
          {{ t('upload.ingredients') }}
          <input v-model="form.ingredients" required maxlength="500">
        </label>
        <FoodTagPicker v-model="form.tagIds" :disabled="saving" />
        <label>
          {{ t('upload.story') }}
          <textarea v-model="form.story" required rows="4"></textarea>
        </label>
        <label>
          {{ t('upload.remark') }}
          <textarea v-model="form.remark" maxlength="1000" rows="3" :placeholder="t('upload.remarkPlaceholder')"></textarea>
        </label>
        <div class="cover-field">
          <span>{{ t('upload.cover') }}</span>
          <div class="cover-row">
            <input
              ref="coverInput"
              class="hidden-file-input"
              type="file"
              accept="image/jpeg,image/png,image/webp"
              :disabled="saving"
              @change="selectImage"
            >
            <button type="button" class="cover-pick" :disabled="saving" @click="coverInput?.click()">
              {{ image ? t('upload.changeCover') : t('upload.pickCover') }}
            </button>
          </div>
          <img v-if="previewUrl" class="cover-preview" :src="previewUrl" :alt="t('upload.cover')">
          <small v-if="imageMeta && !image" class="cover-warning">{{ t('upload.imageNeedsReselect') }}</small>
          <small>{{ t('upload.imageTip') }}</small>
        </div>
        </fieldset>

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
