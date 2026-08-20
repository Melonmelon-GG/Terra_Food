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
  address?: string
  summary: string
  story: string
  ingredients: string
  imageUrl?: string
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
}

export type UserRole = 'USER' | 'ADMIN'

export interface AuthUser {
  id: number
  username: string
  displayName: string
  role: UserRole
  active: boolean
  createdAt: string
}

export interface LoginPayload {
  username: string
  password: string
  role: UserRole
}

export interface RegisterPayload {
  username: string
  password: string
  displayName: string
}

export interface SetUserActivePayload {
  active: boolean
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
