<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { createFoodTag, getFoodTags } from '../api'
import { apiErrorMessage } from '../apiError'
import type { FoodTag } from '../types'

const props = defineProps<{ modelValue?: number[]; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: number[]] }>()
const { t } = useI18n()
const tags = ref<FoodTag[]>([])
const loading = ref(true)
const error = ref('')
const draftNames = ref<Record<FoodTag['type'], string>>({ TASTE: '', INGREDIENT: '', CUISINE: '' })
const creating = ref<FoodTag['type']>()
const types: FoodTag['type'][] = ['TASTE', 'INGREDIENT', 'CUISINE']
const selected = computed(() => new Set(props.modelValue ?? []))

function tagsFor(type: FoodTag['type']) { return tags.value.filter((tag) => tag.type === type) }
function toggle(tag: FoodTag) {
  if (props.disabled || loading.value) return
  const next = new Set(props.modelValue ?? [])
  if (next.has(tag.id)) next.delete(tag.id)
  else {
    if (tagsFor(tag.type).filter((item) => next.has(item.id)).length >= 10) {
      error.value = t('tagPicker.typeLimit')
      return
    }
    next.add(tag.id)
  }
  emit('update:modelValue', [...next])
}

async function add(type: FoodTag['type']) {
  if (props.disabled || loading.value || creating.value) return
  const name = draftNames.value[type].trim()
  if (!name) return
  creating.value = type
  error.value = ''
  try {
    const tag = await createFoodTag({ type, name })
    tags.value.push(tag)
    draftNames.value[type] = ''
    toggle(tag)
  } catch (reason) { error.value = apiErrorMessage(reason, t('tagPicker.createFailed')) }
  finally { creating.value = undefined }
}

onMounted(async () => {
  try { tags.value = await getFoodTags() }
  catch (reason) { error.value = apiErrorMessage(reason, t('tagPicker.loadFailed')) }
  finally { loading.value = false }
})
</script>

<template>
  <section class="food-tag-picker">
    <header><b>{{ t('tagPicker.title') }}</b><small>{{ t('tagPicker.hint') }}</small></header>
    <p v-if="loading">{{ t('common.loading') }}</p>
    <div v-for="type in types" :key="type" class="tag-kind">
      <strong>{{ t(`home.tagType${type}`) }}</strong>
      <div class="tag-options">
        <button v-for="tag in tagsFor(type)" :key="tag.id" type="button"
          :disabled="disabled || loading" :class="{ selected: selected.has(tag.id), pending: tag.status === 'PENDING' }" @click="toggle(tag)">
          {{ tag.name }}<small v-if="tag.status === 'PENDING'">{{ t('tagPicker.pending') }}</small>
        </button>
      </div>
      <div class="tag-create">
        <input v-model="draftNames[type]" maxlength="30" :disabled="disabled || loading"
          :placeholder="t('tagPicker.newPlaceholder')" @keydown.enter.prevent.stop="add(type)">
        <button type="button" :disabled="disabled || loading || creating === type" @click="add(type)">{{ t('tagPicker.create') }}</button>
      </div>
    </div>
    <p v-if="error" class="form-error">{{ error }}</p>
  </section>
</template>

<style scoped>
.food-tag-picker{display:grid;gap:12px;padding:12px;border:1px solid var(--border-paper);border-radius:12px;background:var(--surface-soft)}
header{display:flex;gap:8px;align-items:baseline} header small{color:var(--muted)}
.tag-kind{display:grid;gap:6px}.tag-options{display:flex;flex-wrap:wrap;gap:6px}.tag-options button{padding:5px 9px;border:1px solid var(--border-paper);border-radius:999px;background:var(--surface-strong);color:var(--ink)}
.tag-options button.selected{background:var(--seal);color:#fff}.tag-options button.pending{border-style:dashed}.tag-options small{margin-left:4px}
.tag-create{display:flex;gap:6px}.tag-create input{flex:1}.tag-create button{padding:5px 10px}
</style>
