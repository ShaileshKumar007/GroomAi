# Groom.ai — Requirements Specification & Traceability Matrix

**Document Version:** 1.1.0  
**Status:** Draft — Proposed Specification (Pending Stakeholder Approval)  
**Last Updated:** October 2026  

---

## 1. Requirements Overview & Priority Definitions

This document details all functional and non-functional requirements for Groom.ai, establishing priority ratings, dependencies, measurable verification criteria, and a cross-cutting traceability matrix.

### Priority Taxonomy
- **P0 (Must-Have / MVP Baseline):** Critical capabilities required for a functional, secure, and viable first release. Without these, the product cannot launch.
- **P1 (Should-Have / Fast Follow):** High-value capabilities that improve user retention, UX resilience, or operational safety. Targeted for early updates post-MVP launch.
- **P2 (Could-Have / Future Scope):** Enhancements and advanced features deferred to subsequent product iterations.

---

## 2. Functional Requirements (FR)

### Module: Authentication & Session Management (FR-AUTH)

#### FR-001: User Registration
- **Priority:** P0
- **Dependencies:** None
- **Description:** System shall allow new users to register using an email address, a password meeting complexity rules (minimum 8 characters, at least one uppercase, one lowercase, one number, and one symbol), and an optional display name.
- **Acceptance Criteria:**
  1. Valid registration creates a user record in `users` with password hashed using Argon2id or BCrypt.
  2. Duplicate email registrations are rejected with a standardized `409 Conflict` error (`EMAIL_ALREADY_EXISTS`).
  3. Returns an initial JWT access token and refresh token.

#### FR-002: User Login & Session Handling
- **Priority:** P0
- **Dependencies:** FR-001
- **Description:** System shall authenticate users via email and password, returning short-lived access tokens (15-minute validity) and rotating refresh tokens (30-day validity).
- **Acceptance Criteria:**
  1. Incorrect credentials return `401 Unauthorized` without specifying whether email or password was invalid (`INVALID_CREDENTIALS`).
  2. Successful login returns a valid JWT bearer token with claims (`sub`, `email`, `role`, `exp`).
  3. Calling `POST /api/v1/auth/refresh` with a valid refresh token issues a new access token and rotates the refresh token.

#### FR-003: User Logout & Token Revocation
- **Priority:** P0
- **Dependencies:** FR-002
- **Description:** System shall allow authenticated users to terminate their session, revoking the active refresh token.
- **Acceptance Criteria:**
  1. `POST /api/v1/auth/logout` marks the corresponding refresh token in the database as revoked (`is_revoked = TRUE`).
  2. Revoked refresh tokens cannot be used to generate new access tokens.
  3. Mobile client purges tokens from `Expo SecureStore`.

#### FR-004: Password Reset Workflow
- **Priority:** P1
- **Dependencies:** FR-001
- **Description:** System shall provide a complete password recovery workflow allowing users to request a password reset link sent to their registered email address and submit a new password.
- **Acceptance Criteria:**
  1. `POST /api/v1/auth/forgot-password` always returns `200 OK` regardless of whether the email is registered (preventing email enumeration).
  2. For registered emails, a cryptographically secure token is stored in `password_reset_tokens` (15-minute TTL) and dispatched via email.
  3. `POST /api/v1/auth/reset-password` validates the token, updates `users.password_hash`, marks the token as used, updates `tokens_invalid_before = NOW()`, and invalidates all existing refresh tokens for the user.

---

### Module: User Profile & Grooming Preferences (FR-PROF)

#### FR-005: User Profile Data Management
- **Priority:** P0
- **Dependencies:** FR-001
- **Description:** System shall store and retrieve personal profile attributes including display name, age bracket, and profile creation metadata.
- **Acceptance Criteria:**
  1. `GET /api/v1/profile` retrieves the authenticated user's current profile.
  2. `PUT /api/v1/profile` allows updating display name and age bracket with input validation.

