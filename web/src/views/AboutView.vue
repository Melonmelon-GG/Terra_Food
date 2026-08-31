<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <section class="about-page">
    <div class="about-seal">炎</div>
    <h1>{{ t('common.appName') }}</h1>
    <p class="about-tagline">{{ t('common.tagline') }}</p>
    <p class="about-body">{{ t('about.description') }}</p>
    <RouterLink to="/" class="about-back">{{ t('notFound.backHome') }}</RouterLink>
  </section>
</template>

<style scoped>
.about-page {
  min-height: calc(100dvh - 82px);
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 12px;
  padding: 48px 24px;
  text-align: center;
  background:
    radial-gradient(circle at 80% 12%, #a7493324, transparent 34%),
    #faf7ef;
}

.about-seal {
  display: grid;
  width: 72px;
  height: 72px;
  place-items: center;
  color: #fff7e8;
  font-size: 32px;
  background: #8f3029;
  border: 1px solid #c89b7d;
  border-radius: 12px;
}

.about-page h1 {
  margin: 6px 0 0;
  font-size: 30px;
}

.about-tagline {
  margin: 0;
  color: #84503f;
  font-size: 15px;
}

.about-body {
  max-width: 560px;
  margin: 10px 0 6px;
  color: #6b564a;
  font-size: 14px;
  line-height: 1.9;
}

.about-back {
  margin-top: 14px;
  padding: 10px 22px;
  color: #fff;
  text-decoration: none;
  background: #843129;
  border: 1px solid #843129;
}

.about-back:hover {
  background: #6f241e;
}
</style>