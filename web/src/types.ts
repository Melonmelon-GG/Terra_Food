export interface Region {
  id: number
  name: string
  province: string
  description: string
  centerLatitude?: number
  centerLongitude?: number
}

export interface Food {
  id: number
  name: string
  region: Region
  latitude: number
  longitude: number
  address?: string
  summary: string
  story: string
  ingredients: string
  imageUrl?: string
  remark?: string
  heat: number
  reviewStatus: FoodReviewStatus
  reviewedBy?: string
  reviewedAt?: string
  createdBy: string
  createdAt: string
}

export interface MapBounds {
  minLatitude: number
  maxLatitude: number
  minLongitude: number
  maxLongitude: number
}

export interface MapFocus {
  latitude: number
  longitude: number
  zoom: number
}

export interface ResolvedMapLocation {
  region: Region
  address: string
}

export interface PagedFoods {
  items: Food[]
  total: number
  page: number
  pageSize: number
  totalHeat: number
  pendingTotal: number
}

export type FoodReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface FoodReviewPayload {
  status: Extract<FoodReviewStatus, 'APPROVED' | 'REJECTED'>
}

export interface FoodCreatePayload {
  name: string
  regionId: number
  latitude: number
  longitude: number
  address?: string
  summary: string
  story: string
  ingredients: string
  imageUrl?: string
  remark?: string
}

export type FoodUpdatePayload = FoodCreatePayload

export type UserRole = 'USER' | 'SUB_ADMIN' | 'ADMIN'
export type LoginRole = Extract<UserRole, 'USER' | 'ADMIN'>

export interface AuthUser {
  id: number
  username: string
  displayName: string
  email: string | null
  avatarUrl?: string
  role: UserRole
  active: boolean
  createdAt: string
}

export interface LoginPayload {
  username: string
  password: string
  role: LoginRole
}

export interface RegisterPayload {
  username: string
  password: string
  displayName: string
  email: string
  verificationCode: string
}

export interface SendRegistrationCodePayload {
  email: string
}

export interface SendPasswordResetCodePayload {
  username: string
  email: string
}

export interface PasswordResetPayload extends SendPasswordResetCodePayload {
  verificationCode: string
  newPassword: string
}
export interface SetUserActivePayload {
  active: boolean
}

export interface SetUserRolePayload {
  role: Extract<UserRole, 'USER' | 'SUB_ADMIN'>
}

export interface FoodImportIssue {
  rowNumber: number
  reason: string
}

export interface FoodImportResult {
  totalRows: number
  importedCount: number
  skippedCount: number
  duplicateCount: number
  anonymousCount: number
  issues: FoodImportIssue[]
}

export interface PagedAuthUsers {
  items: AuthUser[]
  total: number
  page: number
  pageSize: number
}
