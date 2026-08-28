<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { createFood, uploadImage } from '../api'
import {
  cacheDraftImage,
  clearDraft,
  forgetDraftImage,
  getCachedDraftImage,
  readDraft,
  saveDraft,
  type DraftImageMeta,
} from '../drafts'
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

// 坐标只能来自地图选点（通过 props 注入），表单自身不提供默认坐标，
// 避免未选点时带上无效经纬度直接创建。
type UploadForm = Omit<FoodCreatePayload, 'latitude' | 'longitude'> & {
  latitude?: number
  longitude?: number
}

const form = reactive<UploadForm>({
  name: '',
  regionId: 0,
  latitude: undefined,
  longitude: undefined,
  address: '',
  summary: '',
  story: '',
  ingredients: '',
  remark: '',
})

const image = ref<File>()
const imageMeta = ref<DraftImageMeta>()
const previewUrl = ref('')
const coverInput = ref<HTMLInputElement>()
const saving = ref(false)
const error = ref('')
const regionDrawerOpen = ref(false)
const { t } = useI18n()

const selectedRegion = computed(() => props.regions.find((region) => region.id === form.regionId))
const pickedCoordinatesUnchanged = computed(() => {
  return props.latitude != null
    && props.longitude != null
    && form.latitude != null
    && form.longitude != null
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

// 草稿缓存：文本字段在误关弹窗后保留；坐标/地区/地址永远跟随地图选点（props），不参与草稿。
const DRAFT_KEY = 'foodUpload'

interface UploadDraft {
  name: string
  summary: string
  ingredients: string
  story: string
  remark: string
  image?: DraftImageMeta
}

const draft = readDraft<UploadDraft>(DRAFT_KEY)
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

function selectRegion(regionId?: number) {
  if (regionId != null) {
    form.regionId = regionId
    error.value = ''
  }
}

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

  // 坐标必须来自地图选点，禁止带着默认值直接创建。
  if (form.latitude == null || form.longitude == null
      || !Number.isFinite(form.latitude) || !Number.isFinite(form.longitude)) {
    error.value = t('upload.coordinateRequired')
    return
  }

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

    const food = await createFood({
      ...form,
      latitude: form.latitude as number,
      longitude: form.longitude as number,
    })
    if (food.reviewStatus === 'PENDING') {
      window.alert(t('upload.pendingSuccess'))
    }
    clearDraft(DRAFT_KEY)
    forgetDraftImage(DRAFT_KEY)
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
        <div class="cover-field">
          <span>{{ t('upload.cover') }}</span>
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
          <img v-if="previewUrl" class="cover-preview" :src="previewUrl" :alt="t('upload.cover')">
          <small v-if="imageMeta && !image" class="cover-warning">{{ t('upload.imageNeedsReselect') }}</small>
          <small>{{ t('upload.imageTip') }}</small>
        </div>

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
