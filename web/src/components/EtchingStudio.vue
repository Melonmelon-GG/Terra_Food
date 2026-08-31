<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { createEtching, updateEtching } from '../api'
import type { EtchingDesign } from '../types'
import HexEtching from './HexEtching.vue'

const props = defineProps<{ design?: EtchingDesign }>()
const emit = defineEmits<{ close: []; saved: [design: EtchingDesign] }>()
const { t } = useI18n()
const CELL_COUNT = 169
const empty = () => Array<string>(CELL_COUNT).fill('')
const normalizeLayer = (colors?: string[]) => [...(colors || []), ...empty()].slice(0, CELL_COUNT)
const name = ref(props.design?.name || '')
const layerOne = ref(normalizeLayer(props.design?.layerOne))
const selectedColor = ref('#9a352e')
const saving = ref(false)
const error = ref('')
const palette = ['#9A352E', '#D46A4C', '#E2A84B', '#F2D787', '#6E8B57', '#397B78', '#4B6F9A', '#65558F', '#9A6680', '#3C302A', '#F5E9D2', '#FFFFFF']
const paintedCount = computed(() => new Set(layerOne.value.filter(Boolean)).size)

function paint(index: number) {
  layerOne.value[index] = selectedColor.value
}
function clearLayer() { layerOne.value = empty() }
async function save() {
  if (!name.value.trim()) { error.value = t('etching.nameRequired'); return }
  saving.value = true; error.value = ''
  try {
    const payload = { name: name.value.trim(), layerOne: layerOne.value }
    const saved = props.design ? await updateEtching(props.design.id, payload) : await createEtching(payload)
    emit('saved', saved)
  } catch { error.value = t('etching.saveError') } finally { saving.value = false }
}
</script>

<template>
  <div class="etching-studio-mask" @click.self="emit('close')">
    <section class="etching-studio" role="dialog" aria-modal="true" :aria-label="t('etching.studioTitle')">
      <header><div><small>{{ t('etching.eyebrow') }}</small><h2>{{ design ? t('etching.editTitle') : t('etching.createTitle') }}</h2></div><button type="button" @click="emit('close')">×</button></header>
      <div class="etching-studio-layout">
        <div class="etching-canvas-wrap">
          <HexEtching :layer-one="layerOne" editable @paint="paint" />
          <small>{{ t('etching.dragHint') }}</small>
        </div>
        <div class="etching-tools">
          <label>{{ t('etching.name') }}<input v-model="name" maxlength="50"></label>
          <div class="etching-palette"><button v-for="color in palette" :key="color" type="button" :style="{ background: color }" :class="{ active: selectedColor === color }" :aria-label="color" @click="selectedColor = color" /><label class="custom-color"><input v-model="selectedColor" type="color"><span>＋</span></label><button type="button" class="eraser" :class="{ active: selectedColor === '' }" @click="selectedColor = ''">{{ t('etching.eraser') }}</button></div>
          <button type="button" class="clear-layer" @click="clearLayer">{{ t('etching.clearLayer') }}</button>
          <p>{{ t('etching.colorCount', { count: paintedCount }) }}</p><p v-if="error" class="form-error">{{ error }}</p>
        </div>
      </div>
      <footer><button type="button" @click="emit('close')">{{ t('common.cancel') }}</button><button type="button" :disabled="saving" @click="save">{{ saving ? t('etching.saving') : t('etching.save') }}</button></footer>
    </section>
  </div>
</template>

<style scoped>
.etching-studio-mask{position:fixed;inset:0;z-index:80;display:grid;padding:24px;place-items:center;background:#261b17b8}.etching-studio{width:min(900px,100%);max-height:calc(100vh - 32px);overflow:auto;background:#fffaf0;border:1px solid #b89878;box-shadow:0 24px 70px #160d0a66}.etching-studio header,.etching-studio footer{display:flex;align-items:center;justify-content:space-between;padding:18px 24px;border-bottom:1px solid #dcc9ae}.etching-studio header h2{margin:4px 0 0}.etching-studio header>button{font-size:28px;background:none;border:0;cursor:pointer}.etching-studio-layout{display:grid;grid-template-columns:minmax(300px,1.1fr) minmax(260px,.9fr);gap:24px;padding:24px}.etching-canvas-wrap{display:grid;padding:16px;place-items:center;background:radial-gradient(circle,#fff,#e9d7bd);border:1px solid #d2bfa3}.etching-canvas-wrap small{color:#806b5a}.etching-tools{display:grid;gap:14px;align-content:start}.etching-tools>label{display:grid;gap:6px}.etching-tools input[type=text],.etching-tools label>input:not([type]){padding:10px}.layer-tabs{display:grid;grid-template-columns:1fr 1fr}.layer-tabs button,.clear-layer{padding:9px;background:#f4ead9;border:1px solid #cdb79a}.layer-tabs button.active{color:#fff;background:#8f3029}.layer-visibility{display:flex;gap:14px;font-size:12px}.etching-palette{display:flex;flex-wrap:wrap;gap:7px}.etching-palette>button,.custom-color{display:grid;width:32px;height:32px;padding:0;place-items:center;border:2px solid #d5c2aa;cursor:pointer}.etching-palette>button.active{outline:2px solid #8f3029}.custom-color{position:relative;overflow:hidden}.custom-color input{position:absolute;inset:-8px;width:48px;height:48px;opacity:0}.custom-color span{pointer-events:none}.etching-palette .eraser{width:auto;padding:0 8px;background:#fff}.etching-studio footer{justify-content:flex-end;gap:10px;border-top:1px solid #dcc9ae;border-bottom:0}.etching-studio footer button{padding:9px 18px}.etching-studio footer button:last-child{color:#fff;background:#8f3029;border-color:#8f3029}@media(max-width:700px){.etching-studio-mask{padding:8px}.etching-studio-layout{grid-template-columns:1fr;padding:14px}.etching-canvas-wrap{padding:6px}.etching-studio header,.etching-studio footer{padding:14px}}
</style>
