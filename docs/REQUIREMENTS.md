# Groom.ai — Requirements Specification & Traceability Matrix

**Document Version:** 2.0.0  
**Status:** Approved Specification (Wardrobe-First Evolution)  
**Last Updated:** October 2026  

---

## 1. Requirements Overview & Priority Definitions

This document details all functional and non-functional requirements for Groom.ai, establishing priority ratings, dependencies, measurable verification criteria, and a cross-cutting traceability matrix reflecting the approved wardrobe-first personal stylist architecture.

### Priority Taxonomy
- **P0 (Must-Have / MVP Baseline):** Critical capabilities required for a functional, secure, and viable product release. Without these, the product cannot launch.
- **P1 (Should-Have / Fast Follow):** High-value capabilities that improve user retention, UX resilience, or operational safety. Targeted for early updates post-MVP launch.
- **P2 (Could-Have / Future Scope):** Enhancements and advanced features deferred to subsequent product iterations (e.g. gap analysis, generative try-on, grooming expansion).

---

## 2. Functional Requirements (FR)

### Module: Authentication & Session Management (FR-AUTH)

#### FR-001: User Registration
- **Priority:** P0 (Implemented & Verified)
- **Dependencies:** None
- **Description:** System shall allow new users to register using an email address, a password meeting complexity rules (minimum 8 characters, at least one uppercase, one lowercase, one number, and one symbol), and an optional display name.
- **Acceptance Criteria:**
  1. Valid registration creates a user record in `users` with password hashed using BCrypt (strength 12).
  2. Duplicate email registrations are rejected with a standardized `409 Conflict` error (`EMAIL_ALREADY_EXISTS`).
  3. Returns an initial JWT access token and rotating refresh token.

#### FR-002: User Login & Session Handling
- **Priority:** P0 (Implemented & Verified)
- **Dependencies:** FR-001
- **Description:** System shall authenticate users via email and password, returning short-lived access tokens (15-minute validity) and rotating refresh tokens (30-day validity).
- **Acceptance Criteria:**
  1. Incorrect credentials return `401 Unauthorized` without specifying whether email or password was invalid (`INVALID_CREDENTIALS`).
  2. Successful login returns a valid JWT bearer token with claims (`sub`, `email`, `role`, `exp`).
  3. Calling `POST /api/v1/auth/refresh` with a valid refresh token issues a new access token and rotates the refresh token.

#### FR-003: User Logout & Token Revocation
- **Priority:** P0 (Implemented & Verified)
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

### Module: User Profile, Consents & Style Preferences (FR-PROF)

#### FR-005: User Profile Data Management
- **Priority:** P0
- **Dependencies:** FR-001
- **Description:** System shall store and retrieve personal profile attributes including display name and age bracket.
- **Acceptance Criteria:**
  1. `GET /api/v1/profile` retrieves the authenticated user's current profile.
  2. `PUT /api/v1/profile` allows updating display name and age bracket with input validation.

#### FR-006: Style Preferences Management
- **Priority:** P0
- **Dependencies:** FR-005
- **Description:** System shall capture structured user styling preferences:
  - Primary Style: `CASUAL`, `SMART_CASUAL`, `MINIMALIST`, `CLASSIC`, `STREETWEAR`, `PREPPY`, `ATHLEISURE`
  - Preferred Fit: `SLIM`, `REGULAR`, `RELAXED`, `OVERSIZED`
  - Color Preferences: JSON array of preferred color enums
  - Avoided Colors: JSON array of avoided color enums
  - Default Formality: `CASUAL`, `SMART_CASUAL`, `BUSINESS_CASUAL`, `FORMAL`
- **Acceptance Criteria:**
  1. Values strictly conform to defined enumerations.
  2. Preferences can be updated at any time via `PUT /api/v1/profile/style-preferences`.

