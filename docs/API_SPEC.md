# Groom.ai — REST API Specification

**Document Version:** 2.0.0  
**Status:** Approved Specification (Wardrobe-First Personal Stylist)  
**Base Path:** `/api/v1`  
**Protocol:** HTTPS / TLS 1.3  
**Data Format:** `application/json` (UTF-8)  
**Last Updated:** October 2026  

---

## 1. API Conventions & Standards

### 1.1 URL Conventions & Versioning
- All endpoints use the `/api/v1` version prefix.
- Resource paths are pluralized, lowercase, and hyphenated (e.g., `/api/v1/wardrobe/items`, `/api/v1/outfits/wear-history`).
- Standard HTTP verbs apply: `GET` (read), `POST` (create / action), `PUT` (replace/update), `PATCH` (partial update), `DELETE` (remove).

### 1.2 Data Formats & Timestamps
- Attribute naming uses **`camelCase`** across all JSON request and response payloads.
- All timestamps are serialized as ISO-8601 strings in UTC format: `YYYY-MM-DDTHH:mm:ss[.SSS]Z`.
- Entity identifiers are standard RFC 4122 **UUIDv4** strings.

### 1.3 Authentication & Headers
- Authenticated endpoints require standard Authorization header:
  ```http
  Authorization: Bearer <jwt_access_token>
  ```
- **Correlation ID:** Clients may send `X-Correlation-ID: <uuid>`. If omitted, the backend generates one and returns it in the response header for distributed tracing.

### 1.4 Standard Error Response Schema
All error responses (4xx and 5xx) strictly adhere to this structure:

```json
{
  "timestamp": "2026-10-16T08:30:00.123Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_FAILED",
  "message": "One or more input fields failed validation constraints.",
  "path": "/api/v1/wardrobe/items",
  "details": [
    {
      "field": "category",
      "issue": "Must be one of: TOP, BOTTOM, FOOTWEAR, OUTERWEAR, ACCESSORY"
    }
  ]
}
```

#### Standard Error Codes:
- `VALIDATION_FAILED`: Request payload failed constraint validation.
- `UNAUTHORIZED`: Authentication missing or token invalid/expired.
- `TOKEN_EXPIRED`: JWT access token has expired.
- `FORBIDDEN`: User lacks authorization for the requested resource.
- `RESOURCE_NOT_FOUND`: Target entity does not exist or user does not own it.
- `RESOURCE_CONFLICT`: Duplicate resource (e.g., email already registered).
- `RATE_LIMIT_EXCEEDED`: Too many requests submitted within the active window.
- `CONSENT_REQUIRED`: Required privacy consent has not been granted.
- `UNSUPPORTED_IMAGE_FORMAT`: Image format rejected (e.g., `image/heic`; client must transcode to JPEG).
- `INSUFFICIENT_WARDROBE_ITEMS`: User has fewer than the minimum items (1 Top, 1 Bottom, 1 Footwear) required for recommendations.
- `AI_SERVICE_UNAVAILABLE`: Upstream AI provider is unreachable or returned 5xx.
- `AI_INFERENCE_TIMEOUT`: Upstream AI styling call exceeded 30-second socket timeout.
- `INTERNAL_SERVER_ERROR`: Unhandled exception caught by global safety wrapper.

### 1.5 Pagination Envelope
List endpoints support pagination:
- `page`: 0-indexed page number (default: `0`).
- `size`: Items per page (default: `20`, max: `100`).
- `sort`: Sorting field and direction (e.g., `createdAt,desc`).

```json
{
  "content": [ ... ],
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 34,
  "totalPages": 2,
  "first": true,
  "last": false
}
```

---

## 2. Authentication & Session Endpoints (Phase 1 Baseline — Implemented)

### 2.1 Register New User
- **Method & Path:** `POST /api/v1/auth/register`
- **Authentication:** None (Public)
- **Request Body:**
  ```json
  {
    "email": "user@example.com",
    "password": "SecurePassword123!",
    "displayName": "Alex Sharma"
  }
  ```
