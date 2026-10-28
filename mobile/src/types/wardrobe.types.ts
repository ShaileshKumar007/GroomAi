/**
 * Wardrobe domain and upload intent models matching docs/API_SPEC.md Section 4.
 * Strict TypeScript types with zero `any`.
 */

export type Category = 'TOP' | 'BOTTOM' | 'FOOTWEAR' | 'OUTERWEAR' | 'ACCESSORY';

export type Color =
  | 'WHITE'
  | 'BLACK'
  | 'NAVY'
  | 'GREY'
  | 'BEIGE'
  | 'BROWN'
  | 'BLUE'
  | 'GREEN'
  | 'OLIVE'
  | 'RED'
  | 'BURGUNDY'
  | 'YELLOW'
  | 'MULTI';

export type FormalityLevel = 'CASUAL' | 'SMART_CASUAL' | 'BUSINESS_CASUAL' | 'FORMAL';

export type Pattern = 'SOLID' | 'STRIPED' | 'CHECKED' | 'FLORAL' | 'GRAPHIC' | 'TEXTURED';

export type Season = 'SPRING' | 'SUMMER' | 'FALL' | 'WINTER' | 'ALL_SEASON';

export type ItemStatus = 'ACTIVE' | 'ARCHIVED';

export interface UploadIntentRequestPayload {
  readonly contentType: 'image/jpeg';
  readonly byteSize: number;
}

export interface UploadIntentResponse {
  readonly uploadMode: 'LOCAL_STREAM' | 'S3_PRESIGNED';
  readonly uploadUrl: string;
  readonly storageKey: string;
  readonly expiresAt: string;
  readonly httpMethod: 'POST' | 'PUT';
  readonly requiredHeaders: Record<string, string>;
  readonly uploadTicket?: string;
}

export interface NormalizedImage {
  readonly uri: string;
  readonly width: number;
  readonly height: number;
  readonly byteSize: number;
  readonly contentType: 'image/jpeg';
}

export interface CreateWardrobeItemPayload {
  readonly name: string;
  readonly category: Category;
  readonly subcategory?: string | null;
  readonly primaryColor: Color;
  readonly secondaryColor?: Color | null;
  readonly pattern?: Pattern | null;
  readonly formalityLevel?: FormalityLevel | null;
  readonly season?: Season | null;
  readonly storageKey: string;
  readonly uploadTicket?: string | null;
}

export interface UpdateWardrobeItemPayload {
  readonly name: string;
  readonly category?: Category;
  readonly subcategory?: string | null;
  readonly primaryColor: Color;
  readonly secondaryColor?: Color | null;
  readonly pattern?: Pattern | null;
  readonly formalityLevel?: FormalityLevel | null;
  readonly season?: Season | null;
}

export interface WardrobeItemResponse {
  readonly id: string;
  readonly name: string;
  readonly category: Category;
  readonly subcategory: string | null;
  readonly primaryColor: Color;
  readonly secondaryColor: Color | null;
  readonly pattern: Pattern;
  readonly formalityLevel: FormalityLevel;
  readonly season: Season;
  readonly storageKey: string;
  readonly downloadUrl: string;
  readonly status: ItemStatus;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface PagedResponse<T> {
  readonly content: readonly T[];
  readonly pageNumber: number;
  readonly pageSize: number;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly first: boolean;
  readonly last: boolean;
}

export interface WardrobeListParams {
  readonly category?: Category;
  readonly status?: ItemStatus;
  readonly page?: number;
  readonly size?: number;
  readonly sort?: string;
}

export interface WardrobeItemFormMetadata {
  readonly name: string;
  readonly category: Category | '';
  readonly subcategory: string;
  readonly primaryColor: Color | '';
  readonly secondaryColor: Color | '';
  readonly pattern: Pattern;
  readonly formalityLevel: FormalityLevel;
  readonly season: Season;
}

export type WardrobeUploadStep =
  | 'idle'
  | 'requesting_intent'
  | 'uploading_binary'
  | 'cataloging'
  | 'completed'
  | 'partial_failure';

export type WardrobeErrorType =
  | 'CONSENT_REQUIRED'
  | 'IMAGE_TOO_SMALL'
  | 'FILE_TOO_LARGE'
  | 'PERMISSION_DENIED'
  | 'PERMISSION_PERMANENTLY_DENIED'
  | 'NORMALIZATION_FAILED'
  | 'UNSUPPORTED_FORMAT'
  | 'EXPIRED_TICKET'
  | 'INVALID_TICKET'
  | 'DUPLICATE_ITEM'
  | 'STORAGE_OBJECT_ALREADY_EXISTS'
  | 'INVALID_PAYLOAD'
  | 'VALIDATION_ERROR'
  | 'UNAUTHORIZED'
  | 'NETWORK_ERROR'
  | 'SERVER_ERROR'
  | 'UNKNOWN';

export interface WardrobeError {
  readonly type: WardrobeErrorType;
  readonly message: string;
  readonly status?: number;
}