#### FR-007: Dual Decoupled Privacy Consent
- **Priority:** P0
- **Dependencies:** FR-005
- **Description:** System shall enforce two distinct, unbundled consents recorded in `user_profiles`:
  1. `wardrobe_storage_consent_granted`: Authorizes storing user clothing photos in private cloud storage.
  2. `ai_styling_consent_granted`: Authorizes passing wardrobe item metadata and imagery to external AI models for recommendation rationale.
- **Acceptance Criteria:**
  1. Upload intent generation rejects requests if wardrobe storage consent is false (`code: "CONSENT_REQUIRED"`).
  2. Deterministic outfit generation remains available when AI styling consent is false. In that state, the system must not send wardrobe data to an external AI provider and must omit AI-generated rationale/tips.
  3. Consents can be queried via `GET /api/v1/profile` and updated via `PUT /api/v1/profile/consents`.
  4. Legacy `photo_consent_granted` is preserved in the schema for portrait photos but is never repurposed or bundled.

---

### Module: Digital Wardrobe Management (FR-WARD)

#### FR-008: Wardrobe Photo Capture & Normalization
- **Priority:** P0
- **Dependencies:** FR-007
- **Description:** Mobile client shall capture clothing photos with device camera or pick from gallery, normalizing all photos to standard JPEG under 10MB using `expo-image-manipulator` prior to requesting upload intents.
- **Acceptance Criteria:**
  1. Non-standard formats (e.g., iOS HEIC) are transcoded to JPEG on-device.
  2. Backend upload intent endpoint rejects `contentType: "image/heic"` with `400 Bad Request` (`UNSUPPORTED_IMAGE_FORMAT`).
  3. Maximum allowable file size is 10MB.

#### FR-009: Pre-Signed Upload Intent & Verification
- **Priority:** P0
- **Dependencies:** FR-007, FR-008
- **Description:** Backend shall generate short-lived pre-signed upload URLs for private cloud storage (or local dev storage), verifying asset ownership upon upload confirmation.
- **Acceptance Criteria:**
  1. `POST /api/v1/wardrobe/upload-intent` verifies `wardrobe_storage_consent_granted == true` and returns ephemeral upload URL (TTL ≤ 15 min).
  2. Mobile client uploads binary directly to storage.
  3. Subsequent item creation verifies storage object existence and MIME type before activating item.

#### FR-010: Lightweight Wardrobe Item Model
- **Priority:** P0
- **Dependencies:** FR-009
- **Description:** System shall record item metadata with minimal required user input:
  - **Required:** `name`, `category` (`TOP`, `BOTTOM`, `FOOTWEAR`, `OUTERWEAR`, `ACCESSORY`), `primaryColor`, and `storageKey`.
  - **Optional with defaults:** `formalityLevel` (default: `CASUAL`), `pattern` (default: `SOLID`), `subcategory` (nullable), `secondaryColor` (nullable), `season` (default: `ALL_SEASON`).
- **Acceptance Criteria:**
  1. `POST /api/v1/wardrobe/items` creates item and persists to `wardrobe_items`.
  2. Detailed fabric weight, micro-seasonality, and brand fields are deferred beyond MVP.

#### FR-011: Wardrobe Catalog Browsing & Filter
- **Priority:** P0
- **Dependencies:** FR-010
- **Description:** Users shall be able to browse their digital wardrobe items with filters for category, formality, season, and primary color.
- **Acceptance Criteria:**
  1. `GET /api/v1/wardrobe/items` returns paginated list of active items.
  2. Each item payload includes a fresh pre-signed download URL (TTL ≤ 15 min).

#### FR-012: Wardrobe Item Edit & Deletion
- **Priority:** P0
- **Dependencies:** FR-010
- **Description:** Users shall be able to edit wardrobe item details or delete an item from their catalog.
- **Acceptance Criteria:**
  1. `PUT /api/v1/wardrobe/items/{id}` updates item attributes.
  2. `DELETE /api/v1/wardrobe/items/{id}` enqueues item storage key to `storage_deletion_outbox` in the same transaction that deletes the `wardrobe_items` row.
  3. Historical outfits referencing the deleted item retain their text analysis with item reference set to null (`ON DELETE SET NULL`).

