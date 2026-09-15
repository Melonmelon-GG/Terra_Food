import axios from 'axios'

import type { AgentChatPayload, AgentChatResponse, FoodFootprint, FoodCheckin, FoodTag, PagedCheckins, FoodMapResults, FoodMapClusters, FoodMapClusterItem, FoodSort, PagedFoodTags, FoodTagAdminPayload, PagedWishlist } from './types'

import type {
  Achievement,
  ProfileStats,
  AuthUser,
  CaptchaChallenge,
  Food,
  EtchingDesign,
  EtchingDesignPayload,
  FoodComment,
  FoodCommentCreatePayload,
  FoodCreatePayload,
  FoodLikeStatus,
  FavoriteStatus,
  FoodUpdatePayload,
  FoodImportResult,
  FoodMarker,
  FoodReviewPayload,
  MapBounds,
  PagedCatalog,
  PagedFoods,
  PagedComments,
  LoginPayload,
  PasswordResetPayload,
  SetUserActivePayload,
  SetUserRolePayload,
  Region,
  PagedAuthUsers,
  RegisterPayload,
  SendRegistrationCodePayload,
  SendPasswordResetCodePayload,
  UserPublic,
  ReviewItemPayload,
  WishlistItem,
  WishlistItemCreatePayload,
  WishlistStatus,
} from './types'

export interface FoodQuery extends Partial<MapBounds> {
  keyword?: string
  regionId?: number
  tasteIds?: number[]
  ingredientIds?: number[]
  cuisineIds?: number[]
  sort?: FoodSort
  inBounds?: boolean
}

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 8000,
  withCredentials: true,
})

// 由 main.ts 注册的会话失效回调：统一清空登录态并跳转登录页，
// 保持 api.ts 与 auth/router 解耦，避免循环依赖。
type RevisionedRequest = { __sessionRevision?: number }
let onUnauthorized: ((requestRevision?: number) => void) | undefined
let readSessionRevision: () => number = () => 0

export function registerUnauthorizedHandler(handler: (requestRevision?: number) => void): void {
  onUnauthorized = handler
}

export function registerSessionRevisionProvider(provider: () => number): void {
  readSessionRevision = provider
}

api.interceptors.request.use((config) => {
  ;(config as typeof config & RevisionedRequest).__sessionRevision = readSessionRevision()
  const method = (config.method || 'get').toLowerCase()
  if (!['post', 'put', 'patch', 'delete'].includes(method)) return config
  return api.get<{ token: string; headerName: string }>('/auth/csrf').then(({ data }) => {
    config.headers.set(data.headerName, data.token)
    return config
  })
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      const requestUrl = error.config?.url ?? ''
      // /auth/me 的 401 由 restoreSession 自行处理（静默）；登录失败 401 由登录表单展示，
      // 其余业务请求的 401 才触发统一登出跳转。
      if (!requestUrl.includes('/auth/me') && !requestUrl.includes('/auth/login')) {
        onUnauthorized?.((error.config as (typeof error.config & RevisionedRequest) | undefined)?.__sessionRevision)
      }
    }
    return Promise.reject(error)
  },
)

export async function getFoods(params?: FoodQuery): Promise<Food[]> {
  const response = await api.get<Food[]>('/foods', { params })
  return response.data
}

/** 地图标记（轻量接口，只含弹窗所需字段），驱动 Leaflet 标记层。 */
export async function getFoodMarkers(params?: FoodQuery): Promise<FoodMarker[]> {
  const response = await api.get<FoodMarker[]>('/foods/markers', { params })
  return response.data
}

export async function getFoodMapResults(params?: FoodQuery, signal?: AbortSignal): Promise<FoodMapResults> {
  const response = await api.get<FoodMapResults>('/foods/map-results', { params, signal })
  return response.data
}

export async function getFoodMapClusters(params: FoodQuery & { zoom: number }, signal?: AbortSignal): Promise<FoodMapClusters> {
  const response = await api.get<FoodMapClusters>('/foods/map-clusters', { params, signal })
  return response.data
}

export async function getFoodMapClusterMembers(cluster: FoodMapClusterItem, params: FoodQuery, page = 1, signal?: AbortSignal): Promise<PagedCatalog> {
  const response = await api.get<PagedCatalog>(`/foods/map-clusters/${cluster.id}/members`, {
    params: { ...params, minLatitude: cluster.minLatitude, maxLatitude: cluster.maxLatitude,
      minLongitude: cluster.minLongitude, maxLongitude: cluster.maxLongitude, page, pageSize: 20 }, signal,
  })
  return response.data
}

