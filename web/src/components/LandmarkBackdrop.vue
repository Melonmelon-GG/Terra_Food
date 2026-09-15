<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { defaultLandmark, resolveLandmark } from '../landmarks'
const props = defineProps<{ province?: string }>()
const artwork = computed(() => resolveLandmark(props.province))
const failed = ref(false)
const hidden = ref(false)
watch(artwork, () => { failed.value = false; hidden.value = false })
function fallback() {
  if (failed.value || artwork.value.image === defaultLandmark.image) hidden.value = true
  else failed.value = true
}
</script>
<template>
  <img v-if="!hidden" class="archive-landmark" :src="failed ? defaultLandmark.image : artwork.image"
    :style="{ objectPosition: artwork.position }" alt="" aria-hidden="true" @error="fallback">
</template>
<style scoped>
.archive-landmark { position:absolute; inset:0; width:100%; height:100%; object-fit:cover; opacity:.12; pointer-events:none; mix-blend-mode:multiply; }
:global(html[data-theme='dark']) .archive-landmark { opacity:.11; mix-blend-mode:screen; filter:invert(1); }
</style>