---

### Module: Outfit Recommendation & Styling Engine (FR-OUTFIT)

#### FR-013: Deterministic Outfit Generator (Stage 1)
- **Priority:** P0
- **Dependencies:** FR-010
- **Description:** System shall evaluate the user's active wardrobe items using deterministic grammar rules to generate valid outfit combinations in < 100ms without calling external AI models.
- **Acceptance Criteria:**
  1. Category Completeness: Generates combinations containing exactly 1 Top + 1 Bottom + 1 Footwear (+ optional Outerwear if weather is cold/rainy; + optional Accessories).
  2. Formality Filtering: All selected items must fall within acceptable formality bounds for the requested occasion.
  3. Color Harmony: Evaluates color combinations using color theory rules (monochrome, complementary, neutral anchors).
  4. Requires at least 1 Top, 1 Bottom, 1 Footwear in wardrobe; returns `INSUFFICIENT_WARDROBE_ITEMS` if incomplete.

#### FR-014: Multimodal AI Styling Rationale (Stage 2)
- **Priority:** P0
- **Dependencies:** FR-013, FR-007
- **Description:** Backend shall invoke a hosted multimodal AI provider passing the pre-validated outfit candidate items from Stage 1 to generate natural-language styling advice.
- **Acceptance Criteria:**
  1. Generates clear styling explanations: why the pieces pair well, tucking/layering tips, and footwear pairing rationale.
  2. **Zero Wardrobe Item Hallucination Guardrail:** AI is strictly constrained to the candidate set; it never invents, assumes, or suggests items the user does not own.
  3. External AI call is bounded by a strict 30-second socket timeout.

#### FR-015: Weather & Occasion Context
- **Priority:** P0
- **Dependencies:** FR-013
- **Description:** Outfit recommendation requests shall accept occasion and weather parameters to guide outfit filtering.
- **Acceptance Criteria:**
  1. Occasion presets: `CASUAL`, `SMART_CASUAL`, `BUSINESS_CASUAL`, `CORPORATE_FORMAL`, `DATE_NIGHT`, `LOUNGEWEAR`, `ATHLETIC`.
  2. Weather parameters: temperature bracket (`HOT`, `WARM`, `MILD`, `COOL`, `COLD`) and condition (`SUNNY`, `RAINY`, `WINDY`, `SNOWY`).

---

### Module: Wear Logging & Outfit History (FR-WEAR)

#### FR-016: Outfit Wear Logging
- **Priority:** P0
- **Dependencies:** FR-013
- **Description:** Users shall be able to mark an outfit as "Worn Today" or log wear for a past date.
- **Acceptance Criteria:**
  1. `POST /api/v1/outfits/{id}/wear` records a wear event in `outfit_wear_logs`.
  2. Increments the wear count of the associated wardrobe items.

#### FR-017: Wear History Repetition Penalty
- **Priority:** P0
- **Dependencies:** FR-016
- **Description:** The Stage 1 deterministic outfit generator shall apply a scoring penalty to items that were logged as worn within the last 3–7 days to prevent repetitive outfit suggestions.
- **Acceptance Criteria:**
  1. Items worn today or within the last 3 days receive a heavy scoring deduction.
  2. Outfits containing recently worn items are deprioritized unless no other complete combinations exist.

---

### Module: Privacy, Compliance & Account Deletion (FR-PRIV)

#### FR-018: Permanent Account Deletion
- **Priority:** P0
- **Dependencies:** FR-001, FR-010
- **Description:** Users shall be able to permanently delete their account and all associated wardrobe photos and data directly from settings.
- **Acceptance Criteria:**
  1. `DELETE /api/v1/users/account` requires password confirmation.
  2. Within a single `@Transactional` database transaction: in-flight jobs are cancelled; all photo and wardrobe storage keys are enqueued into `storage_deletion_outbox` (`status = 'PENDING'`, `attempts = 0`); `DELETE FROM users WHERE id = :userId` cascades across all dependent tables.
  3. Backend returns **HTTP `204 No Content`** immediately upon transaction commit (< 500ms provisional target).
  4. Subsequent requests with existing JWT access tokens fail immediately (`401 Unauthorized`) via user-existence check.
  5. Post-commit dispatcher and one-minute sweeper retry failed cloud deletions across 24 hours (delays: 15m, 1h, 6h, 16h45m). Tasks are never automatically purged until verified.

