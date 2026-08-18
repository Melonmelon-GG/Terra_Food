<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
</script>

<template>
  <header>
    <RouterLink to="/" class="brand">
      <span class="seal">炎</span>
      <div>
        <strong>大炎珍馐志</strong>
        <small>寻味九州 · 记录人间烟火</small>
      </div>
    </RouterLink>

    <nav>
      <RouterLink to="/">珍馐图鉴</RouterLink>
      <a href="#about">关于</a>
    </nav>
  </header>

  <main>
    <RouterView />
  </main>

  <footer id="about">
    大炎珍馐志 · 记录地域风味，也记录食物背后的人与时光
  </footer>
</template>
