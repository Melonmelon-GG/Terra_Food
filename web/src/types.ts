export interface Region {
  id: number
  name: string
  province: string
  description: string
}

export interface Food {
  id: number
  name: string
  region: Region
  latitude: number
  longitude: number
  summary: string
  story: string
  ingredients: string
  imageUrl?: string
  heat: number
  createdAt: string
}

export interface FoodCreatePayload {
  name: string
  regionId: number
  latitude: number
  longitude: number
  summary: string
  story: string
  ingredients: string
  imageUrl?: string
}
