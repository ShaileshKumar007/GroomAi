# Groom.ai — REST API Specification

**Document Version:** 1.1.0  
**Status:** Draft — Proposed Specification (Pending Stakeholder Approval)  
**Base Path:** `/api/v1`  
**Protocol:** HTTPS / TLS 1.3  
**Data Format:** `application/json` (UTF-8)  
**Last Updated:** October 2026  

---

## 1. API Conventions & Standards

### 1.1 URL Conventions & Versioning
- All application endpoints are versioned with the `/api/v1` prefix.
- Resource paths are pluralized, lowercase, and hyphenated (e.g., `/api/v1/saved-recommendations`).
- Actions on resources use standard HTTP verbs or sub-resource action suffixes (e.g., `POST /api/v1/photos/upload-intent`).

### 1.2 Data Formats & Timestamps
- Attribute naming uses **`camelCase`** across all JSON request and response payloads.
- All timestamps are serialized as ISO-8601 strings in UTC format with second/millisecond precision: `YYYY-MM-DDTHH:mm:ss[.SSS]Z` (e.g., `"2026-10-14T10:15:30.123Z"`).
- Entity identifiers are standard RFC 4122 **UUIDv4** strings.

### 1.3 Authentication & Headers
- Authenticated endpoints require the standard HTTP Authorization header:
  ```http
  Authorization: Bearer <jwt_access_token>
  ```
- **Correlation ID:** Clients may send `X-Correlation-ID: <uuid>`. If omitted, the backend generates one and returns it in the response header for distributed tracing.
- **Idempotency Header:** For state-changing operations susceptible to network retries (specifically recommendation job dispatch), clients should supply:
  ```http
  Idempotency-Key: <unique-uuid>
  ```

### 1.4 Standard Error Response Schema
All error responses (4xx and 5xx) strictly follow this structure:

