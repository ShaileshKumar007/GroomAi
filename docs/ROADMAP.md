# Groom.ai — Implementation Roadmap & Phased Execution Plan

**Document Version:** 2.0.0  
**Status:** Approved Roadmap (Wardrobe-First Evolution)  
**Approach:** Phased, Milestones-Driven Delivery with Early Vertical Slice  
**Last Updated:** October 2026  

---

## 1. Phasing Overview & Milestone Dependencies

The project is structured into sequential phases to validate technical feasibility early, derisk integrations, and maintain high engineering quality without architectural drift.

```mermaid
graph TD
    Phase0["Phase 0: Project Setup & Baseline Scaffolding\n(Maven, Spring Boot 3, Expo, Flyway, CI)\n[COMPLETED]"]
    Phase1["Phase 1: Foundation & Authentication\n(Mobile connects to Backend, JWT Auth, Health Ping, Postgres)\n[COMPLETED]"]
    Phase2["Phase 2: Digital Wardrobe Management\n(Wardrobe CRUD, Private Storage, Lightweight Model, Dual Consent)\n[STAGES 3A-3C MERGED, 3D IN PR #8]"]
    Phase3["Phase 3: Deterministic Outfit Engine & AI Styling Rationale\n(Completeness rules, Formality, Weather, Color Harmony, Explainable AI)\n[ACTIVE MILESTONE: STAGE 4A DESIGN COMPLETE, 4B NEXT]"]
    Phase4["Phase 4: Outfit History & Wear Tracking\n(Wear Logging, Repetition Penalty, Usage Insights)"]
    Phase5["Phase 5: Privacy, Deletion, Password Reset & Hardening\n(Account Cascade, Storage Outbox, Pwd Reset, Rate Limits)"]
    Phase6["Phase 6: Cloud Deployment & Beta Release\n(PaaS Deploy, Managed Postgres, S3, Beta Cohort)"]
    Phase7["Phase 7+: Gap Analysis, Generative Try-On & Grooming Expansion\n(Versatile additions, Virtual Try-On, Hairstyles & Beard Consulting)"]

    Phase0 --> Phase1
    Phase1 --> Phase2
    Phase2 --> Phase3
    Phase3 --> Phase4
    Phase4 --> Phase5
    Phase5 --> Phase6
    Phase6 --> Phase7
```

---

## 2. Detailed Milestone Specifications

> **Status hygiene:** “Implemented”, “CI passed”, and “verified on a physical device” are separate claims. Only mark a task/device flow verified when the corresponding test evidence exists. The CI run IDs and commit ranges below are historical notes and must be reconciled against GitHub/repository history before being treated as canonical.


