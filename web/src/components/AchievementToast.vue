<script setup lang="ts">
import { computed, onUnmounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { useAchievementNotifications } from '../achievement'

const { t } = useI18n()
const achievementNotifications = useAchievementNotifications()
const activeAchievement = computed(() => achievementNotifications.notifications.value[0])
let dismissTimer: number | undefined

async function dismiss() {
  const achievement = activeAchievement.value
  if (!achievement) return
  window.clearTimeout(dismissTimer)
  await achievementNotifications.dismiss(achievement.id)
}

watch(activeAchievement, (achievement) => {
  window.clearTimeout(dismissTimer)
  if (achievement) {
    dismissTimer = window.setTimeout(dismiss, 7000)
  }
}, { immediate: true })

onUnmounted(() => window.clearTimeout(dismissTimer))
</script>

<template>
  <Teleport to="body">
    <Transition name="achievement-toast">
      <aside
        v-if="activeAchievement"
        class="achievement-toast"
        role="status"
        aria-live="polite"
      >
        <button
          type="button"
          class="achievement-toast-close"
          :aria-label="t('achievement.close')"
          @click="dismiss"
        >
          ×
        </button>
        <div class="achievement-toast-image">
          <img :src="activeAchievement.imageUrl" :alt="activeAchievement.name">
        </div>
        <div class="achievement-toast-copy">
          <div class="achievement-toast-name">
            <small>{{ t('achievement.unlocked') }}</small>
            <h3>{{ activeAchievement.name }}</h3>
          </div>
          <p>{{ activeAchievement.description }}</p>
        </div>
      </aside>
    </Transition>
  </Teleport>
</template>

<style scoped>
.achievement-toast {
  position: fixed;
  top: 92px;
  right: 24px;
  z-index: 3000;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 2fr);
  width: min(480px, calc(100vw - 48px));
  min-height: 156px;
  overflow: hidden;
  color: #3e2b23;
  background:
    radial-gradient(circle at 85% 15%, #a7493324, transparent 34%),
    #fffaf0;
  border: 1px solid #b99677;
  box-shadow: 0 22px 60px #2e17104d;
}

.achievement-toast-image {
  display: grid;
  min-width: 0;
  padding: 12px;
  place-items: center;
  background: #eee2d0;
  border-right: 1px solid #cdb99f;
}

.achievement-toast-image img {
  width: 100%;
  height: 100%;
  max-height: 132px;
  object-fit: contain;
  mix-blend-mode: multiply;
}

.achievement-toast-copy {
  display: grid;
  grid-template-rows: 1fr 1fr;
  min-width: 0;
}

.achievement-toast-name {
  display: grid;
  align-content: end;
  padding: 20px 28px 12px 20px;
  border-bottom: 1px solid #d8c8b2;
}

.achievement-toast-name small {
  color: #9a352e;
  font-size: 9px;
  letter-spacing: 3px;
}

.achievement-toast-name h3 {
  margin: 5px 0 0;
  font-size: 23px;
}

.achievement-toast-copy > p {
  display: grid;
  align-content: start;
  margin: 0;
  padding: 12px 20px 18px;
  color: #786153;
  font-size: 12px;
  line-height: 1.7;
}

.achievement-toast-close {
  position: absolute;
  top: 6px;
  right: 8px;
  z-index: 1;
  width: 28px;
  height: 28px;
  color: #80695b;
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.achievement-toast-enter-active,
.achievement-toast-leave-active {
  transition: opacity 240ms ease, transform 240ms ease;
}

.achievement-toast-enter-from,
.achievement-toast-leave-to {
  opacity: 0;
  transform: translateX(28px);
}

@media (max-width: 700px) {
  .achievement-toast {
    top: 78px;
    right: 12px;
    width: calc(100vw - 24px);
    min-height: 136px;
  }

  .achievement-toast-image {
    padding: 8px;
  }

  .achievement-toast-name {
    padding: 16px 26px 9px 14px;
  }

  .achievement-toast-name h3 {
    font-size: 19px;
  }

  .achievement-toast-copy > p {
    padding: 9px 14px 13px;
    font-size: 11px;
  }
}
</style>
