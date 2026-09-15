<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import LandmarkBackdrop from '../components/LandmarkBackdrop.vue'
import '../archive.css'

const { t } = useI18n()
</script>

<template>
  <section class="about-page archive-about">
    <LandmarkBackdrop />
    <div class="archive-about-copy">
    <div class="about-seal">炎</div>
    <p class="about-tagline">{{ t('archive.aboutEyebrow') }}</p>
    <h1>{{ t('archive.aboutTitle') }}</h1>
    <p class="about-body">{{ t('archive.aboutIntro') }}</p>
    <RouterLink to="/" class="about-back">{{ t('notFound.backHome') }}</RouterLink>
    </div>
    <div class="archive-about-notes">
      <article v-for="topic in ['explore', 'record', 'collect']" :key="topic">
        <h2>{{ t('archive.' + topic + 'Title') }}</h2>
        <p>{{ t('archive.' + topic + 'Body') }}</p>
      </article>
    </div>
    <footer class="archive-about-footer"><p>{{ t('archive.fanNotice') }}</p><small>{{ t('archive.aboutCredit') }}</small></footer>
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
