<script setup lang="ts">
import { reactive, ref, watch } from 'vue'

import { createFood, uploadImage } from '../api'
import type { Food, FoodCreatePayload, Region } from '../types'

const props = defineProps<{
  regions: Region[]
  latitude?: number
  longitude?: number
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

function selectImage(event: Event) {
  const input = event.target as HTMLInputElement
  image.value = input.files?.[0]
}

async function submit() {
  error.value = ''

  if (!form.regionId) {
    error.value = '请选择所属地区。'
    return
  }

  saving.value = true
  try {
    if (image.value) {
      form.imageUrl = await uploadImage(image.value)
    }

    const food = await createFood(form)
    emit('saved', food)
  } catch {
    error.value = '保存失败，请检查填写内容和后端服务。'
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
          <small>NEW DELICACY</small>
          <h2>收录新的珍馐</h2>
        </div>
        <button class="icon-button" @click="emit('close')">×</button>
      </div>

      <form @submit.prevent="submit">
        <div class="form-grid">
          <label>
            菜品名称
            <input v-model="form.name" required maxlength="100">
          </label>
          <label>
            所属地区
            <select v-model.number="form.regionId" required>
              <option :value="0" disabled>请选择</option>
              <option v-for="region in regions" :key="region.id" :value="region.id">
                {{ region.province }} · {{ region.name }}
              </option>
            </select>
          </label>
          <label>
            纬度
            <input v-model.number="form.latitude" type="number" min="-90" max="90" step="0.0000001" required>
          </label>
          <label>
            经度
            <input v-model.number="form.longitude" type="number" min="-180" max="180" step="0.0000001" required>
          </label>
        </div>

        <p class="coordinate-tip">可先关闭表单并点击地图选取位置，再次打开时会自动带入坐标。</p>

        <label>
          一句话简介
          <textarea v-model="form.summary" required maxlength="1000" rows="2"></textarea>
        </label>
        <label>
          主要食材
          <input v-model="form.ingredients" required maxlength="500">
        </label>
        <label>
          珍馐掌故
          <textarea v-model="form.story" required rows="4"></textarea>
        </label>
        <label>
          封面图片
          <input type="file" accept="image/jpeg,image/png,image/webp" @change="selectImage">
          <small>支持 JPG、PNG、WebP，最大 5MB</small>
        </label>

        <p v-if="error" class="form-error">{{ error }}</p>

        <div class="modal-actions">
          <button type="button" class="secondary-button" @click="emit('close')">取消</button>
          <button class="primary-button" :disabled="saving">
            {{ saving ? '正在收录……' : '收入珍馐志' }}
          </button>
        </div>
      </form>
    </section>
  </div>
</template>
