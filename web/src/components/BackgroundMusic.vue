<template>
  <div id="MusicControl" ref="playerRef">
    <button v-if="!isPlayerVisible && currentMusic.src" class="reopen-btn" type="button" aria-label="打开音乐播放器" @click.stop="isPlayerVisible = true">♫</button>
    <div v-if="isPlayerVisible && currentMusic.src" class="control-bar">
      <div class="player-content">
        <div class="song-info"><div class="title">{{ currentMusic.name }}</div><div class="artist">{{ currentMusic.artist || '未知艺人' }}</div></div>
        <div class="progress-container" @click="seekAudio"><div class="progress-bar" :style="{width: progress + '%'}"/><div class="time-display">{{ formatTime(currentTime) }} / {{ formatTime(duration) }}</div></div>
        <div class="main-controls">
          <button class="playlist-btn" title="播放列表" @click="togglePlaylist">☷</button>
          <button title="上一首" @click="handlePrev">|◀</button>
          <button class="play-btn" @click="toggleMusic" :aria-label="isMusicPlaying ? '暂停' : '播放'"><span v-if="isMusicPlaying">Ⅱ</span><span v-else>▶</span></button>
          <button title="下一首" @click="handleNext">▶|</button>
          <button title="静音" @click="toggleMute">{{ isMuted ? '🔇' : '🔊' }}</button>
          <input v-model.number="volume" type="range" min="0" max="1" step="0.01" aria-label="音量" @input="handleVolumeChange" />
        </div>
        <div class="play-mode" role="group" aria-label="播放模式">
          <button type="button" :class="{ active: playMode === 'list' }" @click="setPlayMode('list')">列表播放</button>
          <button type="button" :class="{ active: playMode === 'random' }" @click="setPlayMode('random')">随机播放</button>
        </div>
        <div v-show="showPlaylist" class="playlist">
          <button v-for="(song, index) in musicList" :key="song.src" class="song-item" :class="{ playing: currentIndex === index }" @click="playSong(index)">
            <span>{{ index + 1 }}. {{ song.name }}</span><small>{{ song.artist }}</small>
          </button>
        </div>
      </div>
    </div>
    <audio ref="bgMusic" :src="currentMusic.src" @timeupdate="updateProgress" @loadedmetadata="updateDuration" @play="isMusicPlaying = true" @pause="isMusicPlaying = false" @ended="handleNext" />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
type Track = { name: string; artist: string; src: string }
type PlayMode = 'list' | 'random'
const musicList = ref<Track[]>([]), currentIndex = ref(0), currentTime = ref(0), duration = ref(0)
const volume = ref(0.35), isMuted = ref(false), isMusicPlaying = ref(false), isPlayerVisible = ref(true), showPlaylist = ref(false)
const playMode = ref<PlayMode>('list')
const bgMusic = ref<HTMLAudioElement | null>(null), playerRef = ref<HTMLElement | null>(null)
const currentMusic = computed(() => musicList.value[currentIndex.value] || { name: '', artist: '', src: '' })
const progress = computed(() => duration.value ? currentTime.value / duration.value * 100 : 0)
const formatTime = (s: number) => Number.isFinite(s) ? `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, '0')}` : '0:00'
async function playCurrent() { if (!bgMusic.value || !currentMusic.value.src) return; bgMusic.value.volume = volume.value; try { await bgMusic.value.play(); isMusicPlaying.value = true } catch { isMusicPlaying.value = false } }
function toggleMusic() { isMusicPlaying.value ? bgMusic.value?.pause() : playCurrent() }
async function playSong(index: number) {
  if (!musicList.value.length) return
  currentIndex.value = index
  currentTime.value = 0
  await nextTick()
  bgMusic.value?.load()
  await playCurrent()
}
function handlePrev() { if (musicList.value.length) playSong((currentIndex.value - 1 + musicList.value.length) % musicList.value.length) }
function getNextIndex() {
  if (playMode.value === 'list' || musicList.value.length < 2) return (currentIndex.value + 1) % musicList.value.length
  let nextIndex = currentIndex.value
  while (nextIndex === currentIndex.value) nextIndex = Math.floor(Math.random() * musicList.value.length)
  return nextIndex
}
function handleNext() { if (musicList.value.length) playSong(getNextIndex()) }
function handleAgentMusicSwitch(event: Event) {
  const query = (event as CustomEvent<{ query?: string }>).detail?.query?.trim().toLowerCase()
  if (!query) return
  const index = musicList.value.findIndex((track) =>
    track.name.toLowerCase().includes(query)
      || track.artist.toLowerCase().includes(query)
      || query.includes(track.name.toLowerCase()),
  )
  if (index >= 0) playSong(index)
}
function updateProgress(e: Event) { currentTime.value = (e.target as HTMLAudioElement).currentTime }
function updateDuration(e: Event) { duration.value = (e.target as HTMLAudioElement).duration }
function seekAudio(e: MouseEvent) { if (!bgMusic.value || !duration.value) return; const r = (e.currentTarget as HTMLElement).getBoundingClientRect(); bgMusic.value.currentTime = (e.clientX - r.left) / r.width * duration.value }
function handleVolumeChange() { if (bgMusic.value) bgMusic.value.volume = volume.value; isMuted.value = volume.value === 0 }
function toggleMute() { isMuted.value = !isMuted.value; if (bgMusic.value) bgMusic.value.muted = isMuted.value }
function togglePlaylist() { showPlaylist.value = !showPlaylist.value }
function setPlayMode(mode: PlayMode) { playMode.value = mode; localStorage.setItem('background-music-play-mode', mode) }
function outside(e: MouseEvent) { if (isPlayerVisible.value && playerRef.value && !playerRef.value.contains(e.target as Node)) { isPlayerVisible.value = false; showPlaylist.value = false } }
onMounted(async () => { if (window.matchMedia('(max-width: 600px), (max-height: 500px)').matches) isPlayerVisible.value = false; const savedMode = localStorage.getItem('background-music-play-mode'); if (savedMode === 'list' || savedMode === 'random') playMode.value = savedMode; try { musicList.value = await (await fetch('/audio/music-manifest.json')).json(); await playCurrent() } catch { musicList.value = [] }; window.addEventListener('pointerdown', playCurrent, { once: true }); window.addEventListener('agent:music-switch', handleAgentMusicSwitch); document.addEventListener('click', outside) })
onBeforeUnmount(() => { window.removeEventListener('pointerdown', playCurrent); window.removeEventListener('agent:music-switch', handleAgentMusicSwitch); document.removeEventListener('click', outside) })
</script>