#### FR-006: Grooming Preferences Questionnaire
- **Priority:** P0
- **Dependencies:** FR-005
- **Description:** System shall capture structured grooming constraints:
  - Hair Type: `STRAIGHT`, `WAVY`, `CURLY`, `COILY`
  - Hair Density: `THIN`, `MEDIUM`, `THICK`
  - Facial Hair Pattern: `CLEAN_SHAVEN`, `STUBBLE`, `FULL_BEARD`, `PATCHY`, `GOATEE`
  - Daily Styling Effort: `MINIMAL_UNDER_5_MIN`, `MODERATE_5_TO_15_MIN`, `EXTENSIVE_OVER_15_MIN`
  - Workplace Environment: `CORPORATE_FORMAL`, `BUSINESS_CASUAL`, `CREATIVE_CASUAL`
- **Acceptance Criteria:**
  1. Values strictly conform to defined enumerations.
  2. Preferences can be updated at any time via `PUT /api/v1/preferences`.

---

### Module: Photo Management & Ingestion (FR-PHOTO)

#### FR-007: Explicit Photo Privacy Consent
- **Priority:** P0
- **Dependencies:** FR-005
- **Description:** System shall require explicit user consent acknowledging photo processing by AI and private cloud storage before permitting photo capture or upload.
- **Acceptance Criteria:**
  1. Backend records `photoConsentGrantedAt` timestamp in `user_profiles`.
  2. Recommendation and upload endpoints reject requests if consent has not been recorded (`code: "CONSENT_REQUIRED"`).

#### FR-008: In-App Camera Capture
- **Priority:** P0
- **Dependencies:** FR-007
- **Description:** Mobile app shall offer an in-app camera viewfinder with an overlay guide (oval face guide) to assist the user in framing a clear, well-lit, front-facing portrait.
- **Acceptance Criteria:**
  1. App requests camera permissions gracefully.
  2. Captured image is held in temporary local cache for user confirmation before upload.

#### FR-009: Photo Gallery Selection
- **Priority:** P0
- **Dependencies:** FR-007
- **Description:** Mobile app shall allow users to select an existing portrait photo from their device photo library.
- **Acceptance Criteria:**
  1. App requests media library permissions.
  2. User can crop or confirm the selected photo.

#### FR-010: Client-Side Pre-validation & Format Normalization
- **Priority:** P0
- **Dependencies:** FR-008, FR-009
- **Description:** Mobile client shall validate image file format and file size (maximum 10MB) before requesting an upload URL. HEIC images must be transcoded to JPEG locally using `expo-image-manipulator` before the upload-intent request is made. The backend rejects `image/heic` uploads.
- **Acceptance Criteria:**
  1. Files larger than 10MB are rejected with an actionable on-screen message before any network request.
  2. Photos in JPEG or PNG format are accepted as-is after size validation.
  3. Photos in HEIC format are transcoded to JPEG on-device before upload intent is requested.
  4. `POST /api/v1/photos/upload-intent` with `contentType: "image/heic"` returns HTTP `400 Bad Request` (code `UNSUPPORTED_IMAGE_FORMAT`). The backend does not attempt server-side HEIC transcoding for MVP (see `ARCHITECTURE.md` Section 4.1.1 for the pending implementation status of server-side HEIC handling).

#### FR-011: Pre-Signed Upload URL Generation
- **Priority:** P0
- **Dependencies:** FR-010
- **Description:** Backend shall generate a short-lived pre-signed PUT URL targeting a private cloud storage bucket.
- **Acceptance Criteria:**
  1. `POST /api/v1/photos/upload-intent` creates a `photo_assets` record with status `PENDING_UPLOAD`.
  2. Pre-signed URL has a strict expiration of 15 minutes and enforces content-type headers.

#### FR-012: Upload Confirmation & Asset Activation
- **Priority:** P0
- **Dependencies:** FR-011
- **Description:** Following direct upload from mobile to object storage, the client notifies the backend to confirm and activate the photo asset.
- **Acceptance Criteria:**
  1. `POST /api/v1/photos/{id}/confirm` triggers backend object storage verification (`HeadObject`).
  2. Upon successful verification, photo status transitions to `ACTIVE`.