/** 目录分页：keyword/regionId 过滤 + page/pageSize，与地图 bounds 解耦。 */
export async function getFoodCatalog(params: {
  keyword?: string
  regionId?: number
  tasteIds?: number[]
  ingredientIds?: number[]
  cuisineIds?: number[]
  sort?: FoodSort
  inBounds?: boolean
  minLatitude?: number
  maxLatitude?: number
  minLongitude?: number
  maxLongitude?: number
  page: number
  pageSize: number
  compact?: boolean
}, signal?: AbortSignal): Promise<PagedCatalog> {
  const response = await api.get<PagedCatalog>('/foods/catalog', { params, signal })
  return response.data
}

export async function getFood(id: number): Promise<Food> {
  const response = await api.get<Food>(`/foods/${id}`)
  return response.data
}

export async function getFoodExportImage(id: number, signal?: AbortSignal): Promise<Blob> {
  const response = await api.get<Blob>('/images/foods/' + id + '/export', {
    responseType: 'blob',
    signal,
    timeout: 12_000,
  })
  return response.data
}

export async function getUserPublic(id: number): Promise<UserPublic> {
  const response = await api.get<UserPublic>(`/users/${id}`)
  return response.data
}

export async function getFoodLikeStatus(id: number): Promise<FoodLikeStatus> {
  const response = await api.get<FoodLikeStatus>(`/foods/${id}/like/status`)
  return response.data
}

export async function likeFood(id: number): Promise<FoodLikeStatus> {
  const response = await api.post<FoodLikeStatus>(`/foods/${id}/like`)
  return response.data
}

export async function unlikeFood(id: number): Promise<FoodLikeStatus> {
  const response = await api.post<FoodLikeStatus>(`/foods/${id}/like/unlike`)
  return response.data
}

export async function getFoodComments(foodId: number): Promise<FoodComment[]> {
  const response = await api.get<FoodComment[]>('/foods/' + foodId + '/comments')
  return response.data
}

export async function createFoodComment(
  foodId: number,
  payload: FoodCommentCreatePayload,
): Promise<FoodComment> {
  const response = await api.post<FoodComment>('/foods/' + foodId + '/comments', payload)
  return response.data
}

export async function getRegions(): Promise<Region[]> {
  const response = await api.get<Region[]>('/regions')
  if (!Array.isArray(response.data)) throw new Error('Invalid list response')
  return response.data
}

interface TiandituAddressComponent {
  province?: string
  city?: string
  county?: string
}

interface TiandituGeocoderResult {
  status?: string | number
  msg?: string
  result?: {
    formatted_address?: string
    addressComponent?: TiandituAddressComponent
  }
}

interface PhotonProperties {
  state?: string
  province?: string
  city?: string
  county?: string
  district?: string
  locality?: string
  name?: string
  street?: string
  housenumber?: string
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
  display_name?: string
  address?: NominatimAddress
}

export interface MapLocation {
  province: string
  city: string
  address: string
}

const mapRegionCache = new Map<string, MapLocation>()
const tiandituKey = import.meta.env.VITE_TIANDITU_KEY?.trim()
export async function reverseMapLocation(
  latitude: number,
  longitude: number,
  signal?: AbortSignal,
): Promise<MapLocation> {
  const key = latitude.toFixed(4) + ',' + longitude.toFixed(4)
  let location = mapRegionCache.get(key)
  if (!location) {
    try {
      location = await reverseWithTianditu(latitude, longitude, signal)
    } catch (error) {
      if (signal?.aborted) throw error
      try {
        location = await reverseWithPhoton(latitude, longitude, signal)
      } catch (fallbackError) {
        if (signal?.aborted) throw fallbackError
        try {
          location = await reverseWithNominatim(latitude, longitude, signal)
        } catch (lastError) {
          if (signal?.aborted) throw lastError
          throw lastError
        }
      }
    }
    mapRegionCache.set(key, location)
  }

  return location
}