- **Responses:**
  - `201 Created`:
    ```json
    {
      "accessToken": "eyJhbGciOi...",
      "tokenType": "Bearer",
      "expiresIn": 900,
      "refreshToken": "7c8e3f...",
      "user": {
        "id": "d290f1ee-6c54-4b01-90e6-d701748f0851",
        "email": "user@example.com",
        "displayName": "Alex Sharma"
      }
    }
    ```
  - `409 Conflict`: `code: "EMAIL_ALREADY_EXISTS"`

### 2.2 Login User
- **Method & Path:** `POST /api/v1/auth/login`
- **Authentication:** None (Public)
- **Request Body:**
  ```json
  {
    "email": "user@example.com",
    "password": "SecurePassword123!"
  }
  ```
- **Responses:**
  - `200 OK`: Returns tokens and user details payload.
  - `401 Unauthorized`: `code: "INVALID_CREDENTIALS"`

### 2.3 Refresh Access Token
- **Method & Path:** `POST /api/v1/auth/refresh`
- **Authentication:** None (Public)
- **Request Body:**
  ```json
  {
    "refreshToken": "7c8e3f..."
  }
  ```
- **Responses:**
  - `200 OK`: Returns new `accessToken` and rotated `refreshToken`.
  - `401 Unauthorized`: Expired, revoked, or invalid refresh token.

### 2.4 Logout User
- **Method & Path:** `POST /api/v1/auth/logout`
- **Authentication:** Bearer Token
- **Request Body:**
  ```json
  {
    "refreshToken": "7c8e3f..."
  }
  ```
- **Responses:**
  - `200 OK`: `{ "message": "Successfully logged out." }`

### 2.5 Get Authenticated User Details
- **Method & Path:** `GET /api/v1/auth/me`
- **Authentication:** Bearer Token
- **Responses:**
  - `200 OK`:
    ```json
    {
      "id": "d290f1ee-6c54-4b01-90e6-d701748f0851",
      "email": "user@example.com",
      "displayName": "Alex Sharma",
      "role": "ROLE_USER"
    }
    ```

---

## 3. User Profile, Dual Consents & Style Preferences

### 3.1 Get User Profile
- **Method & Path:** `GET /api/v1/profile`
- **Authentication:** Bearer Token
- **Responses:**
  - `200 OK`:
    ```json
    {
      "userId": "d290f1ee-6c54-4b01-90e6-d701748f0851",
      "displayName": "Alex Sharma",
      "ageBracket": "TWENTIES",
      "wardrobeStorageConsentGranted": true,
      "wardrobeStorageConsentGrantedAt": "2026-10-16T08:30:00Z",
      "aiStylingConsentGranted": true,
      "aiStylingConsentGrantedAt": "2026-10-16T08:35:00Z",
      "updatedAt": "2026-10-16T08:35:00Z"
    }
    ```

### 3.2 Update User Profile
- **Method & Path:** `PUT /api/v1/profile`
- **Authentication:** Bearer Token
- **Request Body:**
  ```json
  {
    "displayName": "Alex S.",
    "ageBracket": "THIRTIES"
  }
  ```
- **Responses:**
  - `200 OK`: Updated profile payload.

### 3.3 Update Privacy Consents (Dual Decoupled Consents)
- **Method & Path:** `PUT /api/v1/profile/consents`
- **Authentication:** Bearer Token
- **Request Body:**
  ```json
  {
    "wardrobeStorageConsent": true,
    "aiStylingConsent": true
  }
  ```
- **Responses:**
  - `200 OK`:
    ```json
    {
      "wardrobeStorageConsentGranted": true,
      "wardrobeStorageConsentGrantedAt": "2026-10-16T08:30:00Z",
      "aiStylingConsentGranted": true,
      "aiStylingConsentGrantedAt": "2026-10-16T08:35:00Z"
    }
    ```