#### FR-013: Photo Asset Deletion
- **Priority:** P0
- **Dependencies:** FR-012
- **Description:** Users shall be able to delete any previously uploaded photo asset while retaining historical recommendation text.
- **Acceptance Criteria:**
  1. `DELETE /api/v1/photos/{id}` verifies user ownership.
  2. Backend marks database record as deleted and enqueues object storage deletion into `storage_deletion_outbox`.
  3. Historical recommendations referencing this photo retain their styling rationale and Barber Cards with `photo_asset_id` set to `null` (`ON DELETE SET NULL`).

---

### Module: AI Recommendation Engine (FR-REC)

#### FR-014: Asynchronous Recommendation Job Creation
- **Priority:** P0
- **Dependencies:** FR-006, FR-012
- **Description:** Authenticated users shall be able to trigger a recommendation analysis job for an active photo asset.
- **Acceptance Criteria:**
  1. `POST /api/v1/recommendations/jobs` creates a `recommendation_jobs` record with status `PENDING` and returns **HTTP `202 Accepted`**.
  2. Accepts an optional `Idempotency-Key` header to prevent duplicate job executions within 60 seconds.
  3. Job execution targets completion within ≤ 15 seconds (provisional target, measured from `created_at` to `completed_at`), bounded by an explicit 30-second read timeout.

#### FR-015: Job Status & Polling Retrieval
- **Priority:** P0
- **Dependencies:** FR-014
- **Description:** Mobile client can poll the status of a recommendation job.
- **Acceptance Criteria:**
  1. `GET /api/v1/recommendations/jobs/{jobId}` returns status: `PENDING`, `PROCESSING`, `COMPLETED`, or `FAILED`.
  2. When `COMPLETED`, response includes the created `recommendationId`.
  3. When `FAILED`, response includes a user-friendly error code and reason.

#### FR-016: Multimodal Prompt Orchestration & AI Invocation
- **Priority:** P0
- **Dependencies:** FR-014
- **Description:** Backend shall construct a structured multimodal prompt combining the user's secure photo URL with their declared hair texture, density, facial hair pattern, and styling constraints.
- **Acceptance Criteria:**
  1. The prompt instructs the AI provider to return strictly structured JSON matching the application schema.
  2. The prompt includes safety caveats: avoid medical diagnoses, treat face shape assessments as helpful suggestions rather than rigid facts.

#### FR-017: Server-Side AI Output Validation
- **Priority:** P0
- **Dependencies:** FR-016
- **Description:** Backend shall strictly validate the structured JSON received from the AI provider against schema constraints before writing to the database.
- **Acceptance Criteria:**
  1. Malformed JSON or missing required fields triggers a single automated retry or transitions job to `FAILED` with `AI_OUTPUT_UNAVAILABLE`.
  2. Validated payload is normalized into `recommendations` and `recommendation_options` tables.

#### FR-018: Structured Hairstyle & Beard Recommendations
- **Priority:** P0
- **Dependencies:** FR-017
- **Description:** Each successful recommendation must provide:
  - 2 to 3 distinct hairstyle options.
  - 1 to 2 distinct beard/facial hair styling options.
  - Overall facial feature observations presented as style-matching advice.
- **Acceptance Criteria:**
  1. Each option contains: `styleName`, `category` (`HAIRSTYLE` or `BEARD`), `suitabilityScore` (1-100), `rationale`, `barberInstructions`, and `maintenanceRoutine`.

#### FR-019: Barber Instruction Card
- **Priority:** P0
- **Dependencies:** FR-018
- **Description:** Each recommendation option shall provide concise, technical instructions intended for a barber or stylist.
- **Acceptance Criteria:**
  1. Includes: `topLengthDescription`, `sidesAndBackTechnique` (e.g., "Low taper fade #1 into #2"), `necklineFinish`, and `texturizingAdvice`.

