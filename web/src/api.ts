import axios from 'axios'

import type {
  AuthUser,
  Food,
  FoodCreatePayload,
  FoodImportResult,
  LoginPayload,
  SetUserActivePayload,
  Region,
  PagedAuthUsers,
  RegisterPayload,
} from './types'

interface FoodQuery {
  keyword?: string
  regionId?: number
}

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 8000,
  withCredentials: true,
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

interface PhotonProperties {
  state?: string
  province?: string
  city?: string
  county?: string
  district?: string
  locality?: string
  name?: string
}

interface PhotonResult {
  features?: Array<{ properties?: PhotonProperties }>
}

interface NominatimAddress {
  state?: string
  province?: string
  region?: string
  municipality?: string
  city?: string
  town?: string
  county?: string
  city_district?: string
  district?: string
  village?: string
}

interface NominatimResult {
  address?: NominatimAddress
}

interface MapLocation {
  province: string
  city: string
}

const mapRegionCache = new Map<string, MapLocation>()

export async function resolveMapRegion(
  latitude: number,
  longitude: number,
  signal?: AbortSignal,
): Promise<Region> {
  const key = `${latitude.toFixed(4)},${longitude.toFixed(4)}`
  let location = mapRegionCache.get(key)
  if (!location) {
    try {
      location = await reverseWithPhoton(latitude, longitude, signal)
    } catch (error) {
      if (signal?.aborted) {
        throw error
      }
      location = await reverseWithNominatim(latitude, longitude, signal)
    }
    mapRegionCache.set(key, location)
  }

  const response = await api.post<Region>('/regions/resolve', location, { signal })
  return response.data
}

async function reverseWithPhoton(
  latitude: number,
  longitude: number,
  signal?: AbortSignal,
): Promise<MapLocation> {
  const response = await axios.get<PhotonResult>('https://photon.komoot.io/reverse', {
    params: { lat: latitude, lon: longitude, lang: 'zh', limit: 1 },
    signal,
    timeout: 8000,
  })
  const properties = response.data.features?.[0]?.properties
  return extractMapLocation({
    province: properties?.state || properties?.province,
    city: properties?.city,
    county: properties?.county,
    district: properties?.district,
    locality: properties?.locality,
    name: properties?.name,
  })
}

async function reverseWithNominatim(
  latitude: number,
  longitude: number,
  signal?: AbortSignal,
): Promise<MapLocation> {
  const response = await axios.get<NominatimResult>('https://nominatim.openstreetmap.org/reverse', {
    params: {
      format: 'jsonv2',
      lat: latitude,
      lon: longitude,
      zoom: 12,
      addressdetails: 1,
      'accept-language': 'zh-CN',
    },
    signal,
    timeout: 8000,
  })
  const address = response.data.address
  return extractMapLocation({
    province: address?.state || address?.province || address?.region || address?.municipality,
    city: address?.city || address?.town || address?.municipality,
    county: address?.county || address?.city_district,
    district: address?.district,
    locality: address?.village,
  })
}

function extractMapLocation(value: {
  province?: string
  city?: string
  county?: string
  district?: string
  locality?: string
  name?: string
}): MapLocation {
  const province = normalizeProvince(value.province || '')
  const municipality = ['北京', '上海', '天津', '重庆', '香港', '澳门'].includes(province)
  const city = normalizeCity(
    municipality
      ? value.county || value.district || value.city || value.locality || value.name || ''
      : value.city || value.county || value.district || value.locality || value.name || '',
  )
  if (!province || !city) {
    throw new Error('Reverse geocoding response does not contain an administrative region')
  }
  return { province, city }
}

function normalizeProvince(value: string): string {
  return value
    .replace('特别行政区', '')
    .replace('维吾尔自治区', '')
    .replace('壮族自治区', '')
    .replace('回族自治区', '')
    .replace('自治区', '')
    .replace('省', '')
    .replace('市', '')
    .trim()
}

function normalizeCity(value: string): string {
  return value
    .replace('自治州', '')
    .replace('地区', '')
    .replace('县', '')
    .replace('区', '')
    .replace('市', '')
    .trim()
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

export async function importFoodSpreadsheet(file: File): Promise<FoodImportResult> {
  const formData = new FormData()
  formData.append('file', file)

  const response = await api.post<FoodImportResult>('/foods/import', formData, { timeout: 120_000 })
  return response.data
}

export async function login(payload: LoginPayload): Promise<AuthUser> {
  const response = await api.post<AuthUser>('/auth/login', payload)
  return response.data
}

export async function register(payload: RegisterPayload): Promise<AuthUser> {
  const response = await api.post<AuthUser>('/auth/register', payload)
  return response.data
}

export async function getCurrentUser(): Promise<AuthUser> {
  const response = await api.get<AuthUser>('/auth/me')
  return response.data
}

export async function logout(): Promise<void> {
  await api.post('/auth/logout')
}

export async function deleteFood(id: number): Promise<void> {
  await api.delete(`/foods/${id}`)
}

export async function getUsers(page = 1, pageSize = 10): Promise<PagedAuthUsers> {
  const response = await api.get<PagedAuthUsers>('/admin/users', {
    params: { page, pageSize },
  })
  return response.data
}

export async function setUserActive(id: number, payload: SetUserActivePayload): Promise<void> {
  await api.patch(`/admin/users/${id}/active`, payload)
}

export async function deleteUser(id: number): Promise<void> {
  await api.delete(`/admin/users/${id}`)
}