- **Consent Revocation Semantics:**
  - Revoking wardrobe storage consent (`wardrobeStorageConsent: false`) immediately blocks subsequent `POST /api/v1/wardrobe/upload-intent` calls (`403 Forbidden` / `CONSENT_REQUIRED`), but preserves previously cataloged wardrobe items unless explicitly deleted.
  - Revoking AI styling consent (`aiStylingConsent: false`) immediately prevents subsequent requests from sending wardrobe data to an external AI provider. `POST /api/v1/outfits/generate` may still return deterministic outfit candidates, but `stylingRationale` and `stylingTips` must be omitted or `null`; the response must identify that AI rationale was not generated. Existing outfits and wear logs are preserved.

### 3.4 Get Style Preferences
- **Method & Path:** `GET /api/v1/profile/style-preferences`
- **Authentication:** Bearer Token
- **Responses:**
  - `200 OK`:
    ```json
    {
      "primaryStyle": "SMART_CASUAL",
      "preferredFit": "REGULAR",
      "colorPreferences": ["NAVY", "WHITE", "OLIVE", "BEIGE"],
      "avoidedColors": ["YELLOW", "NEON_GREEN"],
      "defaultFormality": "CASUAL",
      "updatedAt": "2026-10-16T08:32:00Z"
    }
    ```

### 3.5 Update Style Preferences
- **Method & Path:** `PUT /api/v1/profile/style-preferences`
- **Authentication:** Bearer Token
- **Request Body:**
  ```json
  {
    "primaryStyle": "MINIMALIST",
    "preferredFit": "SLIM",
    "colorPreferences": ["BLACK", "WHITE", "GREY"],
    "avoidedColors": ["RED"],
    "defaultFormality": "SMART_CASUAL"
  }
  ```
- **Responses:**
  - `200 OK`: Updated style preferences payload.

---

## 4. Digital Wardrobe Management Endpoints

### 4.1 Request Wardrobe Pre-Signed Upload Intent
- **Method & Path:** `POST /api/v1/wardrobe/upload-intent`
- **Authentication:** Bearer Token
- **Prerequisite:** `wardrobeStorageConsentGranted == true`.
- **Request Body:**
  ```json
  {
    "contentType": "image/jpeg",
    "byteSize": 2048500
  }
  ```
- **Validation Rules:**
  - `contentType`: Required. Only `image/jpeg` is accepted. Other types, including `image/png` and `image/heic`, are **rejected** with HTTP `400 Bad Request` (`code: "UNSUPPORTED_IMAGE_FORMAT"`). The mobile client must normalize source images to JPEG before requesting an upload intent.
  - `byteSize`: Required, positive integer, max 10MB (`10485760` bytes).
- **Responses:**
  - `201 Created` (When environment uses S3 / Cloud Storage):
    ```json
    {
      "uploadMode": "S3_PRESIGNED",
      "uploadUrl": "https://groomai-assets.s3.amazonaws.com/wardrobe/user-uuid/item-abc.jpg?X-Amz-Signature=...",
      "storageKey": "wardrobe/user-uuid/item-abc.jpg",
      "expiresAt": "2026-10-16T08:45:00Z",
      "httpMethod": "PUT",
      "requiredHeaders": {
        "Content-Type": "image/jpeg"
      }
    }
    ```
  - `201 Created` (When environment uses Local Filesystem Storage for development):
    ```json
    {
      "uploadMode": "LOCAL_STREAM",
      "uploadUrl": "http://192.168.1.100:8080/api/v1/storage/upload/wardrobe/user-uuid/item-abc.jpg",
      "storageKey": "wardrobe/user-uuid/item-abc.jpg",
      "expiresAt": "2026-10-16T08:45:00Z",
      "httpMethod": "POST",
      "requiredHeaders": {
        "Content-Type": "multipart/form-data",
        "X-Upload-Ticket": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
      },
      "uploadTicket": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
    }
    ```
  - `403 Forbidden`: `code: "CONSENT_REQUIRED"` (Wardrobe storage consent not granted).
  - `400 Bad Request`: `code: "UNSUPPORTED_IMAGE_FORMAT"` or file size > 10MB.

