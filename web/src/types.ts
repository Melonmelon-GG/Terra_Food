export interface Region {
  id: number
  name: string
  province: string
  description: string
  centerLatitude?: number
  centerLongitude?: number
}

export interface UserSummary {
  id: number | null
  username: string
  displayName: string
  avatarUrl: string | null
}

export interface UserPublic {
  id: number
  username: string
  displayName: string
  avatarUrl: string | null
  signature: string | null
  foods: Food[]
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
  creator: UserSummary
  createdAt: string
}

export interface FoodComment {
  id: number
  foodId: number
  author: UserSummary
  content: string
  createdAt: string
}

export interface FoodCommentCreatePayload {
  content: string
}

export interface FoodFootprint {
  food: Food
  visitedAt: string
}

export interface AgentClientAction {
  type: 'SWITCH_MUSIC' | 'COMMENT_PUBLISHED'
  query: string
}

export interface AgentChatPayload {
  message: string
  availableTracks: string[]
  currentFoodId?: number
}

export interface AgentChatResponse {
  reply: string
  clientAction?: AgentClientAction
  recommendations?: AgentFoodRecommendation[]
  commentDraft?: AgentCommentDraft
}

export interface AgentFoodRecommendation {
  id: number
  name: string
}

export interface AgentCommentDraft {
  foodId: number
  foodName: string
  content: string
}

export interface MapBounds {
  minLatitude: number
  maxLatitude: number
  minLongitude: number
  maxLongitude: number
}

/** 地图标记：后端 /foods/markers 的轻量载荷，只含弹窗所需字段。 */
export interface FoodMarker {
  id: number
  name: string
  region: Region
  latitude: number
  longitude: number
  summary: string
}

/** 目录分页：后端 /foods/catalog 的返回结构。 */
export interface PagedCatalog {
  items: Food[]
  total: number
  page: number
  pageSize: number
}

export interface MapFocus {
  latitude: number
  longitude: number
  zoom: number
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

export interface FoodLikeStatus {
  likeCount: number
  likedByMe: boolean
}

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
  signature?: string
  signaturePending?: string
  signatureStatus?: SignatureStatus
  role: UserRole
  active: boolean
  createdAt: string
  pendingReviews?: PendingReview[]
}

export type ReviewField = 'DISPLAY_NAME' | 'SIGNATURE' | 'SEAL'
export type ReviewItemStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface PendingReview {
  id: number
  field: ReviewField
  currentValue: string
  pendingValue: string
  requestedAt: string
}

export interface ReviewItemPayload {
  field: ReviewField
  status: Extract<ReviewItemStatus, 'APPROVED' | 'REJECTED'>
}

export type SignatureStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface Achievement {
  id: number
  code: string
  name: string
  description: string
  imageUrl: string
  unlockedAt: string
  selected: boolean
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

export interface CaptchaChallenge {
  captchaId: string
  question: string
}

export interface SendRegistrationCodePayload {
  email: string
  captchaId: string
  captchaAnswer: string
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
  invalidCount: number
  truncatedCount: number
  issues: FoodImportIssue[]
}

export interface PagedAuthUsers {
  items: AuthUser[]
  total: number
  page: number
  pageSize: number
}
