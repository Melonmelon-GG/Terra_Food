import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'

import App from './App.vue'
import FoodDetailView from './views/FoodDetailView.vue'
import HomeView from './views/HomeView.vue'

import './style.css'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: HomeView,
    },
    {
      path: '/foods/:id',
      component: FoodDetailView,
    },
  ],
})

createApp(App)
  .use(router)
  .mount('#app')
