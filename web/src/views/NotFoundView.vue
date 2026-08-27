<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <section class="not-found">
    <h1>404</h1>
    <p>{{ t('notFound.description') }}</p>
    <RouterLink to="/" class="not-found-back">{{ t('notFound.backHome') }}</RouterLink>
  </section>
</template>

<style scoped>
.not-found {
  min-height: 62vh;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 14px;
  padding: 48px 20px;
  text-align: center;
}

.not-found h1 {
  margin: 0;
  color: #843129;
  font-size: 72px;
  line-height: 1;
}

.not-found p {
  margin: 0;
  color: #6b564a;
  font-size: 15px;
}

.not-found-back {
  margin-top: 10px;
  padding: 10px 20px;
  color: #fff;
  text-decoration: none;
  background: #843129;
  border: 1px solid #843129;
}

.not-found-back:hover {
  background: #6f241e;
}
</style>