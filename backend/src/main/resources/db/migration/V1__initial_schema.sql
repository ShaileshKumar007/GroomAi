-- ==============================================================================
-- Groom.ai — Baseline Relational Schema Migration (V1)
-- Target Engine: PostgreSQL 15+ / 16
-- Reference: docs/DATA_MODEL.md
-- ==============================================================================

-- Enable pgcrypto for UUID generation if needed (gen_random_uuid is native in PG 13+)
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ------------------------------------------------------------------------------
-- 1. Table: users
-- ------------------------------------------------------------------------------
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'ROLE_USER',
    tokens_invalid_before TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE INDEX idx_users_email ON users (email);

-- ------------------------------------------------------------------------------
-- 2. Table: user_profiles
-- ------------------------------------------------------------------------------
CREATE TABLE user_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    display_name VARCHAR(64) NULL,
    age_bracket VARCHAR(32) NULL,
    photo_consent_granted BOOLEAN NOT NULL DEFAULT FALSE,
    photo_consent_granted_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_profiles_user_id UNIQUE (user_id),
    CONSTRAINT fk_user_profiles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- ------------------------------------------------------------------------------
-- 3. Table: grooming_preferences
-- ------------------------------------------------------------------------------
CREATE TABLE grooming_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    hair_type VARCHAR(32) NOT NULL,
    hair_density VARCHAR(32) NOT NULL,
    facial_hair_pattern VARCHAR(32) NOT NULL,
    daily_styling_effort VARCHAR(32) NOT NULL,
    workplace_environment VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_grooming_preferences_user_id UNIQUE (user_id),
    CONSTRAINT fk_grooming_preferences_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- ------------------------------------------------------------------------------
-- 4. Table: refresh_tokens
-- ------------------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_hash ON refresh_tokens (token_hash);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

-- ------------------------------------------------------------------------------
-- 5. Table: password_reset_tokens
-- ------------------------------------------------------------------------------
CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_pwd_reset_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_pwd_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_pwd_reset_tokens_hash ON password_reset_tokens (token_hash);
CREATE INDEX idx_pwd_reset_tokens_user ON password_reset_tokens (user_id);

-- ------------------------------------------------------------------------------
-- 6. Table: photo_assets
-- ------------------------------------------------------------------------------
CREATE TABLE photo_assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    content_type VARCHAR(64) NOT NULL,
    byte_size INTEGER NOT NULL CHECK (byte_size > 0 AND byte_size <= 10485760),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_UPLOAD',
    confirmed_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_photo_assets_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_photo_assets_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_photo_assets_user_status ON photo_assets (user_id, status);

-- ------------------------------------------------------------------------------
-- 7. Table: storage_deletion_outbox
-- Deliberately contains NO user_id and NO foreign keys to decouple from account purge
-- ------------------------------------------------------------------------------
CREATE TABLE storage_deletion_outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    storage_key VARCHAR(512) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    last_attempt_at TIMESTAMPTZ NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    lease_expires_at TIMESTAMPTZ NULL,
    error_message TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_storage_outbox_status_due ON storage_deletion_outbox (status, next_attempt_at);
CREATE INDEX idx_storage_outbox_lease ON storage_deletion_outbox (status, lease_expires_at);

-- ------------------------------------------------------------------------------
-- 8. Table: recommendation_jobs
-- photo_asset_id references photo_assets with ON DELETE SET NULL
-- ------------------------------------------------------------------------------
CREATE TABLE recommendation_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    photo_asset_id UUID NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    idempotency_key VARCHAR(128) NULL,
    error_message TEXT NULL,
    recommendation_id UUID NULL,
    completed_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rec_jobs_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_rec_jobs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_rec_jobs_photo FOREIGN KEY (photo_asset_id) REFERENCES photo_assets(id) ON DELETE SET NULL
);

CREATE INDEX idx_rec_jobs_user_status ON recommendation_jobs (user_id, status);

-- ------------------------------------------------------------------------------
-- 9. Table: recommendations
-- photo_asset_id references photo_assets with ON DELETE SET NULL
-- ------------------------------------------------------------------------------
CREATE TABLE recommendations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    job_id UUID NOT NULL,
    photo_asset_id UUID NULL,
    summary_observation TEXT NOT NULL,
    disclaimer TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_recommendations_job_id UNIQUE (job_id),
    CONSTRAINT fk_recommendations_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_recommendations_job FOREIGN KEY (job_id) REFERENCES recommendation_jobs(id) ON DELETE CASCADE,
    CONSTRAINT fk_recommendations_photo FOREIGN KEY (photo_asset_id) REFERENCES photo_assets(id) ON DELETE SET NULL
);

CREATE INDEX idx_recommendations_user_created ON recommendations (user_id, created_at DESC);

-- ------------------------------------------------------------------------------
-- 10. Table: recommendation_options
-- Transitive child of recommendations (cascades on delete)
-- ------------------------------------------------------------------------------
CREATE TABLE recommendation_options (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recommendation_id UUID NOT NULL,
    category VARCHAR(32) NOT NULL,
    style_name VARCHAR(128) NOT NULL,
    suitability_score INTEGER NOT NULL CHECK (suitability_score BETWEEN 1 AND 100),
    rationale TEXT NOT NULL,
    barber_instructions JSONB NOT NULL,
    maintenance_routine JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_rec_options_recommendation FOREIGN KEY (recommendation_id) REFERENCES recommendations(id) ON DELETE CASCADE
);

CREATE INDEX idx_rec_options_parent ON recommendation_options (recommendation_id);

-- ------------------------------------------------------------------------------
-- 11. Table: saved_recommendations
-- ------------------------------------------------------------------------------
CREATE TABLE saved_recommendations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    option_id UUID NOT NULL,
    personal_notes TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_saved_user_option UNIQUE (user_id, option_id),
    CONSTRAINT fk_saved_rec_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_saved_rec_option FOREIGN KEY (option_id) REFERENCES recommendation_options(id) ON DELETE CASCADE
);

CREATE INDEX idx_saved_rec_user ON saved_recommendations (user_id);

-- ------------------------------------------------------------------------------
-- 12. Table: grooming_plans
-- ------------------------------------------------------------------------------
CREATE TABLE grooming_plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    active_hairstyle_saved_id UUID NULL,
    active_beard_saved_id UUID NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_grooming_plans_user_id UNIQUE (user_id),
    CONSTRAINT fk_grooming_plans_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_grooming_plans_hair_saved FOREIGN KEY (active_hairstyle_saved_id) REFERENCES saved_recommendations(id) ON DELETE SET NULL,
    CONSTRAINT fk_grooming_plans_beard_saved FOREIGN KEY (active_beard_saved_id) REFERENCES saved_recommendations(id) ON DELETE SET NULL
);

-- ------------------------------------------------------------------------------
-- 13. Table: grooming_plan_items
-- Transitive child of grooming_plans (cascades on delete)
-- ------------------------------------------------------------------------------
CREATE TABLE grooming_plan_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    cadence VARCHAR(32) NOT NULL,
    is_completed_today BOOLEAN NOT NULL DEFAULT FALSE,
    last_completed_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_plan_items_plan FOREIGN KEY (plan_id) REFERENCES grooming_plans(id) ON DELETE CASCADE
);

CREATE INDEX idx_plan_items_plan ON grooming_plan_items (plan_id);