#### FR-020: Maintenance Routine & Product Recommendations
- **Priority:** P0
- **Dependencies:** FR-018
- **Description:** Each recommendation option shall provide practical maintenance advice.
- **Acceptance Criteria:**
  1. Includes: `dailyStylingTimeMinutes`, `genericProductCategories` (e.g., "Matte Styling Clay"), `applicationTechnique`, and `trimCadenceWeeks`.

#### FR-021: Recommendation History Listing
- **Priority:** P0
- **Dependencies:** FR-018
- **Description:** Users can view a chronological list of their previous recommendation reports.
- **Acceptance Criteria:**
  1. `GET /api/v1/recommendations` returns a paginated list of past recommendations with photo references (or null if photo was deleted).

---

### Module: Grooming Plan & Saved Styles (FR-PLAN)

#### FR-022: Bookmark / Save Recommendation Option
- **Priority:** P0
- **Dependencies:** FR-018
- **Description:** Users can bookmark any recommended hairstyle or beard style to their saved collection.
- **Acceptance Criteria:**
  1. `POST /api/v1/saved-recommendations` saves the style option to the user's account.
  2. `DELETE /api/v1/saved-recommendations/{id}` removes the bookmark.

#### FR-023: Active Grooming Plan View
- **Priority:** P0
- **Dependencies:** FR-022
- **Description:** System provides an active Grooming Plan dashboard displaying the user's currently selected hairstyle, beard style, and aggregated maintenance checklist.
- **Acceptance Criteria:**
  1. `GET /api/v1/grooming-plan` returns the active plan.
  2. `PUT /api/v1/grooming-plan` allows setting the primary active hairstyle and beard style from saved options.

#### FR-024: Interactive Maintenance Checklist
- **Priority:** P1
- **Dependencies:** FR-023
- **Description:** Users can toggle routine grooming checklist items (e.g., "Apply matte clay", "Trim neckline") within their Grooming Plan.
- **Acceptance Criteria:**
  1. `PATCH /api/v1/grooming-plan/items/{itemId}` toggles completion state.

---

### Module: Privacy, Compliance & Account Deletion (FR-PRIV)

#### FR-025: Complete Account Deletion
- **Priority:** P0
- **Dependencies:** FR-001, FR-012
- **Description:** Users shall be able to permanently delete their account and all associated personal data directly from the mobile app settings.
- **Acceptance Criteria:**
  1. `DELETE /api/v1/users/account` requires password re-confirmation.
  2. Within a single `@Transactional` database transaction: in-flight recommendation jobs are cancelled; all photo storage keys are inserted into `storage_deletion_outbox` (`status = 'PENDING'`, `attempts = 0`, `next_attempt_at = NOW()`); `DELETE FROM users WHERE id = :userId` cascades across all 11 dependent tables.
  3. On transaction commit, backend returns **HTTP `204 No Content`** (provisional target: < 500ms until measured).
  4. Subsequent requests with existing JWT access tokens fail immediately (`401 Unauthorized`) via user-existence check.
  5. A post-commit dispatcher attempts cloud deletion, while the durable row remains recoverable by a one-minute sweeper if dispatch does not run. An atomic claim prevents concurrent workers from processing the same row. Failed attempts are retried after 15 minutes, 1 hour, 6 hours, and 16 hours 45 minutes (four retry delays totalling 24 hours). Expired processing leases are recoverable.
  6. After 5 failed claimed attempts, `STORAGE_DELETION_DEAD_LETTER` is emitted and automated retries halt. Unverified deletion tasks remain in `storage_deletion_outbox` until deletion is verified or an operator resolves the task; they are **never automatically purged** based on age.

#### FR-026: User Data Export
- **Priority:** P1
- **Dependencies:** FR-005, FR-018
- **Description:** Users can request a download of their profile data, preferences, and recommendation history as a structured JSON file.
- **Acceptance Criteria:**
  1. `POST /api/v1/users/export` returns a JSON object containing the user's non-binary personal data.

---

### Module: System Health & Diagnostics (FR-SYS)