export async function ensureMapRegion(
  province: string,
  city: string,
  signal?: AbortSignal,
): Promise<Region> {
  const response = await api.post<Region>('/regions/resolve', { province, city }, { signal })
  return response.data
}
async function reverseWithTianditu(
  latitude: number,
  longitude: number,
  signal?: AbortSignal,
): Promise<MapLocation> {
  if (!tiandituKey) {
    throw new Error('VITE_TIANDITU_KEY is not configured')
  }

  const response = await axios.get<TiandituGeocoderResult>('https://api.tianditu.gov.cn/geocoder', {
    params: {
      postStr: JSON.stringify({ lon: longitude, lat: latitude, ver: 1 }),
      type: 'geocode',
      tk: tiandituKey,
    },
    signal,
    timeout: 8000,
  })
  if (String(response.data.status) !== '0') {
    throw new Error(response.data.msg || 'Tianditu reverse geocoding failed')
  }

  const address = response.data.result?.addressComponent
  return extractMapLocation({
    province: address?.province,
    city: address?.city,
    county: address?.county,
  }, response.data.result?.formatted_address)
}

async function reverseWithPhoton(
  latitude: number,
  longitude: number,
  signal?: AbortSignal,
): Promise<MapLocation> {
  const response = await axios.get<PhotonResult>('https://photon.komoot.io/reverse', {
    // Photon 不接受 zh 作为语言代码；省略该参数可返回地点本地名称。
    params: { lat: latitude, lon: longitude, limit: 1 },
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
  }, formatAddress([
    properties?.state || properties?.province,
    properties?.city,
    properties?.district || properties?.county,
    properties?.street,
    properties?.housenumber || properties?.name,
  ]))
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
  }, response.data.display_name)
}

function extractMapLocation(value: {
  province?: string
  city?: string
  county?: string
  district?: string
  locality?: string
  name?: string
}, address = ''): MapLocation {
  const province = normalizeProvince(value.province || '')
  const municipality = ['北京', '上海', '天津', '重庆', '香港', '澳门'].includes(province)
  const city = normalizeCity(
    municipality
      ? province
      : value.city || value.county || value.district || value.locality || value.name || '',
  )
  if (!province || !city) {
    throw new Error('Reverse geocoding response does not contain an administrative region')
  }
  return {
    province,
    city,
    address: address.trim() || formatAddress([value.province, value.city, value.district, value.name]),
  }
}