#### FR-019: User Data Export
- **Priority:** P1
- **Dependencies:** FR-005, FR-010
- **Description:** Users can download their profile data, style preferences, wardrobe item catalog, and wear history as a structured JSON archive.
- **Acceptance Criteria:**
  1. `POST /api/v1/users/export` returns a structured JSON payload of non-binary personal data.

---

### Module: System Health & Diagnostics (FR-SYS)

#### FR-020: System Health & Diagnostic Ping
- **Priority:** P0 (Implemented & Verified)
- **Dependencies:** None
- **Description:** Backend shall expose a public health check endpoint reflecting service and database connectivity.
- **Acceptance Criteria:**
  1. `GET /api/v1/health` returns `200 OK` with JSON status `"UP"`.

---

## 3. Non-Functional Requirements (NFR)

| ID | Category | Requirement Statement | Verification Method | Priority |
| :--- | :--- | :--- | :--- | :--- |
| **NFR-001** | Performance | Standard REST endpoints (profile, auth, wardrobe CRUD) shall respond within **300ms** at the 95th percentile under normal operational load (< 50 req/s). | Load testing with k6 / Gatling | P0 |
| **NFR-002** | Performance | **Deterministic Outfit Generation Latency:** Stage 1 rule-based candidate filtering in Java must execute in **< 100ms** for wardrobes up to 500 items. | JUnit performance microbenchmarks | P0 |
| **NFR-003** | Performance | **AI Styling Latency:** Multimodal AI styling rationale targets completion within **≤ 15 seconds** at 90th percentile; enforced by a strict 30-second socket read timeout. | Backend metrics and WireMock tests | P0 |
| **NFR-004** | Security | Cryptographic passwords must be hashed using **BCrypt** (cost factor 12) with unique cryptographic salts (Argon2id acceptable future alternative). | Unit & security audit test | P0 |
| **NFR-005** | Security | Every endpoint handling user assets must enforce **strict server-side tenancy verification** (`userId == token.sub`). Insecure Direct Object References (IDOR) must result in 404/403. | Automated security regression test suite | P0 |
| **NFR-006** | Privacy | Wardrobe photos must be stored in private storage with **public read access completely blocked**. Viewing URLs must be pre-signed with a TTL of **≤ 15 minutes**. | Storage bucket policy audit script | P0 |
| **NFR-007** | Privacy | Structured logs must **never contain image binaries**, raw image URLs, JWT tokens, or passwords. | Log sanitization filter unit tests | P0 |
| **NFR-008** | Reliability | The Spring Boot backend must handle external AI provider timeouts (30s) and rate limits (429) gracefully without crashing or corrupting data. | Fault injection / WireMock integration tests | P0 |
| **NFR-009** | Reliability | Schema changes must be managed strictly through **Flyway versioned migrations**; zero manual DDL execution. | Fresh Docker DB initialization in CI | P0 |
| **NFR-010** | Reliability | **Local Dev Storage Resilience:** Local filesystem storage adapter (`LocalStorageService`) allows developers to test image upload/viewing offline on emulators and physical devices without cloud keys. | Local integration tests | P0 |
| **NFR-011** | Mobile UX | Mobile app must comply with **WCAG 2.1 AA** contrast guidelines and support system-level dynamic type / font scaling. | Accessibility audit via Expo tools | P1 |
| **NFR-012** | Maintainability| Backend test coverage across domain modules (`service`, `validator`) must exceed **80% line coverage**. | JaCoCo code coverage report in CI | P0 |

---

## 4. Traceability Matrix