### 4.1.1 Local Storage Adapter Endpoints (Local Dev / Device Testing Only)
- **POST `/api/v1/storage/upload/{storageKey}`**:
  - Direct binary or multipart upload endpoint used in local development mode. Saves file to `./data/storage/{storageKey}`.
  - **Authorization & Security:**
    - Requires valid Bearer access token matching `storageKey` user prefix.
    - Requires `X-Upload-Ticket` header containing the signed upload ticket issued by `POST /api/v1/wardrobe/upload-intent`. Query parameter transport is rejected.
    - **Create-Only Protection:** Operates as atomic create-only storage. Rejects replay or overwrite attempts targeting an existing storage key.
  - **Responses:**
    - `200 OK`: File uploaded successfully.
    - `401 Unauthorized`: `code: "UNAUTHORIZED"` (Missing or invalid Bearer access token).
    - `403 Forbidden`: `code: "INVALID_UPLOAD_TICKET"` (Missing, tampered, or mismatched ticket), `code: "EXPIRED_UPLOAD_TICKET"` (Ticket expired after 15m), or `code: "FORBIDDEN"` (Cross-tenant user mismatch).
    - `409 Conflict`: `code: "STORAGE_OBJECT_ALREADY_EXISTS"` (Target file already exists; overwrite prohibited).
    - `400 Bad Request`: `code: "INVALID_IMAGE_PAYLOAD"` (Payload exceeds 10MB).
- **GET `/api/v1/storage/files/{storageKey}`**:
  - Serves authenticated image stream from local disk with appropriate `Content-Type: image/jpeg` header. Requires valid Bearer token matching `storageKey` user prefix.

### 4.2 Create Wardrobe Item (Confirm & Catalog)
- **Method & Path:** `POST /api/v1/wardrobe/items`
- **Authentication:** Bearer Token
- **Request Body:**
  ```json
  {
    "name": "Navy Oxford Button-Down",
    "category": "TOP",
    "subcategory": "BUTTON_DOWN",
    "primaryColor": "NAVY",
    "secondaryColor": "WHITE",
    "pattern": "SOLID",
    "formalityLevel": "SMART_CASUAL",
    "season": "ALL_SEASON",
    "storageKey": "wardrobe/user-uuid/item-abc.jpg",
    "uploadTicket": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }
  ```
- **Validation Rules:**
  - `name`: Required, 2–100 chars.
  - `category`: Required enum (`TOP`, `BOTTOM`, `FOOTWEAR`, `OUTERWEAR`, `ACCESSORY`).
  - `primaryColor`: Required enum (`WHITE`, `BLACK`, `NAVY`, `GREY`, `BEIGE`, `BROWN`, `BLUE`, `GREEN`, `OLIVE`, `RED`, `BURGUNDY`, `YELLOW`, `MULTI`).
  - `formalityLevel`: Optional enum (default: `CASUAL`). Values: `CASUAL`, `SMART_CASUAL`, `BUSINESS_CASUAL`, `FORMAL`.
  - `pattern`: Optional enum (default: `SOLID`). Values: `SOLID`, `STRIPED`, `CHECKED`, `FLORAL`, `GRAPHIC`, `TEXTURED`.
  - `season`: Optional enum (default: `ALL_SEASON`). Values: `SPRING`, `SUMMER`, `FALL`, `WINTER`, `ALL_SEASON`.
  - `storageKey`: Required string matching uploaded asset.
  - `uploadTicket`: Optional string. If omitted, backend verifies the ticket persisted in storage metadata at upload time.
  - **Storage Object Verification:** The backend verifies that the object referenced by `storageKey` exists, begins with JPEG magic bytes (`0xFF, 0xD8, 0xFF`), does not exceed 10MB, and has image dimensions of at least 200x200 px.