function formatAddress(parts: Array<string | undefined>): string {
  return [...new Set(parts.map((part) => part?.trim()).filter(Boolean))]
    .join(' · ')
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

export async function createFood(payload: FoodCreatePayload, idempotencyKey?: string): Promise<Food> {
  const response = await api.post<Food>('/foods', payload, {
    headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined,
  })
  return response.data
}

export async function getMyProfileStats(): Promise<ProfileStats> {
  const response = await api.get<ProfileStats>('/profile/stats')
  return response.data
}

export async function getMyFoods(): Promise<Food[]> {
  const response = await api.get<Food[]>('/profile/foods')
  if (!Array.isArray(response.data)) throw new Error('Invalid list response')
  return response.data
}

export async function getMyFootprints(limit = 20): Promise<FoodFootprint[]> {
  const response = await api.get<FoodFootprint[]>('/profile/footprints', { params: { limit } })
  if (!Array.isArray(response.data)) throw new Error('Invalid list response')
  return response.data
}

export async function getFoodCommentsPage(foodId: number, page = 1, pageSize = 20, signal?: AbortSignal): Promise<PagedComments> {
  const response = await api.get<PagedComments>(`/foods/${foodId}/comments/page`, { params: { page, pageSize }, signal })
  return response.data
}

export async function getMyFavoritesPage(page = 1, pageSize = 10): Promise<PagedCatalog> {
  const response = await api.get<PagedCatalog>('/profile/favorites/page', { params: { page, pageSize } })
  return response.data
}

export async function createFoodCheckin(foodId: number, payload: { eatenOn: string; note?: string; visibility: 'PUBLIC' | 'PRIVATE'; timezone: string }, idempotencyKey: string): Promise<FoodCheckin> {
  const response = await api.post<FoodCheckin>(`/foods/${foodId}/check-ins`, payload, { headers: { 'Idempotency-Key': idempotencyKey } })
  return response.data
}

export async function getMyCheckins(page = 1, pageSize = 20): Promise<PagedCheckins> {
  const response = await api.get<PagedCheckins>('/profile/check-ins', { params: { page, pageSize } })
  if (!response.data || !Array.isArray(response.data.items)) throw new Error('Invalid check-in response')
  return response.data
}

export async function updateMyCheckin(id: number, payload: { eatenOn: string; note?: string; visibility: 'PUBLIC' | 'PRIVATE'; timezone: string; version: number }): Promise<FoodCheckin> {
  const response = await api.patch<FoodCheckin>(`/profile/check-ins/${id}`, payload)
  return response.data
}

export async function deleteMyCheckin(id: number, version: number): Promise<void> {
  await api.delete(`/profile/check-ins/${id}`, { params: { version } })
}

export async function getFoodTags(type?: FoodTag['type'], keyword?: string): Promise<FoodTag[]> {
  const response = await api.get<FoodTag[]>('/food-tags', { params: { type, keyword } })
  return response.data
}

export async function createFoodTag(payload: { type: FoodTag['type']; name: string }): Promise<FoodTag> {
  const response = await api.post<FoodTag>('/food-tags', payload)
  return response.data
}

export async function getFoodTagsForFood(foodId: number): Promise<FoodTag[]> {
  const response = await api.get<FoodTag[]>(`/food-tags/food/${foodId}`)
  return response.data
}

export async function getAdminFoodTags(params: { status?: string; type?: string; keyword?: string; page?: number; pageSize?: number } = {}): Promise<PagedFoodTags> {
  const response = await api.get<PagedFoodTags>('/admin/food-tags', { params })
  return response.data
}

export async function updateAdminFoodTag(id: number, payload: FoodTagAdminPayload): Promise<FoodTag> {
  const response = await api.patch<FoodTag>(`/admin/food-tags/${id}`, payload)
  return response.data
}

export async function mergeAdminFoodTag(id: number, targetId: number, version: number): Promise<FoodTag> {
  const response = await api.post<FoodTag>(`/admin/food-tags/${id}/merge`, { targetId, version })
  return response.data
}

export async function chatWithAgent(payload: AgentChatPayload): Promise<AgentChatResponse> {
  const response = await api.post<AgentChatResponse>('/agent/chat', payload, { timeout: 60_000 })
  return response.data
}

export async function updateMyFood(id: number, payload: FoodUpdatePayload): Promise<Food> {
  const response = await api.patch<Food>(`/profile/foods/${id}`, payload)
  return response.data
}

export async function uploadImage(file: File, signal?: AbortSignal): Promise<string> {
  const formData = new FormData()
  formData.append('file', file)

  const response = await api.post<{ url: string }>('/images', formData, { timeout: 60_000, signal })
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

export async function getMyFavorites(): Promise<Food[]> {
  const response = await api.get<Food[]>('/profile/favorites')
  if (!Array.isArray(response.data)) throw new Error('Invalid list response')
  return response.data
}

export async function getFavoriteStatus(foodId: number): Promise<FavoriteStatus> {
  const response = await api.get<FavoriteStatus>('/profile/favorites/' + foodId + '/status')
  if (!response.data || typeof response.data.favorited !== 'boolean') {
    throw new Error('Invalid favorite response')
  }
  return response.data
}

export async function addFavorite(foodId: number): Promise<FavoriteStatus> {
  const response = await api.post<FavoriteStatus>('/profile/favorites/' + foodId)
  if (!response.data || typeof response.data.favorited !== 'boolean') {
    throw new Error('Invalid favorite response')
  }
  return response.data
}

export async function removeFavorite(foodId: number): Promise<FavoriteStatus> {
  const response = await api.delete<FavoriteStatus>('/profile/favorites/' + foodId)
  if (!response.data || typeof response.data.favorited !== 'boolean') {
    throw new Error('Invalid favorite response')
  }
  return response.data
}

export async function getMyWishlist(): Promise<WishlistItem[]> {
  const response = await api.get<WishlistItem[]>('/profile/wishlist')
  if (!Array.isArray(response.data)) throw new Error('Invalid list response')
  return response.data
}

export async function getMyWishlistPage(page = 1, pageSize = 20): Promise<PagedWishlist> {
  const response = await api.get<PagedWishlist>('/profile/wishlist/page', { params: { page, pageSize } })
  if (!response.data || !Array.isArray(response.data.items)) throw new Error('Invalid wishlist response')
  return response.data
}

export async function getWishlistStatus(foodId: number): Promise<WishlistStatus> {
  const response = await api.get<WishlistStatus>('/profile/wishlist/foods/' + foodId + '/status')
  return response.data
}

export async function addWishlistItem(payload: WishlistItemCreatePayload): Promise<WishlistItem> {
  const response = await api.post<WishlistItem>('/profile/wishlist', payload)
  return response.data
}

export async function deleteWishlistItem(id: number): Promise<void> {
  await api.delete('/profile/wishlist/' + id)
}

export async function register(payload: RegisterPayload): Promise<AuthUser> {
  const response = await api.post<AuthUser>('/auth/register', payload)
  return response.data
}

export async function getCaptcha(): Promise<CaptchaChallenge> {
  const response = await api.get<CaptchaChallenge>('/auth/captcha')
  return response.data
}

export async function sendRegistrationCode(payload: SendRegistrationCodePayload): Promise<void> {
  // SMTP 投递可能超过全局 8 秒超时；邮件实际发出后再被前端误报失败会诱导用户重复发送。
  await api.post('/auth/registration-code', payload, { timeout: 30_000 })
}

export async function sendPasswordResetCode(payload: SendPasswordResetCodePayload): Promise<void> {
  await api.post('/auth/password-reset-code', payload, { timeout: 30_000 })
}

export async function resetPassword(payload: PasswordResetPayload): Promise<void> {
  await api.post('/auth/password-reset', payload)
}
export async function getCurrentUser(): Promise<AuthUser> {
  const response = await api.get<AuthUser>('/auth/me')
  return response.data
}

export async function updateAvatar(avatarUrl: string): Promise<AuthUser> {
  const response = await api.patch<AuthUser>('/profile/avatar', { avatarUrl })
  return response.data
}

export async function updateMySignature(signature: string): Promise<AuthUser> {
  const response = await api.patch<AuthUser>('/profile/signature', { signature })
  return response.data
}

export async function updateMyDisplayName(displayName: string): Promise<AuthUser> {
  const response = await api.patch<AuthUser>('/profile/display-name', { displayName })
  return response.data
}

export async function reviewUserItem(
  id: number,
  payload: ReviewItemPayload,
): Promise<void> {
  await api.patch(`/admin/users/${id}/review`, payload)
}

export async function getAchievements(): Promise<Achievement[]> {
  const response = await api.get<Achievement[]>('/achievements/me')
  if (!Array.isArray(response.data)) throw new Error('Invalid list response')
  return response.data
}

export async function getAchievementNotifications(): Promise<Achievement[]> {
  const response = await api.get<Achievement[]>('/achievements/notifications')
  return response.data
}

export async function markAchievementNotificationRead(achievementId: number): Promise<void> {
  await api.post('/achievements/' + achievementId + '/notification-read')
}

export async function selectAchievement(achievementId: number): Promise<Achievement> {
  const response = await api.put<Achievement>('/achievements/selection', { achievementId })
  return response.data
}

export async function getMyEtchings(): Promise<EtchingDesign[]> {
  const response = await api.get<EtchingDesign[]>('/etchings/me')
  if (!Array.isArray(response.data)) throw new Error('Invalid list response')
  return response.data
}
export async function createEtching(payload: EtchingDesignPayload): Promise<EtchingDesign> {
  const response = await api.post<EtchingDesign>('/etchings', payload)
  if (!response.data || !Number.isSafeInteger(response.data.id) || !Array.isArray(response.data.layerOne)) {
    throw new Error('Invalid etching response')
  }
  return response.data
}
export async function updateEtching(id: number, payload: EtchingDesignPayload): Promise<EtchingDesign> {
  const response = await api.put<EtchingDesign>(`/etchings/${id}`, payload)
  if (!response.data || !Number.isSafeInteger(response.data.id) || !Array.isArray(response.data.layerOne)) {
    throw new Error('Invalid etching response')
  }
  return response.data
}
export async function deleteEtching(id: number): Promise<void> { await api.delete(`/etchings/${id}`) }
export async function selectEtching(id: number): Promise<EtchingDesign> {
  const response = await api.put<EtchingDesign>(`/etchings/${id}/selection`)
  return response.data
}
export async function logout(): Promise<void> {
  await api.post('/auth/logout')
}

export async function deleteFood(id: number): Promise<void> {
  await api.delete(`/foods/${id}`)
}

export async function getAdminFoods(
  page = 1,
  pageSize = 10,
  status?: Food['reviewStatus'],
): Promise<PagedFoods> {
  const response = await api.get<PagedFoods>('/admin/foods', {
    params: { page, pageSize, status },
  })
  return response.data
}

export async function reviewFood(id: number, payload: FoodReviewPayload): Promise<void> {
  await api.patch(`/admin/foods/${id}/review`, payload)
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

export async function setUserRole(id: number, payload: SetUserRolePayload): Promise<void> {
  await api.patch(`/admin/users/${id}/role`, payload)
}

export async function deleteUser(id: number): Promise<void> {
  await api.delete(`/admin/users/${id}`)
}