| Requirement ID | Module | Primary API / Component | Verification / Test Scenario | Milestone |
| :--- | :--- | :--- | :--- | :--- |
| **FR-AUTH-001** | Auth | `POST /api/v1/auth/register` | `AuthServiceTest#register_ValidInput_Success`, `AuthServiceTest#register_DuplicateEmail_Conflict` | Phase 1 (Done) |
| **FR-AUTH-002** | Auth | `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh` | `TokenProviderTest#generateToken`, `AuthIntegrationTest#login_InvalidPassword_401` | Phase 1 (Done) |
| **FR-AUTH-003** | Auth | `POST /api/v1/auth/logout` | `AuthIntegrationTest#logout_RevokesRefreshToken` | Phase 1 (Done) |
| **FR-AUTH-004** | Auth | `POST /api/v1/auth/forgot-password`, `POST /api/v1/auth/reset-password` | `AuthServiceTest#forgotPassword_DispatchesEmail`, `AuthServiceTest#resetPassword_ValidToken_Success` | Phase 5 |
| **FR-PROF-005** | Profile | `GET /api/v1/profile`, `PUT /api/v1/profile` | `UserProfileControllerTest#updateProfile_ValidData` | Phase 2 |
| **FR-PROF-006** | Profile | `GET /api/v1/profile/style-preferences`, `PUT /api/v1/profile/style-preferences` | `StylePreferencesControllerTest#updatePreferences_ValidEnums` | Phase 2 |
| **FR-PRIV-007** | Privacy | `PUT /api/v1/profile/consents` | `UserProfileServiceTest#updateDualConsents` | Phase 2 |
| **FR-WARD-008** | Wardrobe | Mobile Normalizer & Upload Intent | Client Unit Test: Rejection of >10MB files and non-image types | Phase 2 |
| **FR-WARD-009** | Wardrobe | `POST /api/v1/wardrobe/upload-intent` | `WardrobeServiceTest#createUploadIntent_GeneratesPresignedUrl` | Phase 2 |
| **FR-WARD-010** | Wardrobe | `POST /api/v1/wardrobe/items` | `WardrobeItemControllerTest#createItem_ValidPayload` | Phase 2 |
| **FR-WARD-011** | Wardrobe | `GET /api/v1/wardrobe/items` | `WardrobeItemControllerTest#listItems_FilteredByCategory` | Phase 2 |
| **FR-WARD-012** | Wardrobe | `PUT /api/v1/wardrobe/items/{id}`, `DELETE /api/v1/wardrobe/items/{id}` | `WardrobeItemServiceTest#deleteItem_PurgesStorageAndSetsNull` | Phase 2 |
| **FR-OUTFIT-013** | Outfit | Deterministic Combinations Engine | `OutfitGeneratorTest#generateOutfits_CompletenessAndFormality` | Phase 3 |
| **FR-OUTFIT-014** | Outfit | Multimodal AI Styling Adapter | `AiStylingAdapterTest#generateRationale_ZeroHallucination` | Phase 3 |
| **FR-OUTFIT-015** | Outfit | `POST /api/v1/outfits/generate` | `OutfitControllerTest#generateOutfits_WeatherAndOccasion` | Phase 3 |
| **FR-WEAR-016** | History | `POST /api/v1/outfits/{id}/wear` | `WearLogControllerTest#logWearEvent_Success` | Phase 4 |
| **FR-WEAR-017** | History | Repetition Penalty Rule | `OutfitGeneratorTest#penalizeRecentlyWornItems` | Phase 4 |
| **FR-PRIV-018** | Privacy | `DELETE /api/v1/users/account` | `UserServiceTest#deleteAccount_CascadesAndEnqueuesOutbox` | Phase 5 |
| **FR-PRIV-019** | Privacy | `POST /api/v1/users/export` | `UserServiceTest#exportUserData_ReturnsCompleteJson` | Phase 5 |
| **FR-SYS-020** | System | `GET /api/v1/health` | `HealthCheckTest#publicHealthPing_Returns200` | Phase 1 (Done) |