- **Responses:**
  - `201 Created`:
    ```json
    {
      "id": "item-1111-2222-3333-4444",
      "name": "Navy Oxford Button-Down",
      "category": "TOP",
      "subcategory": "BUTTON_DOWN",
      "primaryColor": "NAVY",
      "secondaryColor": "WHITE",
      "pattern": "SOLID",
      "formalityLevel": "SMART_CASUAL",
      "season": "ALL_SEASON",
      "storageKey": "wardrobe/user-uuid/item-abc.jpg",
      "downloadUrl": "https://groomai-assets.s3.amazonaws.com/wardrobe/user-uuid/item-abc.jpg?X-Amz-Signature=...",
      "status": "ACTIVE",
      "createdAt": "2026-10-16T08:35:00Z",
      "updatedAt": "2026-10-16T08:35:00Z"
    }
    ```
  - `400 Bad Request`: Validation failure (field validation), `code: "INVALID_IMAGE_PAYLOAD"` (magic byte / dimension check failed), or `code: "STORAGE_OBJECT_NOT_FOUND"`.
  - `403 Forbidden`: `code: "INVALID_UPLOAD_TICKET"` (Missing or invalid authorization ticket), `code: "EXPIRED_UPLOAD_TICKET"` (Intent ticket expired), or `code: "FORBIDDEN"` (Cross-tenant access).
  - `409 Conflict`: `code: "DUPLICATE_ITEM"` (Item with this storage key has already been cataloged).

### 4.3 List Wardrobe Items
- **Method & Path:** `GET /api/v1/wardrobe/items`
- **Authentication:** Bearer Token
- **Query Parameters:**
  - `category`: Optional filter (`TOP`, `BOTTOM`, `FOOTWEAR`, `OUTERWEAR`, `ACCESSORY`).
  - `formalityLevel`: Optional filter.
  - `season`: Optional filter.
  - `primaryColor`: Optional filter.
  - `status`: Optional filter (default: `ACTIVE`).
  - `page`: default `0`, `size`: default `20`.
- **Responses:**
  - `200 OK`: Paginated envelope with `WardrobeItem` items, each including ephemeral `downloadUrl`.

### 4.4 Get Single Wardrobe Item
- **Method & Path:** `GET /api/v1/wardrobe/items/{id}`
- **Authentication:** Bearer Token
- **Responses:**
  - `200 OK`: Full wardrobe item details.
  - `404 Not Found`: Item does not exist or user does not own it.

### 4.5 Update Wardrobe Item
- **Method & Path:** `PUT /api/v1/wardrobe/items/{id}`
- **Authentication:** Bearer Token
- **Request Body:**
  ```json
  {
    "name": "Navy Linen Shirt",
    "subcategory": "BUTTON_DOWN",
    "primaryColor": "NAVY",
    "secondaryColor": null,
    "pattern": "SOLID",
    "formalityLevel": "CASUAL",
    "season": "SUMMER"
  }
  ```
- **Responses:**
  - `200 OK`: Updated wardrobe item payload.
  - `404 Not Found`: Item not found or not owned by user.

### 4.6 Delete Wardrobe Item
- **Method & Path:** `DELETE /api/v1/wardrobe/items/{id}`
- **Authentication:** Bearer Token
- **Behavior & Eradication:**
  - Enqueues item `storage_key` into `storage_deletion_outbox` in the same transaction.
  - Deletes `wardrobe_items` record. Any historical outfit referencing this item has its reference set to `null` (`ON DELETE SET NULL`), preserving outfit text history.
- **Responses:**
  - `204 No Content`: Item deleted and binary scheduled for eradication.
  - `404 Not Found`: Item not found or not owned by user.

---

## 5. Outfit Recommendation & Wear Logging Endpoints (Phase 3/4 Specification)

### 5.1 Generate Outfit Recommendations (Two-Stage Hybrid Engine)
- **Method & Path:** `POST /api/v1/outfits/generate`
- **Authentication:** Bearer Token
- **Prerequisite:** Minimum 1 Top, 1 Bottom, and 1 Footwear in the active wardrobe. AI styling consent is required only for sending wardrobe data to the hosted AI provider and generating AI-written rationale; without it, deterministic candidates remain available and AI rationale is omitted.
- **Request Body:**
  ```json
  {
    "occasion": "BUSINESS_CASUAL",
    "weather": {
      "temperatureBracket": "MILD",
      "condition": "SUNNY"
    }
  }
  ```