### Phase 0: Project Setup & Baseline Scaffolding
- **Status:** Completed (commit `c108364`, PR #1 merge `ed3a244`, CI run `#38618698144`).
- **Delivered Capabilities:**
  - Initialized Spring Boot 3.3.5 project with Java 21 LTS, Maven, and Flyway.
  - Initialized React Native Expo mobile app with Expo Router and strict TypeScript.
  - Flyway migration `V1__initial_schema.sql` applied and tested against real PostgreSQL 16 Testcontainers.
  - Multi-job CI pipeline verifying Java compilation, tests, TypeScript strictness, and Expo bundle generation.

---

### Phase 1: Foundation & The First Vertical Slice
- **Status:** Completed & merged (commits `388a42c` through `57a200f`, PR #3 merge `27b7148`, CI run `#38627268448`).
- **Delivered Capabilities:**
  - Public health check ping: `GET /api/v1/health` rendered on mobile onboarding screen.
  - User Registration and Login screens in the mobile app.
  - Backend `auth` module endpoints: `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`, and `GET /api/v1/auth/me`.
  - Secure token persistence on the mobile client using `Expo SecureStore`.
  - Centralized Axios client with automatic Bearer injection and 401 refresh interception.
  - CI verification for backend/mobile tests and PostgreSQL integration tests; BCrypt (strength 12) password hashing and rotating refresh tokens.

---

### Phase 2: Digital Wardrobe Management (Stages 3A–3C Merged, Stage 3D in PR #8)
- **Status:** **Stages 3A–3C merged into `main`; Stage 3D implemented, tested, and CI verified (PR #8).**
- **Stage Breakdown:**
  - **Stage 3A (Data Model & Schema):** Flyway `V2__wardrobe_schema.sql` (`wardrobe_items`, `style_preferences`, `user_profiles` dual consents). Merged into `main` (PR #5 `1f74d0d`).
  - **Stage 3B (Ingestion & Storage):** `ObjectStorageService` abstraction (S3 + `LocalStorageService`), upload intent handshake (`POST /api/v1/wardrobe/upload-intent`), JPEG verification (`POST /api/v1/wardrobe/items`), mobile photo capture. Merged into `main` (PR #6 `2a1bb0e`).
  - **Stage 3C (Catalog Browsing):** Paginated listing (`GET /api/v1/wardrobe/items`), item details (`GET /api/v1/wardrobe/items/{id}`), mobile catalog UI with category tabs and `AuthenticatedImage`. Merged into `main` (PR #7 `dbb2456`).
  - **Stage 3D (Editing & Deletion):** Metadata editing (`PUT /api/v1/wardrobe/items/{id}`), transaction-safe deletion (`DELETE /api/v1/wardrobe/items/{id}`), hardened `StorageDeletionOutboxServiceImpl` with lease claim fencing and exponential retries, mobile edit/delete UI, Expo SDK 58 upgrade. Verified in CI run `40908009674` (PR #8 pending merge).
- **Objective:** Implement user style preferences, dual decoupled privacy consents, and the digital wardrobe catalog with private object storage.
- **Key Tasks:**
  - Author and apply Flyway migration `V2__wardrobe_schema.sql`:
    - Add `wardrobe_items` table.
    - Add `style_preferences` table.
    - Alter `user_profiles` to add `wardrobe_storage_consent_granted`, `wardrobe_storage_consent_granted_at`, `ai_styling_consent_granted`, `ai_styling_consent_granted_at`.
  - Implement backend `user` module endpoints:
    - `GET /api/v1/profile` (updated with dual consent status).
    - `PUT /api/v1/profile` (update display name, age bracket).
    - `PUT /api/v1/profile/consents` (update decoupled wardrobe storage and AI styling consents).
    - `GET/PUT /api/v1/profile/style-preferences` (style aesthetic, preferred fit, favorite/avoided colors, default formality).
  - Implement `ObjectStorageService` abstraction in `com.groomai.storage`:
    - `S3StorageService`: AWS S3 private bucket implementation generating pre-signed upload/download URLs (TTL ≤ 15 min; `uploadMode: "S3_PRESIGNED"`).
    - `LocalStorageService`: Local filesystem adapter (`./data/storage`) providing direct multipart upload endpoint (`POST /api/v1/storage/upload/{storageKey}`; `uploadMode: "LOCAL_STREAM"`) and authenticated download stream (`GET /api/v1/storage/files/{storageKey}`) enabling offline development and testing on physical devices/emulators without AWS keys.
  - Implement backend `wardrobe` module:
    - `POST /api/v1/wardrobe/upload-intent`: Verifies `wardrobe_storage_consent_granted == true`; returns upload intent response with `uploadMode`, `storageKey`, and `uploadUrl`; rejects `image/heic` with HTTP `400 Bad Request` (`UNSUPPORTED_IMAGE_FORMAT`).
    - `POST /api/v1/wardrobe/items`: Verifies uploaded object in storage (magic bytes `FF D8 FF`, size ≤ 10MB, min dimensions 200x200), catalogs item with required fields (`name`, `category`, `primaryColor`, `storageKey`) and defaults for optional fields (`formalityLevel`, `pattern`, `subcategory`, `secondaryColor`, `season`).
    - `GET /api/v1/wardrobe/items`: Paginated listing with filtering by category, formality, season, color. Returns fresh pre-signed or authenticated download URLs.
    - `GET /api/v1/wardrobe/items/{id}`: Single item detail view.
    - `PUT /api/v1/wardrobe/items/{id}`: Edit item metadata.
    - `DELETE /api/v1/wardrobe/items/{id}`: Verifies ownership, enqueues storage key to `storage_deletion_outbox` in same transaction, deletes database row.
  - Build Mobile UI features:
    - Style profile setup screen.
    - Wardrobe storage consent modal.
    - Wardrobe Catalog Dashboard with category tabs (Tops, Bottoms, Footwear, Outerwear, Accessories).
    - Add Wardrobe Item screen: Camera capture / gallery picker, client-side JPEG normalization via `expo-image-manipulator` (≤ 10MB), metadata tagging form.
    - Item detail and edit/delete screens.
- **Security & Privacy Checkpoint:**
  - Verify wardrobe photos are stored in private storage with no public access.
  - Verify upload intent generation strictly requires `wardrobe_storage_consent_granted == true`.
  - Verify tenancy isolation: user A cannot view, edit, or delete user B's wardrobe items.
- **Definition of Done (DoD):**
  - Authenticated user can complete style preferences, grant storage consent, upload clothing photos, and view their organized digital wardrobe on a physical iPhone.
  - 100% unit and integration tests passing for wardrobe module using Testcontainers PostgreSQL.

---

### Phase 3: Deterministic Outfit Engine & AI Styling Rationale
- **Objective:** Build the two-stage hybrid outfit recommendation engine combining sub-100ms deterministic candidate generation with multimodal AI styling rationale.
- **Stage 4A: Rules, Scoring & Technical Design (Completed):**
  - Formulated slot grammar: 1 Top + 1 Bottom + 1 Footwear (required); optional Outerwear and Accessory.
  - Formulated composite suitability score ($0\text{--}100$): Color Harmony ($30$), Formality ($25$), Season ($20$), Pattern ($15$), Style Preferences ($10$), minus Wear Repetition ($-20$) and Weather ($-15$) deductions.
  - Formulated hard rejection gates ($\Delta_F \ge 3$, seasonal collision, temperature mismatch) and deterministic 4-stage tie-breaking hierarchy.
  - Authored comprehensive technical design in `docs/OUTFIT_GENERATION_DESIGN.md`.
  - Accepted ADR-0009 in `docs/DECISIONS.md`.
- **Stage 4B: Deterministic Engine Implementation & Verification (Next):**
  - Implement domain package `com.groomai.outfit`:
    - Enums: `Occasion`, `WeatherCondition`.
    - DTOs: `OutfitRecommendationRequestDto`, `OutfitRecommendationResponseDto`, `ScoreBreakdownDto`.
    - Component scorers: `ColorCompatibilityScorer`, `FormalityCompatibilityScorer`, `SeasonCompatibilityScorer`, `PatternCompatibilityScorer`, `WearHistoryPenaltyScorer`.
    - Service: `OutfitGeneratorService` & `OutfitGeneratorServiceImpl` with pre-filtering, tuple generation, and bounded min-heap priority queue.
    - Controller: `OutfitController` exposing `POST /api/v1/outfits/generate`.
  - Exception handling: Global handler for `InsufficientWardrobeItemsException` returning HTTP 422 with `missingCategories`.
  - Test suite: Unit tests for all 10 benchmark evaluation scenarios; JUnit microbenchmark verifying $< 100\text{ ms}$ execution for 500-item wardrobes; tenancy isolation integration tests.
  - Mobile UI: Daily outfit recommendation screen (`mobile/app/outfits/index.tsx`) with occasion selector chips, weather input, recommendation cards carousel, and score breakdown.
- **Stage 4C: Multimodal AI Styling Rationale (Future):**
  - Passes pre-validated candidate items and styling prompt to external AI provider only when `ai_styling_consent_granted == true`.
  - Generates natural-language rationale: why the combination works, tucking/layering advice, shoe pairing tips.
  - **Strict Guardrail:** AI is strictly constrained to the candidate set; it never invents or recommends unowned clothes.
  - Enforces 30-second socket timeout with graceful fallback and user quota preservation.
- **Definition of Done (DoD):**
  - Recommendation generation executes in $< 100\text{ ms}$ with zero unowned item hallucinations.
  - Works offline/without AI consent, returning deterministic candidate combinations and structured explanations.
  - WireMock integration tests verify behavior on AI timeouts and rate limits when AI styling is enabled.

---

### Phase 4: Outfit History & Wear Tracking
- **Objective:** Enable users to log worn outfits and prevent repetitive recommendations.
- **Key Tasks:**
  - Implement wear logging endpoint: `POST /api/v1/outfits/{id}/wear`.
  - Implement `GET /api/v1/outfits/wear-history`.
  - Integrate repetition penalty into Stage 1 deterministic engine: penalizes items worn within the past 3–7 days to prioritize fresh combinations.
  - Build Mobile UI:
    - "Mark as Worn Today" action on outfit cards.
    - Outfit wear history timeline / calendar view.
    - Basic wardrobe utilization insights (most worn / least worn pieces).
- **Definition of Done (DoD):**
  - Logging an outfit as worn immediately penalizes its items in subsequent recommendation requests for 7 days.

---

### Phase 5: Privacy, Deletion, Password Reset & Security Hardening
- **Objective:** Enforce complete relational erasure, durable cloud storage outbox retries, password recovery, rate limiting, and observability.
- **Key Tasks:**
  - Implement password reset workflows: `POST /api/v1/auth/forgot-password` and `POST /api/v1/auth/reset-password`.
  - Implement atomic account deletion cascade: `DELETE /api/v1/users/account`.
    - Single `@Transactional` database transaction: enqueues all wardrobe and photo storage keys into `storage_deletion_outbox` (`status = 'PENDING'`, `attempts = 0`); deletes user row cascading across all dependent tables; returns HTTP 204 immediately (< 500ms provisional target).
  - Implement `StorageOutboxSweeper` background worker & retry dispatcher (the table schema is already applied in `V1__initial_schema.sql`):
    - Post-commit dispatcher and one-minute sweeper (`@Scheduled(cron = "0 * * * * *")`) atomically claim due tasks, transitioning them to `PROCESSING` with a 2-minute lease.
    - Bounded exponential retries across 24 hours (15m, 1h, 6h, 16h45m). Tasks transition to `FAILED` with dead-letter alert on Attempt 5 and are never automatically purged until deletion is confirmed.
  - Implement rate limiting filter using Bucket4j on auth and styling endpoints.
  - Conduct IDOR penetration test suite across all wardrobe and outfit endpoints.
- **Definition of Done (DoD):**
  - Account deletion atomically purges all database records and guarantees durable outbox cleanup of storage assets.
  - Zero IDOR vulnerabilities across all user endpoints.

---

### Phase 6: Cloud Deployment & Closed Beta Release
- **Objective:** Deploy production infrastructure, distribute mobile builds, and onboard initial beta cohort.
- **Key Tasks:**
  - Containerize Spring Boot application with Dockerfile (Eclipse Temurin 21 JRE).
  - Deploy to managed cloud PaaS (e.g., Render, Railway, AWS App Runner) with managed PostgreSQL 16.
  - Configure production AWS S3 private storage and CORS policies.
  - Distribute mobile application via Expo Application Services (EAS) to TestFlight (iOS) and Google Play Internal Testing (Android).
  - Onboard initial beta cohort and monitor wardrobe activation and recommendation CSAT.

---

### Phase 7+: Gap Analysis, Generative Try-On & Grooming Expansion (Post-MVP)
- **Smart Wardrobe Gap Analysis:** Algorithms identifying versatile missing items that maximize new outfit combinations with existing clothes.
- **Generative Virtual Try-On:** Realistic visual rendering of the user wearing specific outfit combinations.
- **Grooming Consultations Expansion:** Hairstyle and beard styling consultations using portrait photos, Barber Cards with technical cut instructions, and daily maintenance routines.
