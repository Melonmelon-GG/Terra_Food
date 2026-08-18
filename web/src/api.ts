import axios from 'axios'

import type { Food, FoodCreatePayload, Region } from './types'

interface FoodQuery {
  keyword?: string
  regionId?: number
}

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 8000,
})

export async function getFoods(params?: FoodQuery): Promise<Food[]> {
  const response = await api.get<Food[]>('/foods', { params })
  return response.data
}

export async function getFood(id: number): Promise<Food> {
  const response = await api.get<Food>(`/foods/${id}`)
  return response.data
}

export async function getRegions(): Promise<Region[]> {
  const response = await api.get<Region[]>('/regions')
  return response.data
}

export async function createFood(payload: FoodCreatePayload): Promise<Food> {
  const response = await api.post<Food>('/foods', payload)
  return response.data
}

export async function uploadImage(file: File): Promise<string> {
  const formData = new FormData()
  formData.append('file', file)

  const response = await api.post<{ url: string }>('/images', formData)
  return response.data.url
}