```json
{
  "timestamp": "2026-10-14T10:15:30.123Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_FAILED",
  "message": "One or more input fields failed validation constraints.",
  "path": "/api/v1/preferences",
  "details": [
    {
      "field": "hairType",
      "issue": "Must be one of: STRAIGHT, WAVY, CURLY, COILY"
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
- `CONSENT_REQUIRED`: Photo privacy consent has not been granted.
- `AI_SERVICE_UNAVAILABLE`: Upstream AI provider is temporarily unreachable or returned 5xx.
- `INTERNAL_SERVER_ERROR`: Unhandled exception caught by global safety wrapper.

### 1.5 Pagination Envelope
List endpoints support pagination via standard query parameters:
- `page`: 0-indexed page number (default: `0`).
- `size`: Items per page (default: `20`, max: `100`).
- `sort`: Sorting field and direction (e.g., `createdAt,desc`).

#### Paginated Response Envelope:
```json
{
  "content": [ ... ],
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 45,
  "totalPages": 3,
  "first": true,
  "last": false
}
```

---

## 2. Authentication & Session Endpoints

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
- **Validation Rules:**
  - `email`: Required, valid email format, max 255 chars.
  - `password`: Required, 8–100 chars, at least 1 uppercase, 1 lowercase, 1 digit, 1 special char.
  - `displayName`: Optional, 2–50 chars.
- **Responses:**
  - `201 Created`:
    ```json
    {
      "userId": "d290f1ee-6c54-4b01-90e6-d701748f0851",
      "email": "user@example.com",
      "displayName": "Alex Sharma",
      "accessToken": "eyJhbGciOiJIUzI1Ni...",
      "refreshToken": "rt_8f1498b8c9d2...",
      "expiresIn": 900
    }
    ```
  - `400 Bad Request`: Validation failure.
  - `409 Conflict`: Email already registered (`code: "EMAIL_ALREADY_EXISTS"`).

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
  - `200 OK`: Same payload as Register (User object + tokens).
  - `401 Unauthorized`: Invalid credentials (`code: "INVALID_CREDENTIALS"`).

### 2.3 Refresh Access Token
- **Method & Path:** `POST /api/v1/auth/refresh`
- **Authentication:** None
- **Request Body:**
  ```json
  {
    "refreshToken": "rt_8f1498b8c9d2..."
  }
  ```
- **Responses:**
  - `200 OK`:
    ```json
    {
      "accessToken": "eyJhbGciOiJIUzI1Ni...",
      "refreshToken": "rt_9a2234c7b1e4...",
      "expiresIn": 900
    }
    ```
  - `401 Unauthorized`: Token invalid, revoked, or expired.

### 2.4 Logout
- **Method & Path:** `POST /api/v1/auth/logout`
- **Authentication:** Bearer Token
- **Request Body:**
  ```json
  {
    "refreshToken": "rt_9a2234c7b1e4..."
  }
  ```
- **Responses:**
  - `204 No Content`: Refresh token revoked in database.

### 2.5 Get Current Authenticated User (`/me`)
- **Method & Path:** `GET /api/v1/auth/me`
- **Authentication:** Bearer Token
- **Responses:**
  - `200 OK`:
    ```json
    {
      "userId": "d290f1ee-6c54-4b01-90e6-d701748f0851",
      "email": "user@example.com",
      "displayName": "Alex Sharma",
      "createdAt": "2026-10-14T10:00:00Z"
    }
    ```

### 2.6 Forgot Password (Initiate Reset)
- **Method & Path:** `POST /api/v1/auth/forgot-password`
- **Authentication:** None (Public)
- **Request Body:**
  ```json
  {
    "email": "user@example.com"
  }
  ```
- **Validation Rules:**
  - `email`: Required, valid email format.
- **Behavior & Security:**
  - Generates a cryptographically secure random reset token (valid for 15 minutes).
  - Stores the SHA-256 hash in `password_reset_tokens`.
  - Dispatches reset link email via background email service.
  - **Always returns HTTP 200 OK** regardless of whether the email exists, preventing account enumeration.
- **Responses:**
  - `200 OK`:
    ```json
    {
      "message": "If the email is registered, a password reset link has been dispatched."
    }
    ```
  - `400 Bad Request`: Invalid email format.
  - `429 Too Many Requests`: Rate limit exceeded (e.g. max 5 requests per IP per hour).

### 2.7 Reset Password (Complete Reset)
- **Method & Path:** `POST /api/v1/auth/reset-password`
- **Authentication:** None (Public)
- **Request Body:**
  ```json
  {
    "token": "d7a4f91b-8c2e-4b19-91a0-123456789abc",
    "newPassword": "NewSecurePassword456!"
  }
  ```
- **Validation Rules:**
  - `token`: Required.
  - `newPassword`: Required, 8–100 chars, at least 1 uppercase, 1 lowercase, 1 digit, 1 special char.
- **Behavior & Security:**
  - Verifies token hash exists, `expires_at > NOW()`, and `is_used == FALSE`.
  - Updates `users.password_hash` with new Argon2id/BCrypt hash.
  - Marks reset token as used (`is_used = TRUE`).
  - Sets `users.tokens_invalid_before = NOW()` and revokes all active refresh tokens for the user in `refresh_tokens`.
- **Responses:**
  - `200 OK`:
    ```json
    {
      "message": "Password successfully reset. Please log in with your new password."
    }
    ```
  - `400 Bad Request`: Invalid, expired, or previously used token, or weak password (`code: "INVALID_RESET_TOKEN"`).

---

## 3. User Profile & Grooming Preferences Endpoints

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
      "photoConsentGranted": true,
      "photoConsentGrantedAt": "2026-10-14T10:05:00Z",
      "updatedAt": "2026-10-14T10:05:00Z"
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
- **Validation Rules:**
  - `ageBracket`: Optional enum (`TEENS`, `TWENTIES`, `THIRTIES`, `FORTIES`, `FIFTY_PLUS`).
- **Responses:**
  - `200 OK`: Updated profile payload.

### 3.3 Grant / Update Photo Processing Consent
- **Method & Path:** `PUT /api/v1/profile/consent`
- **Authentication:** Bearer Token
- **Request Body:**
  ```json
  {
    "consentGranted": true
  }
  ```
- **Responses:**
  - `200 OK`:
    ```json
    {
      "photoConsentGranted": true,
      "photoConsentGrantedAt": "2026-10-14T10:05:00Z"
    }
    ```

### 3.4 Get Grooming Preferences
- **Method & Path:** `GET /api/v1/preferences`
- **Authentication:** Bearer Token
- **Responses:**
  - `200 OK`:
    ```json
    {
      "hairType": "WAVY",
      "hairDensity": "MEDIUM",
      "facialHairPattern": "STUBBLE",
      "dailyStylingEffort": "MODERATE_5_TO_15_MIN",
      "workplaceEnvironment": "BUSINESS_CASUAL",
      "updatedAt": "2026-10-14T10:06:00Z"
    }
    ```

### 3.5 Update Grooming Preferences
- **Method & Path:** `PUT /api/v1/preferences`
- **Authentication:** Bearer Token
- **Request Body:**
  ```json
  {
    "hairType": "WAVY",
    "hairDensity": "MEDIUM",
    "facialHairPattern": "STUBBLE",
    "dailyStylingEffort": "MODERATE_5_TO_15_MIN",
    "workplaceEnvironment": "BUSINESS_CASUAL"
  }
  ```
- **Validation Rules:**
  - `hairType`: `STRAIGHT`, `WAVY`, `CURLY`, `COILY`.
  - `hairDensity`: `THIN`, `MEDIUM`, `THICK`.
  - `facialHairPattern`: `CLEAN_SHAVEN`, `STUBBLE`, `FULL_BEARD`, `PATCHY`, `GOATEE`.
  - `dailyStylingEffort`: `MINIMAL_UNDER_5_MIN`, `MODERATE_5_TO_15_MIN`, `EXTENSIVE_OVER_15_MIN`.
  - `workplaceEnvironment`: `CORPORATE_FORMAL`, `BUSINESS_CASUAL`, `CREATIVE_CASUAL`.
- **Responses:**
  - `200 OK`: Updated preferences object.

---

## 4. Photo Asset Lifecycle Endpoints

#### 4.1 Request Pre-Signed Upload Intent
- **Method & Path:** `POST /api/v1/photos/upload-intent`
- **Authentication:** Bearer Token
- **Request Body:**
  ```json
  {
    "contentType": "image/jpeg",
    "byteSize": 2840120
  }
  ```
- **Validation Rules:**
  - `contentType`: Required. Accepted values: `image/jpeg`, `image/png`. The value `image/heic` is **rejected** with HTTP `400 Bad Request` (code `UNSUPPORTED_IMAGE_FORMAT`). Client must transcode HEIC to JPEG using `expo-image-manipulator` before requesting an upload intent.
  - `byteSize`: Required, positive integer, maximum `10485760` (10MB).
  - Prerequisites: `photoConsentGranted` must be `true`.
  - **Format Policy:** Client-side JPEG normalization is the only supported format path for MVP. Server-side HEIC transcoding is not available and must not be assumed (see `ARCHITECTURE.md` Section 4.1.1).
- **Responses:**
  - `201 Created`:
    ```json
    {
      "photoId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "uploadUrl": "https://groomai-assets.s3.amazonaws.com/uploads/user-uuid/a1b2c3d4.jpg?X-Amz-Signature=...",
      "expiresAt": "2026-10-14T10:20:00Z",
      "storageKey": "uploads/user-uuid/a1b2c3d4.jpg",
      "httpMethod": "PUT",
      "requiredHeaders": {
        "Content-Type": "image/jpeg"
      }
    }
    ```
  - `403 Forbidden`: Consent not granted (`code: "CONSENT_REQUIRED"`).
  - `400 Bad Request`: Invalid MIME type or file size exceeds 10MB.

### 4.2 Confirm Photo Upload
- **Method & Path:** `POST /api/v1/photos/{photoId}/confirm`
- **Authentication:** Bearer Token
- **Path Parameters:**
  - `photoId`: UUID of the photo asset created in upload-intent.
- **Responses:**
  - `200 OK`:
    ```json
    {
      "photoId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "status": "ACTIVE",
      "contentType": "image/jpeg",
      "byteSize": 2840120,
      "confirmedAt": "2026-10-14T10:07:00Z"
    }
    ```
  - `400 Bad Request`: Asset not found in object storage or corrupted (`code: "OBJECT_VERIFICATION_FAILED"`).
  - `404 Not Found`: Photo asset record does not exist or belongs to another user.

### 4.3 Get Photo Asset Details & Read URL
- **Method & Path:** `GET /api/v1/photos/{photoId}`
- **Authentication:** Bearer Token
- **Responses:**
  - `200 OK`:
    ```json
    {
      "photoId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "status": "ACTIVE",
      "downloadUrl": "https://groomai-assets.s3.amazonaws.com/uploads/user-uuid/a1b2c3d4.jpg?X-Amz-Signature=...",
      "downloadUrlExpiresAt": "2026-10-14T10:25:00Z",
      "createdAt": "2026-10-14T10:06:00Z"
    }
    ```

### 4.4 Delete Photo Asset
- **Method & Path:** `DELETE /api/v1/photos/{photoId}`
- **Authentication:** Bearer Token
- **Behavior & Retention Policy:**
  - Enqueues cloud storage deletion in `storage_deletion_outbox` in the same database transaction as the photo-record deletion. The outbox has no user foreign key; its storage key is treated as sensitive operational data. A shared dispatcher and sweeper process the durable task.
  - Deletes the `photo_assets` database record.
  - Any historical recommendations that referenced this photo retain their structured text analysis, suitability scores, and Barber Cards; their `photo_asset_id` is set to `null` (`ON DELETE SET NULL`), and subsequent GET calls omit the image URL.
- **Responses:**
  - `204 No Content`: Photo asset deleted and cloud binary scheduled for eradication.
  - `404 Not Found`: Resource not found or not owned by user.

---

## 5. Recommendation Engine Endpoints

### 5.1 Dispatch Recommendation Job
- **Method & Path:** `POST /api/v1/recommendations/jobs`
- **Authentication:** Bearer Token
- **Headers:**
  - `Idempotency-Key`: Optional UUID string (recommended).
- **Request Body:**
  ```json
  {
    "photoId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
  }
  ```
- **Performance & Timeouts:**
  - The `estimatedWaitSeconds: 15` response attribute is a **provisional performance target**, measured strictly from job creation (`created_at`) to completion persistence (`completed_at`).
  - Bounded by a strict 30-second backend socket read timeout against the AI provider.
  - Client polling loops poll every 2.5s and timeout after 30 seconds with user retry guidance.
- **Responses:**
  - `202 Accepted`:
    ```json
    {
      "jobId": "f7a6b5c4-d3e2-1098-7654-3210fedcba98",
      "status": "PENDING",
      "photoId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "createdAt": "2026-10-14T10:10:00Z",
      "estimatedWaitSeconds": 15
    }
    ```
  - `400 Bad Request`: Photo is not in `ACTIVE` status or preferences incomplete.
  - `429 Too Many Requests`: Rate limit exceeded (e.g. > 5 jobs/hour).

### 5.2 Poll Recommendation Job Status
- **Method & Path:** `GET /api/v1/recommendations/jobs/{jobId}`
- **Authentication:** Bearer Token
- **Responses:**
  - `200 OK` (While In-Flight):
    ```json
    {
      "jobId": "f7a6b5c4-d3e2-1098-7654-3210fedcba98",
      "status": "PROCESSING",
      "createdAt": "2026-10-14T10:10:00Z",
      "updatedAt": "2026-10-14T10:10:04Z"
    }
    ```
  - `200 OK` (When Completed):
    ```json
    {
      "jobId": "f7a6b5c4-d3e2-1098-7654-3210fedcba98",
      "status": "COMPLETED",
      "recommendationId": "99887766-5544-3322-1100-aabbccddeeff",
      "completedAt": "2026-10-14T10:10:12Z"
    }
    ```
  - `200 OK` (When Failed):
    ```json
    {
      "jobId": "f7a6b5c4-d3e2-1098-7654-3210fedcba98",
      "status": "FAILED",
      "errorCode": "AI_INFERENCE_TIMEOUT",
      "errorMessage": "The analysis timed out. Your request has not deducted from your quota. Please retry.",
      "completedAt": "2026-10-14T10:10:35Z"
    }
    ```

### 5.3 Get Recommendation Report Details
- **Method & Path:** `GET /api/v1/recommendations/{recommendationId}`
- **Authentication:** Bearer Token
- **Responses:**
  - `200 OK`:
    ```json
    {
      "recommendationId": "99887766-5544-3322-1100-aabbccddeeff",
      "photoId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "photoDownloadUrl": "https://groomai-assets.s3.amazonaws.com/uploads/...jpg?...",
      "summaryObservation": "Balanced oval facial symmetry with dense, wavy hair and a full stubble growth pattern.",
      "disclaimer": "Recommendations are styling suggestions based on AI visual analysis and user preferences, not medical or dermatological assessments.",
      "createdAt": "2026-10-14T10:10:12Z",
      "options": [
        {
          "optionId": "opt-1111-2222-3333-4444",
          "category": "HAIRSTYLE",
          "styleName": "Textured Crop with Low Taper",
          "suitabilityScore": 94,
          "rationale": "Accentuates natural wavy texture while maintaining low daily styling commitment. The clean taper around the neckline complements jawline structure.",
          "barberInstructions": {
            "topLengthDescription": "2 to 2.5 inches on top, heavily point-cut for texture and separation",
            "sidesAndBackTechnique": "Low taper fade starting at a #1 guard blending cleanly into #2",
            "necklineFinish": "Natural tapered neckline, avoiding harsh block lines",
            "texturizingAdvice": "Scissor texturing throughout the crown; leave fringe lightly textured"
          },
          "maintenanceRoutine": {
            "dailyStylingTimeMinutes": 5,
            "productCategories": [
              "Matte Styling Clay",
              "Sea Salt Texture Spray"
            ],
            "applicationTechnique": "Towel dry hair, spray 2 pumps of sea salt spray, and work a dime-sized amount of matte clay from back to front.",
            "trimCadenceWeeks": 3
          }
        },
        {
          "optionId": "opt-5555-6666-7777-8888",
          "category": "BEARD",
          "styleName": "Defined Designer Stubble (3mm - 4mm)",
          "suitabilityScore": 92,
          "rationale": "Highlights cheekbone definition while keeping maintenance under 5 minutes twice weekly.",
          "barberInstructions": {
            "topLengthDescription": "Trimmed evenly to 3.5mm using guarded trimmer",
            "sidesAndBackTechnique": "Fade upper cheek line cleanly; avoid carving too low",
            "necklineFinish": "Clean neckline shaped 1 finger above Adam's apple",
            "texturizingAdvice": "Fade density lightly into sideburns"
          },
          "maintenanceRoutine": {
            "dailyStylingTimeMinutes": 3,
            "productCategories": [
              "Lightweight Beard Oil (Jojoba/Argan)"
            ],
            "applicationTechnique": "Apply 3 drops of beard oil immediately following morning shower to nourish skin.",
            "trimCadenceWeeks": 1
          }
        }
      ]
    }
    ```

### 5.4 List Historical Recommendations
- **Method & Path:** `GET /api/v1/recommendations`
- **Authentication:** Bearer Token
- **Query Parameters:** `page`, `size`, `sort`
- **Responses:**
  - `200 OK`: Standard paginated envelope containing summaries of past recommendations.

---

## 6. Saved Recommendations & Grooming Plan Endpoints

### 6.1 Bookmark / Save Recommendation Option
- **Method & Path:** `POST /api/v1/saved-recommendations`
- **Authentication:** Bearer Token
- **Request Body:**
  ```json
  {
    "optionId": "opt-1111-2222-3333-4444",
    "personalNotes": "Liked this for my next haircut on Friday."
  }
  ```
- **Responses:**
  - `201 Created`:
    ```json
    {
      "savedId": "sav-9988-7766-5544",
      "optionId": "opt-1111-2222-3333-4444",
      "styleName": "Textured Crop with Low Taper",
      "category": "HAIRSTYLE",
      "personalNotes": "Liked this for my next haircut on Friday.",
      "savedAt": "2026-10-14T10:15:00Z"
    }
    ```

### 6.2 List Saved Recommendations
- **Method & Path:** `GET /api/v1/saved-recommendations`
- **Authentication:** Bearer Token
- **Responses:**
  - `200 OK`: Array of saved recommendation cards with option details.

### 6.3 Remove Saved Recommendation
- **Method & Path:** `DELETE /api/v1/saved-recommendations/{savedId}`
- **Authentication:** Bearer Token
- **Responses:**
  - `204 No Content`: Bookmark deleted.

### 6.4 Get Active Grooming Plan
- **Method & Path:** `GET /api/v1/grooming-plan`
- **Authentication:** Bearer Token
- **Responses:**
  - `200 OK`:
    ```json
    {
      "planId": "plan-1234-5678-90ab",
      "activeHairstyle": {
        "savedId": "sav-9988-7766-5544",
        "styleName": "Textured Crop with Low Taper",
        "trimCadenceWeeks": 3,
        "lastCutDate": "2026-10-01"
      },
      "activeBeardStyle": {
        "savedId": "sav-2233-4455-6677",
        "styleName": "Defined Designer Stubble (3mm - 4mm)",
        "trimCadenceWeeks": 1,
        "lastTrimDate": "2026-10-12"
      },
      "routineItems": [
        {
          "itemId": "item-001",
          "title": "Apply Matte Clay to damp hair",
          "cadence": "DAILY",
          "isCompletedToday": true
        },
        {
          "itemId": "item-002",
          "title": "Apply 3 drops of beard oil",
          "cadence": "DAILY",
          "isCompletedToday": false
        },
        {
          "itemId": "item-003",
          "title": "Trim neckline and cheek lines",
          "cadence": "WEEKLY",
          "isCompletedToday": false
        }
      ]
    }
    ```

### 6.5 Update Active Grooming Plan Styles
- **Method & Path:** `PUT /api/v1/grooming-plan`
- **Authentication:** Bearer Token
- **Request Body:**
  ```json
  {
    "activeHairstyleSavedId": "sav-9988-7766-5544",
    "activeBeardStyleSavedId": "sav-2233-4455-6677"
  }
  ```
- **Responses:**
  - `200 OK`: Updated plan payload.

### 6.6 Toggle Checklist Item Completion
- **Method & Path:** `PATCH /api/v1/grooming-plan/items/{itemId}`
- **Authentication:** Bearer Token
- **Request Body:**
  ```json
  {
    "isCompleted": true
  }
  ```
- **Responses:**
  - `200 OK`: Updated checklist item.

---

## 7. Privacy, Data Export & Account Deletion Endpoints

### 7.1 Export User Data Archive
- **Method & Path:** `POST /api/v1/users/export`
- **Authentication:** Bearer Token
- **Responses:**
  - `200 OK`: Structured JSON containing complete user profile, preferences, recommendation histories, and saved plan items (excluding photo binary files, providing asset metadata only).

### 7.2 Permanent Account Deletion
- **Method & Path:** `DELETE /api/v1/users/account`
- **Authentication:** Bearer Token
- **Request Body:**
  ```json
  {
    "confirmationText": "DELETE MY ACCOUNT",
    "password": "SecurePassword123!"
  }
  ```
- **Execution Workflow:**
  1. Verifies password and confirmation text.
  2. Within a **single `@Transactional` database transaction:**
     - Cancels active in-flight recommendation jobs for the user.
     - Extracts all photo `storage_key` values and inserts them into `storage_deletion_outbox` (`status = 'PENDING'`, `attempts = 0`).
     - Executes `DELETE FROM users WHERE id = :userId`, cascading across **11 dependent tables** (9 direct, 2 transitive — see `DATA_MODEL.md` Section 4).
  3. On transaction commit, returns **HTTP `204 No Content`** immediately. All subsequent requests with existing access tokens fail (`401 Unauthorized`) because the user row no longer exists. The `< 500ms` response target is **provisional until validated under production load**.
  4. **After transaction commit**, cloud deletion is dispatched asynchronously. If dispatch fails to run, the due outbox row remains eligible for the one-minute sweeper. Dispatcher and sweeper workers atomically claim rows; expired two-minute processing leases are recoverable. Retry delays after failed attempts are 15 minutes, 1 hour, 6 hours, and 16 hours 45 minutes. After five failed claimed attempts, an alert is emitted and the task remains in `FAILED` until an operator re-drives it or verifies that the object is already absent. Unverified tasks are **never purged automatically**.
- **Responses:**
  - `204 No Content`: Account and all relational records permanently erased; cloud binaries tracked in outbox for asynchronous verified eradication.
  - `400 Bad Request`: Incorrect confirmation text or password.

---

## 8. System & Health Endpoints

### 8.1 Public Service Health Check
- **Method & Path:** `GET /api/v1/health`
- **Authentication:** None (Public)
- **Responses:**
  - `200 OK`:
    ```json
    {
      "status": "UP",
      "service": "groomai-backend",
      "timestamp": "2026-10-14T10:15:30.123Z"
    }
    ```

### 8.2 Actuator Detailed Health (Protected or Internal)
- **Method & Path:** `GET /actuator/health`
- **Authentication:** Internal / Restricted
- **Responses:**
  - `200 OK`:
    ```json
    {
      "status": "UP",
      "components": {
        "db": {
          "status": "UP",
          "details": {
            "database": "PostgreSQL",
            "validationQuery": "isValid()"
          }
        },
        "diskSpace": {
          "status": "UP"
        }
      }
    }
    ```