<style scoped>
#MusicControl{position:fixed;right:20px;bottom:20px;z-index:1000}.control-bar{display:flex;gap:12px;background:rgba(255,255,255,.96);border-radius:24px;box-shadow:0 8px 32px #0002;padding:14px}.reopen-btn{border:0;background:rgba(255,255,255,.96);cursor:pointer;padding:10px 14px;border-radius:50%;font-size:20px;box-shadow:0 4px 16px #0003}.player-content{width:320px}.toggle-btn,button{border:0;background:none;cursor:pointer;padding:8px;border-radius:8px}.song-info{text-align:center}.title{font-size:1.1rem;font-weight:600}.artist,.time-display{font-size:.85rem;color:#718096}.progress-container{height:7px;margin:14px 0 24px;background:#ddd;border-radius:4px;cursor:pointer;position:relative}.progress-bar{height:100%;background:#4299e1;border-radius:4px}.time-display{position:absolute;top:10px;width:100%;text-align:center}.main-controls{display:flex;align-items:center;justify-content:center;gap:4px}.play-btn{font-size:20px;font-weight:700}.main-controls input{width:70px}.play-mode{display:flex;width:max-content;margin:8px auto 0;padding:2px;background:#edf2f7;border-radius:6px}.play-mode button{padding:4px 10px;color:#52606d;font-size:.8rem}.play-mode button.active{background:#fff;color:#1f2937;box-shadow:0 1px 3px #0002}.playlist{max-height:150px;overflow:auto;margin-top:10px}.song-item{display:flex;width:100%;justify-content:space-between;text-align:left}.song-item.playing{background:#e6f4ff}.song-item small{color:#718096;margin-left:8px}
@media (max-width:600px),(max-height:500px){#MusicControl{right:max(10px,env(safe-area-inset-right));bottom:max(10px,env(safe-area-inset-bottom))}.reopen-btn{width:48px;height:48px;padding:0}.control-bar{max-width:calc(100vw - 20px);max-height:min(54dvh,430px);padding:10px;overflow-y:auto;border-radius:18px}.player-content{width:min(320px,calc(100vw - 42px))}.song-info{padding:0 34px}.title{overflow:hidden;font-size:1rem;text-overflow:ellipsis;white-space:nowrap}.artist{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.main-controls{flex-wrap:wrap}.main-controls input{width:58px}.play-mode{max-width:100%}.playlist{max-height:24dvh}}
</style>
