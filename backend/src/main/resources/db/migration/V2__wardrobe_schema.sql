-- =============================================================================
-- Groom.ai: Migration V2__wardrobe_schema.sql
-- Digital Wardrobe Catalog & Style Preferences
-- Reference: docs/DATA_MODEL.md Section 4
-- =============================================================================

-- 1. Alter user_profiles to add unbundled wardrobe & AI styling consents and widen display_name
ALTER TABLE user_profiles
    ALTER COLUMN display_name TYPE VARCHAR(100),
    ADD COLUMN wardrobe_storage_consent_granted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN wardrobe_storage_consent_granted_at TIMESTAMPTZ NULL,
    ADD COLUMN ai_styling_consent_granted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN ai_styling_consent_granted_at TIMESTAMPTZ NULL;

-- 2. Style Preferences Table
CREATE TABLE style_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    primary_style VARCHAR(50) NOT NULL DEFAULT 'CASUAL',
    preferred_fit VARCHAR(32) NOT NULL DEFAULT 'REGULAR',
    color_preferences JSONB NOT NULL DEFAULT '[]'::jsonb,
    avoided_colors JSONB NOT NULL DEFAULT '[]'::jsonb,
    default_formality VARCHAR(32) NOT NULL DEFAULT 'CASUAL',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_style_preferences_user_id UNIQUE (user_id),
    CONSTRAINT fk_style_preferences_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_style_preferences_user_id ON style_preferences (user_id);

-- 3. Wardrobe Items Catalog Table
CREATE TABLE wardrobe_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(32) NOT NULL,
    subcategory VARCHAR(50) NULL,
    primary_color VARCHAR(32) NOT NULL,
    secondary_color VARCHAR(32) NULL,
    pattern VARCHAR(32) NOT NULL DEFAULT 'SOLID',
    formality_level VARCHAR(32) NOT NULL DEFAULT 'CASUAL',
    season VARCHAR(32) NOT NULL DEFAULT 'ALL_SEASON',
    storage_key VARCHAR(512) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_wardrobe_items_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_wardrobe_items_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_wardrobe_items_user_category ON wardrobe_items (user_id, category);
CREATE INDEX idx_wardrobe_items_user_status ON wardrobe_items (user_id, status);
CREATE INDEX idx_wardrobe_items_storage_key ON wardrobe_items (storage_key);

-- 4. Outfits Table (Phase 3 Foundation)
CREATE TABLE outfits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    occasion VARCHAR(50) NOT NULL,
    temperature_bracket VARCHAR(32) NULL,
    weather_condition VARCHAR(32) NULL,
    styling_rationale TEXT NOT NULL,
    suitability_score INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_outfits_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_outfits_user_created ON outfits (user_id, created_at DESC);

-- 5. Outfit Items Junction Table
CREATE TABLE outfit_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    outfit_id UUID NOT NULL,
    wardrobe_item_id UUID NULL,
    item_role VARCHAR(32) NOT NULL,
    CONSTRAINT fk_outfit_items_outfit FOREIGN KEY (outfit_id) REFERENCES outfits(id) ON DELETE CASCADE,
    CONSTRAINT fk_outfit_items_wardrobe_item FOREIGN KEY (wardrobe_item_id) REFERENCES wardrobe_items(id) ON DELETE SET NULL
);

CREATE INDEX idx_outfit_items_outfit_id ON outfit_items (outfit_id);
CREATE INDEX idx_outfit_items_wardrobe_item_id ON outfit_items (wardrobe_item_id);

-- 6. Outfit Wear Logs Table (Phase 4 Foundation)
CREATE TABLE outfit_wear_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    outfit_id UUID NULL,
    worn_date DATE NOT NULL DEFAULT CURRENT_DATE,
    notes TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_outfit_wear_logs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_outfit_wear_logs_outfit FOREIGN KEY (outfit_id) REFERENCES outfits(id) ON DELETE SET NULL
);

CREATE INDEX idx_outfit_wear_logs_user_date ON outfit_wear_logs (user_id, worn_date DESC);