- **Responses:**
  - `200 OK`:
    ```json
    {
      "outfits": [
        {
          "id": "outfit-9988-7766",
          "occasion": "BUSINESS_CASUAL",
          "suitabilityScore": 95,
          "stylingRationale": "The navy oxford button-down pairs cleanly with the beige chinos, creating a classic high-contrast smart casual base. Brown loafers ground the look with professional polish.",
          "stylingTips": "Leave the top button undone and tuck the shirt with a matching brown belt for clean lines.",
          "items": [
            {
              "role": "TOP",
              "id": "item-1111",
              "name": "Navy Oxford Button-Down",
              "category": "TOP",
              "primaryColor": "NAVY",
              "downloadUrl": "https://..."
            },
            {
              "role": "BOTTOM",
              "id": "item-2222",
              "name": "Slim Beige Chinos",
              "category": "BOTTOM",
              "primaryColor": "BEIGE",
              "downloadUrl": "https://..."
            },
            {
              "role": "FOOTWEAR",
              "id": "item-3333",
              "name": "Brown Leather Penny Loafers",
              "category": "FOOTWEAR",
              "primaryColor": "BROWN",
              "downloadUrl": "https://..."
            }
          ]
        }
      ]
    }
    ```
  - When AI styling consent is absent, return deterministic candidates without AI rationale; do not return `CONSENT_REQUIRED` solely because this optional AI step is unavailable.
  - `400 Bad Request`: `code: "INSUFFICIENT_WARDROBE_ITEMS"` (Requires at least 1 Top, 1 Bottom, 1 Footwear).
  - `504 Gateway Timeout`: `code: "AI_INFERENCE_TIMEOUT"` (AI provider exceeded 30s timeout).

### 5.2 Log Outfit as Worn Today
- **Method & Path:** `POST /api/v1/outfits/{id}/wear`
- **Authentication:** Bearer Token
- **Request Body:**
  ```json
  {
    "wornDate": "2026-10-16",
    "notes": "Client meeting went well; great outfit."
  }
  ```
- **Responses:**
  - `200 OK`:
    ```json
    {
      "logId": "wear-5555-6666",
      "outfitId": "outfit-9988-7766",
      "wornDate": "2026-10-16",
      "message": "Outfit wear successfully logged. Repetition penalty applied for upcoming recommendations."
    }
    ```

### 5.3 Get Wear History
- **Method & Path:** `GET /api/v1/outfits/wear-history`
- **Authentication:** Bearer Token
- **Responses:**
  - `200 OK`: List of logged outfit wear dates and item summaries.

---

## 6. Privacy, Data Export & Permanent Deletion

### 6.1 Export User Data Archive
- **Method & Path:** `POST /api/v1/users/export`
- **Authentication:** Bearer Token
- **Responses:**
  - `200 OK`: Structured JSON containing complete user profile, style preferences, digital wardrobe items, and wear history.

### 6.2 Permanent Account Deletion
- **Method & Path:** `DELETE /api/v1/users/account`
- **Authentication:** Bearer Token
- **Request Body:**
  ```json
  {
    "confirmationText": "DELETE MY ACCOUNT",
    "password": "SecurePassword123!"
  }
  ```
- **Execution & Invalidation:**
  1. Verifies password and confirmation text.
  2. Within a single `@Transactional` database transaction:
     - Extracts all wardrobe and photo storage keys and enqueues them into `storage_deletion_outbox` (`status = 'PENDING'`, `attempts = 0`).
     - Executes `DELETE FROM users WHERE id = :userId`, cascading across all dependent user tables.
  3. Returns **HTTP `204 No Content`** immediately. All existing JWTs are invalidated immediately.
  4. Post-commit dispatcher and scheduled sweeper asynchronously eradicate all private storage objects with bounded retries.
- **Responses:**
  - `204 No Content`: Account erased and storage scheduled for eradication.
  - `400 Bad Request`: Incorrect password or confirmation text.

---

## 7. System & Health Endpoints

### 7.1 Public Service Health Check
- **Method & Path:** `GET /api/v1/health`
- **Authentication:** None (Public)
- **Responses:**
  - `200 OK`:
    ```json
    {
      "status": "UP",
      "service": "groomai-backend",
      "timestamp": "2026-10-16T08:30:00Z"
    }
    ```