#### FR-027: System Health & Diagnostic Ping
- **Priority:** P0
- **Dependencies:** None
- **Description:** Backend shall expose a public health check endpoint reflecting service and database health.
- **Acceptance Criteria:**
  1. `GET /api/v1/health` returns `200 OK` with JSON status `"UP"`.

---

## 3. Non-Functional Requirements (NFR)

| ID | Category | Requirement Statement | Verification Method | Priority |
| :--- | :--- | :--- | :--- | :--- |
| **NFR-001** | Performance | Standard REST endpoints (profile, auth, plan) shall respond within **300ms** at the 95th percentile under normal operational load (< 50 req/s). *(Provisional target)* | Load testing with k6 / Gatling | P0 |
| **NFR-002** | Performance | **AI Recommendation Latency (Provisional):** Recommendation job processing targets completion within **≤ 15 seconds** at the 90th percentile under normal upstream provider conditions. **Measurement Boundary:** Measured strictly from backend job record creation (`created_at`) to completion persistence (`completed_at`). Dominant factor is upstream provider latency. Handled with explicit 30s backend socket read timeout (marks job `FAILED` with `AI_INFERENCE_TIMEOUT`, no quota deduction) and 30s mobile client polling timeout with user retry guidance. | Backend job duration metric telemetry; client timeout at 30s | P0 |
| **NFR-003** | Security | All client-to-backend and backend-to-external communication must enforce **TLS 1.3** (minimum TLS 1.2). | Automated SSL Labs / cipher suite inspection | P0 |
| **NFR-004** | Security | Cryptographic passwords must be hashed using **Argon2id** or **BCrypt** (cost factor ≥ 12) with unique cryptographic salts. | Unit & security audit test | P0 |
| **NFR-005** | Security | Every endpoint handling user assets must enforce **strict server-side tenancy verification** (`userId == token.sub`). Insecure Direct Object References (IDOR) must result in 404/403. | Automated security regression test suite | P0 |
| **NFR-006** | Privacy | Photos must be stored in private object storage with **public read access completely blocked**. Viewing URLs must be pre-signed with a TTL of **≤ 15 minutes**. | Storage bucket policy audit script | P0 |
| **NFR-007** | Privacy | Structured logs must **never contain biometric photo data**, raw image URLs, JWT tokens, or passwords. | Log sanitization filter unit tests | P0 |
| **NFR-008** | Reliability | The Spring Boot backend must handle external AI provider timeouts (30s timeout) and rate limits (429) gracefully without crashing or leaking unhandled 500 errors. | Fault injection / WireMock integration tests | P0 |
| **NFR-009** | Reliability | Schema changes must be managed strictly through **Flyway versioned migrations**; zero manual DDL execution. | Fresh Docker DB initialization in CI | P0 |
| **NFR-010** | Mobile UX | The mobile client must handle intermittent connectivity gracefully, showing an offline banner and retry states without crashing. | Mobile network throttling & offline tests | P0 |
| **NFR-011** | Mobile UX | Mobile app must comply with **WCAG 2.1 AA** contrast guidelines and support system-level dynamic type / font scaling. | Accessibility audit via Expo tools | P1 |
| **NFR-012** | Maintainability| Backend test coverage across domain modules (`service`, `validator`) must exceed **80% line coverage**. | JaCoCo code coverage report in CI | P0 |

---

## 4. Traceability Matrix

