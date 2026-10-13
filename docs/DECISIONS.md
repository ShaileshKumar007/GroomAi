# Groom.ai — Architecture Decision Records (ADRs)

**Document Version:** 1.1.0  
**Status:** Draft — Living Architecture Ledger (Pending Stakeholder Review)  
**Last Updated:** October 2026  

---

## Index of Architectural Decisions

- [ADR-001: Mobile Client Architecture — React Native with Expo](#adr-001-mobile-client-architecture--react-native-with-expo) (Status: **Accepted Baseline**)
- [ADR-002: Mobile Language & Navigation — Strict TypeScript with Expo Router](#adr-002-mobile-language--navigation--strict-typescript-with-expo-router) (Status: **Accepted Baseline**)
- [ADR-003: Backend Architecture — Java 21 LTS and Spring Boot Modular Monolith](#adr-003-backend-architecture--java-21-lts-and-spring-boot-modular-monolith) (Status: **Accepted Baseline**)
- [ADR-004: Primary Database & Evolution — PostgreSQL with Flyway Migrations](#adr-004-primary-database--evolution--postgresql-with-flyway-migrations) (Status: **Accepted Baseline**)
- [ADR-005: Hosted Multimodal AI Provider Selection & Abstraction](#adr-005-hosted-multimodal-ai-provider-selection--abstraction) (Status: **Proposed — Pending Founder Evaluation**)
- [ADR-006: Photo Ingestion Architecture & Private Object Storage](#adr-006-photo-ingestion-architecture--private-object-storage) (Status: **Proposed — Pending Infrastructure Provider Selection**)
- [ADR-007: Recommendation Processing Model — Asynchronous Polling with Job Lifecycle](#adr-007-recommendation-processing-model--asynchronous-polling-with-job-lifecycle) (Status: **Accepted Baseline**)
- [ADR-008: Authentication & Identity Management Architecture](#adr-008-authentication--identity-management-architecture) (Status: **Proposed — Recommended Baseline (Pending Stakeholder Approval)**)
- [ADR-009: API Error Handling Contract & Observability Standards](#adr-009-api-error-handling-contract--observability-standards) (Status: **Accepted Baseline**)
- [ADR-010: Account & Photo Deletion Strategy, Foreign Key Decoupling, and Storage Outbox](#adr-010-account--photo-deletion-strategy-foreign-key-decoupling-and-storage-outbox) (Status: **Proposed — Recommended Baseline (Pending Stakeholder Approval)**)

---

### ADR-001: Mobile Client Architecture — React Native with Expo

- **Status:** **Accepted Baseline**
- **Date:** October 2026
- **Context:**  
  Groom.ai requires a cross-platform mobile application targeting both iOS and Android. The app requires access to device hardware (camera, photo library, secure keystore), smooth animations, and rapid development iteration without maintaining separate native Swift/Kotlin codebases.
- **Decision:**  
  Use **React Native with Expo** (Managed Workflow with Custom Dev Clients if native modules are needed).
- **Alternatives Considered:**
  1. *Flutter (Dart):* Excellent performance and UI consistency, but introduces an additional language runtime and smaller ecosystem for shared web/TS logic.
  2. *Native Swift & Kotlin:* Maximum platform fidelity, but doubles development and maintenance overhead for a single-developer or early-stage team.
  3. *Bare React Native:* Requires managing Xcode and Android Studio native project configurations manually, increasing build breakages and maintenance cost.
- **Consequences:**
  - *Positive:* Single TypeScript codebase for iOS and Android; robust Expo modules for Camera, ImagePicker, and SecureStore; streamlined OTA updates and builds via EAS.
  - *Negative:* Slightly larger initial application binary footprint; dependency on Expo ecosystem updates.
- **Reconsideration Triggers:** If complex on-device real-time computer vision or custom Metal/Vulkan shaders are required that cannot be supported within Expo config plugins.

---

### ADR-002: Mobile Language & Navigation — Strict TypeScript with Expo Router

- **Status:** **Accepted Baseline**
- **Date:** October 2026
- **Context:**  
  To avoid runtime crashes and maintain strict alignment with the backend REST API contracts, the mobile codebase requires compile-time type safety. Furthermore, mobile navigation should be maintainable, intuitive, and support deep linking cleanly.
- **Decision:**  
  Use **TypeScript with strict type checking enabled** (`"strict": true`, `"noImplicitAny": true`) and **Expo Router** for file-based routing.
- **Alternatives Considered:**
  1. *Vanilla JavaScript:* High risk of undefined runtime crashes, especially when parsing nested AI recommendation JSON payloads.
  2. *React Navigation (Standard Component Configuration):* Powerful but requires boilerplate navigation container hierarchies and manual deep linking configuration compared to Expo Router's intuitive filesystem layout.
- **Consequences:**
  - *Positive:* 100% type sharing between API contracts and UI states; file-based routing mirrors URL paths; built-in deep linking.
  - *Negative:* Strict TypeScript requires disciplined type modeling up front; Expo Router has a learning curve for developers accustomed to legacy React Navigation.
- **Reconsideration Triggers:** None foreseen for the MVP lifecycle.

---

### ADR-003: Backend Architecture — Java 21 LTS and Spring Boot Modular Monolith

- **Status:** **Accepted Baseline**
- **Date:** October 2026
- **Context:**  
  The backend must provide high throughput, enterprise-grade security, mature data access patterns, and strong static typing. The architecture must remain manageable by an early-stage team while demonstrating production-level software design.
- **Decision:**  
  Build the backend as a **Modular Monolith** using **Java 21 LTS** and **Spring Boot 3.x**, structured into clear domain modules (`auth`, `user`, `photo`, `recommendation`, `plan`, `common`).
- **Alternatives Considered:**
  1. *Microservices (Multiple independent services):* Introduces severe operational overhead (service discovery, distributed tracing, complex deployments, eventual consistency) with zero business benefit at early stage.
  2. *Python (FastAPI / Django):* Popular for AI prototypes, but lacks the robust static type safety, mature transaction management, and enterprise structure of modern Spring Boot. Hosted multimodal AI APIs expose standard REST/JSON, making Python-specific ML libraries unnecessary on the server.
  3. *Node.js (NestJS / Express):* Fast to scaffold, but less mature ecosystem for relational database transaction boundaries and security abstractions.
- **Consequences:**
  - *Positive:* Single deployable artifact; simple ACID transactions across modules; straightforward integration testing with Testcontainers; clear path to refactor into microservices if scaling ever warrants it.
  - *Negative:* JVM has higher cold-start memory usage (~256MB–512MB RAM) compared to Go or Node.js.
- **Reconsideration Triggers:** If individual domain modules experience radically divergent compute/scaling profiles (e.g., millions of concurrent recommendation requests vs. low auth traffic).

---

### ADR-004: Primary Database & Evolution — PostgreSQL with Flyway Migrations

- **Status:** **Accepted Baseline**
- **Date:** October 2026
- **Context:**  
  The system requires structured relational data storage for user accounts, preferences, jobs, and saved styles, with support for JSON documents (storing structured barber instructions and routine objects) and strict relational integrity (`ON DELETE CASCADE`).
- **Decision:**  
  Use **PostgreSQL 15+ / 16** as the primary datastore, managed exclusively through **Flyway versioned SQL migrations**.
- **Alternatives Considered:**
  1. *MongoDB / NoSQL Document Stores:* Lacks strict relational constraints and cascading deletes essential for privacy compliance and tenant isolation.
  2. *Hibernate `ddl-auto: update`:* Highly risky; can alter or drop database columns unexpectedly in production environments.
  3. *Liquibase:* Powerful XML/YAML migration tool, but standard plain SQL scripts in Flyway are more readable, auditable, and easier for database engineers to maintain.
- **Consequences:**
  - *Positive:* Reliable ACID guarantees; native `JSONB` support with indexability; auditable, immutable SQL migrations; excellent support in Testcontainers.
  - *Negative:* Schema changes require authoring explicit SQL migration files.
- **Reconsideration Triggers:** None foreseen; PostgreSQL is the gold standard for relational persistence.

---

### ADR-005: Hosted Multimodal AI Provider Selection & Abstraction

- **Status:** **Proposed — Pending Founder Evaluation**
- **Date:** October 2026
- **Context:**  
  Groom.ai relies on a multimodal AI model to analyze uploaded portrait images alongside user preferences to generate structured hairstyle and beard recommendations. Different providers vary across visual feature comprehension, output latency, inference cost, structured JSON reliability, and data privacy policies.
- **Decision:**  
  Implement a vendor-neutral **`AiRecommendationProvider` Java interface** in the backend. Currently evaluating three primary candidates for the initial provider implementation.

> [!WARNING]
> **Verification Required:** The model names, latency numbers, approximate costs, and privacy policies below are provisional estimates based on vendor public documentation as of late 2024 / early 2026. **They have not been verified under signed commercial agreements or live API benchmarks** and must be validated prior to production deployment. Furthermore, candidate multimodal models (e.g., OpenAI GPT-4o, Anthropic Claude 3.5 Sonnet) do not natively accept Apple HEIC/HEIF images; provider format support and transcoding overhead must be validated in Phase 3.

| Candidate Provider | Evaluated Models | Vision Quality Hypothesis | Latency Target (Provisional) | Est. Cost / 1K Recs (Unverified) | Commercial Privacy Terms Hypothesis |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Option A: Google Cloud Vertex AI** | Gemini 1.5 Flash / Pro | High (facial geometry & fine hair texture) | 3s – 8s (Flash) / 8s – 15s (Pro) | ~$0.30 (Flash) / ~$3.50 (Pro) | Enterprise terms state customer images/data are not used for training. Requires verification. |
| **Option B: OpenAI API** | GPT-4o / GPT-4o-mini | High (JSON schema adherence) | 5s – 12s | ~$2.50 (mini) / ~$10.00 (4o) | Commercial API data is not used for model training under standard API terms. Requires verification. |
| **Option C: Anthropic API** | Claude 3.5 Sonnet | High (nuanced grooming rationale) | 6s – 14s | ~$3.00 (Input) / ~$15.00 (Output) | Commercial API data is not used for model training. Requires verification. |

- **Proposed Baseline Recommendation:**  
  Begin implementation against the `AiRecommendationProvider` interface using **Google Gemini 1.5 Flash / Pro** or **OpenAI GPT-4o-mini / GPT-4o** based on the lowest cost per recommendation and strict JSON schema support.
- **Alternatives Considered:**
  - *Self-hosted Open-Source Vision Models (e.g., LLaVA on RunPod/Replicate):* High operational complexity, GPU cold starts, fixed infrastructure costs, and lower visual reasoning for nuanced grooming advice.
- **Consequences:**
  - *Positive:* Backend abstraction isolates application logic from provider API changes; swapping providers requires only configuration changes.
  - *Negative:* Ongoing API usage costs per recommendation; dependency on third-party SLA and rate limits; provisional latency targets require monitoring against real-world provider response times.
- **Condition for Final Acceptance:** Founder approval based on actual cost-per-user modeling, live benchmark testing, image format compatibility verification, and legal privacy policy review.

---

### ADR-006: Photo Ingestion Architecture & Private Object Storage

- **Status:** **Proposed — Pending Infrastructure Provider Selection**
- **Date:** October 2026
- **Context:**  
  User portraits are sensitive biometric assets. Uploading high-resolution images directly through the Spring Boot backend consumes server memory and bandwidth. The architecture must guarantee private storage, normalize non-standard image formats (e.g., iOS HEIC), and prevent unauthorized public access.
- **Decision:**  
  Implement **Direct Client Pre-signed Uploads** to private cloud object storage via a vendor-neutral `ObjectStorageService` Java interface with a defined format normalization pipeline:
  1. **Client-Side Normalization — MVP Supported Path:** The React Native mobile client normalizes all photos (converting HEIC to standard JPEG via `expo-image-manipulator`) before requesting an upload intent (`POST /api/v1/photos/upload-intent`). This is the only tested and supported format path for MVP.
  2. **Backend Upload Intent:** Backend validates file metadata. `contentType: "image/heic"` is **rejected** with HTTP `400 Bad Request` (code `UNSUPPORTED_IMAGE_FORMAT`). Only `image/jpeg` and `image/png` are accepted. Backend generates an ephemeral pre-signed `PUT` URL (TTL: 15 minutes) targeting a private bucket.
  3. **Direct Upload:** Mobile client uploads the JPEG binary directly to cloud storage.
  4. **Confirmation:** Backend verifies object existence and content-type header (`HeadObject`) upon confirmation (`POST /api/v1/photos/{id}/confirm`). A stored HEIC object is rejected.
  5. **Server-Side HEIC Handling — Pending Implementation Choice:**
     - Java ImageIO combined with TwelveMonkeys ImageIO plugins does **not** reliably decode HEIC/HEIF. HEIC uses HEVC (H.265) video compression, which TwelveMonkeys lacks a codec for. No backend HEIC transcoding path is available for MVP.
     - A viable server-side HEIC decoding library (e.g., `libheif` via JNI) must be evaluated, integrated, and tested before a backend fallback can be specified.
  6. **Ephemeral Retrieval:** Image retrieval for viewing uses short-lived pre-signed `GET` URLs (TTL: 15 minutes).
- **Candidates for Storage Provider:**
  - *AWS S3:* Industry standard, highest ecosystem compatibility.
  - *Cloudflare R2:* Zero egress fees, standard S3-compatible API.
  - *Google Cloud Storage (GCS):* Integrated if Google Cloud is selected for hosting.
- **Consequences:**
  - *Positive:* Zero binary image payload overhead on the Spring Boot JVM during normal flows; images are never accessible via public static URLs; minimal storage bandwidth costs; eliminates AI model format incompatibility.
  - *Negative:* Requires two-phase handshake on client (request upload intent -> upload binary -> confirm asset); fallback transcoding on server requires ephemeral disk handling.
- **Condition for Final Acceptance:** Selection of cloud hosting provider and validation of provider image format compatibility.

---

### ADR-007: Recommendation Processing Model — Asynchronous Polling with Job Lifecycle

- **Status:** **Accepted Baseline**
- **Date:** October 2026
- **Context:**  
  Multimodal AI inference takes between 6 and 20 seconds depending on provider load. Holding an open synchronous HTTP connection from a mobile client over cellular networks causes frequent gateway timeouts (HTTP 504), connection resets, and thread starvation on the server.
- **Decision:**  
  Standardize on an **Asynchronous Job Lifecycle with Client HTTP Polling**:
  1. Client calls `POST /api/v1/recommendations/jobs` and receives **HTTP `202 Accepted`** with a `jobId`.
  2. Backend executes AI analysis via Spring `@Async` managed thread pool.
  3. Client polls `GET /api/v1/recommendations/jobs/{jobId}` every 2.5 seconds with exponential backoff until `COMPLETED` or `FAILED`.
  4. Client fetches final report from `GET /api/v1/recommendations/{recommendationId}`.
- **Alternatives Considered:**
  1. *Synchronous Request/Response (HTTP 200/201):* Prone to network dropouts and timeouts on mobile cellular connections.
  2. *WebSockets / Server-Sent Events (SSE):* Good for live streaming, but adds connection management complexity (heartbeats, reconnects, stateful proxy configurations) that is unnecessary for a single short-lived background task.
  3. *External Distributed Queue (Kafka / RabbitMQ / BullMQ):* Unnecessary infrastructure overhead for early MVP volume (< 50 concurrent jobs).
- **Consequences:**
  - *Positive:* Resilient to mobile network dropouts; client can resume polling if app is backgrounded; zero new infrastructure required (PostgreSQL job table handles state).
  - *Negative:* Client generates multiple HTTP requests (~4–6 polling requests per recommendation).
- **Reconsideration Triggers:** If concurrent recommendation jobs exceed 500 simultaneous executions, justifying migration to Redis/SQS or Server-Sent Events.

---

### ADR-008: Authentication & Identity Management Architecture

- **Status:** **Proposed — Recommended Baseline (Pending Stakeholder Approval)**
- **Date:** October 2026
- **Context:**  
  Groom.ai requires secure user authentication, password management, session handling, and credential storage. The architecture must protect user accounts while keeping integration friction, maintenance burden, and operating costs low.
- **Evaluation of Alternatives:**
  - **Alternative A: Self-Managed Spring Security JWT (Architect Recommended)**
    - *Mechanics:* Email/password authentication handled directly by Spring Boot using Argon2id/BCrypt. Signed JWT access tokens (15-min TTL) and database-backed rotating refresh tokens (30-day TTL) stored hashed in PostgreSQL `refresh_tokens`. Password reset via secure email tokens stored in `password_reset_tokens`. Mobile client securely persists tokens in `Expo SecureStore`.
    - *Pros:* Zero external vendor lock-in; zero monthly active user (MAU) SaaS charges; complete transactional consistency with PostgreSQL (enabling atomic cascading deletes for GDPR/privacy compliance); no third-party uptime dependencies.
    - *Cons:* Engineering team maintains token rotation filters, password reset token lifecycles, and brute-force rate limiting.
  - **Alternative B: Managed Authentication Provider (e.g., Supabase Auth / Firebase Auth / Auth0)**
    - *Mechanics:* Third-party identity provider issues tokens; Spring Boot backend acts as a resource server validating JWT signatures via JWKS.
    - *Pros:* Turnkey social logins (Google, Apple); managed password reset emails; built-in bot protection.
    - *Cons:* Vendor lock-in; dual-identity state (user records in SaaS auth service + user profile in PostgreSQL); distributed failure modes during account deletion (requires coordinating external user deletion with database cascade); recurring cost per MAU.
- **Architectural Recommendation:**  
  **Adopt Alternative A (Self-Managed Spring Security JWT)** as the baseline for the MVP.
  - *Rationale:* A modular monolith on Spring Boot and PostgreSQL achieves the lowest architectural complexity, tightest transactional integrity, and zero third-party billing for early validation.
  - *Token Revocation Strategy:* A hybrid model reconciles stateless JWTs with immediate revocation:
    1. Account deletion permanently removes the `users` record. Subsequent requests fail authentication immediately (`401 Unauthorized`) via user-existence checks in the security filter.
    2. Security-sensitive operations (password reset, security logout) set `users.tokens_invalid_before = NOW()`. The JWT filter rejects any access token whose `iat` (issued-at) timestamp precedes this cutoff.
    3. Standard logout revokes the refresh token in `refresh_tokens` and clears tokens from `Expo SecureStore`. The short-lived access token expires naturally within ≤ 15 minutes.
- **Condition for Final Acceptance:** Founder confirmation on whether social logins (Apple/Google) are mandatory for the initial private beta.

---

### ADR-009: API Error Handling Contract & Observability Standards

- **Status:** **Accepted Baseline**
- **Date:** October 2026
- **Context:**  
  Mobile clients require deterministic error structures to provide helpful user messaging rather than generic failure states. Debugging distributed issues across mobile, backend, and external AI providers requires correlation tracking.
- **Decision:**  
  Standardize on:
  1. A unified JSON error format across all endpoints: `timestamp`, `status`, `error`, `code`, `message`, `path`, and `details` (matching RFC 7807 principles).
  2. A required correlation identifier (`X-Correlation-ID`) tracked in backend SLF4J MDC (`traceId`) on every request.
  3. Strict suppression of sensitive stack traces and PII in production API responses and server logs.
- **Alternatives Considered:**
  - *Default Spring Boot Error Attributes:* Exposes internal implementation details and inconsistent schemas across validation vs. runtime errors.
- **Consequences:**
  - *Positive:* Clean, predictable client error handling; rapid root-cause analysis via correlation IDs.
  - *Negative:* Requires implementing a comprehensive `@RestControllerAdvice` global exception handler in the backend `common` module.
- **Reconsideration Triggers:** None.

---

### ADR-010: Account & Photo Deletion Strategy, Foreign Key Decoupling, and Storage Outbox

- **Status:** **Proposed — Recommended Baseline (Pending Stakeholder Approval)**
- **Date:** October 2026
- **Context:**  
  Users have the right to delete individual photos or permanently delete their account. The relational schema must support cascading account deletion without foreign key restrictions, preserve historical recommendation text when a photo is deleted, and provide durable cloud object-storage deletion tasks, bounded automatic retries, and operator escalation when network or provider errors occur.
- **Decision:**  
  Adopt a three-part deletion and retention architecture:
  1. **Foreign Key Decoupling (`ON DELETE SET NULL`):**
     - `recommendation_jobs.photo_asset_id` and `recommendations.photo_asset_id` are configured as `UUID NULL REFERENCES photo_assets(id) ON DELETE SET NULL`.
     - *Policy:* When an individual photo is deleted (`DELETE /api/v1/photos/{id}`), the photo binary is purged from object storage and the `photo_assets` record is deleted. Existing historical recommendations retain their styling rationale, score, and Barber Card, while their photo reference becomes `null`.
  2. **Atomic Account Deletion Transaction & Response:**
     - All of the following execute within a **single `@Transactional` database transaction:** aborting in-flight recommendation jobs; extracting photo storage keys and inserting them into `storage_deletion_outbox` (`status = 'PENDING'`, `attempts = 0`); and executing `DELETE FROM users WHERE id = :userId`.
     - PostgreSQL `ON DELETE CASCADE` atomically removes all rows from **11 dependent tables**: 9 direct child tables (`user_profiles`, `grooming_preferences`, `refresh_tokens`, `password_reset_tokens`, `photo_assets`, `recommendation_jobs`, `recommendations`, `saved_recommendations`, `grooming_plans`) and 2 transitive child tables (`recommendation_options`, `grooming_plan_items`). `storage_deletion_outbox` is intentionally excluded — it has no user foreign key.
     - On transaction commit, returns HTTP `204 No Content` immediately. The `< 500ms` response target is **provisional until measured under production conditions**. Existing JWTs fail immediately (`401 Unauthorized`) on subsequent requests.
  3. **Storage Deletion Outbox Lifecycle, Privacy & Retry Semantics:**
     - **Privacy Minimization:** `storage_deletion_outbox` retains only `id`, `storage_key`, `attempts`, `status`, `last_attempt_at`, `next_attempt_at`, `lease_expires_at`, `error_message`, and `created_at`. It has no `user_id` and no foreign keys. Treat `storage_key` as sensitive operational data; do not log it casually.
     - **Durable dispatch and crash recovery:** The row is inserted as `PENDING`, `attempts = 0`, and `next_attempt_at = NOW()` in the same transaction as relational deletion. The post-commit dispatcher and one-minute sweeper use an atomic claim to transition due rows to `PROCESSING`, increment `attempts`, set `last_attempt_at`, and set a two-minute `lease_expires_at`. Expired processing leases are recoverable, so a crash after commit cannot leave work permanently ineligible. The object-storage timeout must be shorter than the lease.
     - **Retry schedule:** After a failed attempt, clear the lease and schedule the next attempt relative to `last_attempt_at`: 15 minutes, then 1 hour, then 6 hours, then 16 hours 45 minutes. These four retry delays total 24 hours. The sweeper (`@Scheduled(cron = "0 * * * * *")`) selects `PENDING` rows with `next_attempt_at <= NOW()` and expired `PROCESSING` leases.
     - **Dead-letter preservation:** After Attempt 5 fails, set `status = 'FAILED'`, clear the lease, and emit `STORAGE_DELETION_DEAD_LETTER`. Automated retries halt until operator intervention. Unverified deletion tasks are never automatically purged, regardless of age. Operators may re-drive a task by resetting `status = 'PENDING'`, `attempts = 0`, `next_attempt_at = NOW()`, and `lease_expires_at = NULL`, or remove it only after verifying that the object is absent.
     - **Access:** Restricted to the storage deletion worker and authorised database administrators. Restricted to `StorageOutboxSweeper` service and authorised database administrators.
- **Alternatives Considered:**
  - *Synchronous-only Cloud Deletion:* Fails if S3 is briefly unreachable, leaving orphaned biometric files in storage, or delays API response unacceptably.
  - *External Message Queue (Kafka/RabbitMQ/SQS):* Introduces unnecessary infrastructure complexity for a low-volume MVP.
- **Consequences:**
  - *Positive:* durable deletion tracking, retries, and operator remediation; relational integrity preserved without foreign key lockouts; user PII minimized; zero additional infrastructure required.
  - *Negative:* Requires a small scheduled sweeper job and an additional database table.
- **Condition for Final Acceptance:** Stakeholder sign-off on retention policy (preserving recommendation text when photo is deleted) and outbox retry parameters.