| Requirement ID | User Story ID | Primary API Operation | Verification / Test Scenario | Milestone |
| :--- | :--- | :--- | :--- | :--- |
| **FR-001** | US-001 | `POST /api/v1/auth/register` | `AuthServiceTest#register_ValidInput_Success`, `AuthServiceTest#register_DuplicateEmail_Conflict` | Phase 1 |
| **FR-002** | US-001 | `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh` | `TokenProviderTest#generateToken`, `AuthIntegrationTest#login_InvalidPassword_401` | Phase 1 |
| **FR-003** | US-001 | `POST /api/v1/auth/logout` | `AuthIntegrationTest#logout_RevokesRefreshToken` | Phase 1 |
| **FR-004** | US-001 | `POST /api/v1/auth/forgot-password`, `POST /api/v1/auth/reset-password` | `AuthServiceTest#forgotPassword_DispatchesEmail`, `AuthServiceTest#resetPassword_ValidToken_Success` | Phase 5 |
| **FR-005** | US-001 | `GET /api/v1/profile`, `PUT /api/v1/profile` | `UserProfileControllerTest#updateProfile_ValidData` | Phase 2 |
| **FR-006** | US-001 | `GET /api/v1/preferences`, `PUT /api/v1/preferences` | `PreferencesControllerTest#updatePreferences_ValidEnums` | Phase 2 |
| **FR-007** | US-002 | `PUT /api/v1/profile/consent` | `UserProfileServiceTest#recordPhotoConsent` | Phase 2 |
| **FR-008** | US-002 | Mobile Native Camera Component | Client UI test: Camera permission grant & preview render | Phase 2 |
| **FR-009** | US-002 | Mobile Native Picker Component | Client UI test: Gallery picker selection & crop | Phase 2 |
| **FR-010** | US-002 | Mobile Client Pre-validator | Client Unit Test: Rejection of >10MB files and non-image types | Phase 2 |
| **FR-011** | US-002 | `POST /api/v1/photos/upload-intent` | `PhotoServiceTest#createUploadIntent_GeneratesPresignedUrl` | Phase 2 |
| **FR-012** | US-002 | `POST /api/v1/photos/{id}/confirm` | `PhotoServiceTest#confirmUpload_StorageVerification` | Phase 2 |
| **FR-013** | US-005 | `DELETE /api/v1/photos/{id}` | `PhotoServiceTest#deletePhoto_PurgesStorageAndSetsNull` | Phase 2 |
| **FR-014** | US-003 | `POST /api/v1/recommendations/jobs` | `RecJobServiceTest#createJob_Returns202Accepted` | Phase 3 |
| **FR-015** | US-003 | `GET /api/v1/recommendations/jobs/{jobId}` | `RecJobControllerTest#pollJobStatus_TransitionsToCompleted` | Phase 3 |
| **FR-016** | US-003 | AI Provider Client Adapter | `AiProviderAdapterTest#buildMultimodalPrompt`, WireMock AI call | Phase 3 |
| **FR-017** | US-003 | AI Response Schema Validator | `AiOutputValidatorTest#validateSchema_RejectsMalformedJson` | Phase 3 |
| **FR-018** | US-003 | `GET /api/v1/recommendations/{id}` | `RecControllerTest#getRecommendation_ReturnsFullPayload` | Phase 3 |
| **FR-019** | US-004 | `GET /api/v1/recommendations/{id}` | UI snapshot test: Barber card rendering guard numbers | Phase 3 |
| **FR-020** | US-004 | `GET /api/v1/recommendations/{id}` | UI snapshot test: Maintenance routine checklist | Phase 3 |
| **FR-021** | US-003 | `GET /api/v1/recommendations` | `RecControllerTest#listRecommendations_Pagination` | Phase 3 |
| **FR-022** | US-004 | `POST /api/v1/saved-recommendations` | `PlanServiceTest#bookmarkRecommendationOption` | Phase 4 |
| **FR-023** | US-004 | `GET /api/v1/grooming-plan`, `PUT /api/v1/grooming-plan` | `PlanControllerTest#getGroomingPlan_PopulatedPlan` | Phase 4 |
| **FR-024** | US-004 | `PATCH /api/v1/grooming-plan/items/{id}` | `PlanControllerTest#toggleChecklistItem` | Phase 4 |
| **FR-025** | US-005 | `DELETE /api/v1/users/account` | `UserServiceTest#deleteAccount_CascadesAndEnqueuesOutbox` | Phase 5 |
| **FR-026** | US-005 | `POST /api/v1/users/export` | `UserServiceTest#exportUserData_ReturnsCompleteJson` | Phase 5 |
| **FR-027** | US-001 | `GET /api/v1/health` | `HealthCheckTest#publicHealthPing_Returns200` | Phase 1 |
