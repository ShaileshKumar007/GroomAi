-- V3__outfit_snapshots_and_wear_hardening.sql
-- Groom.ai Stage 4C.2.1: Historical Data Integrity & Wear Logging Hardening

-- 1. Evolve 'outfits' table: support custom/unscored outfits and descriptive names
ALTER TABLE outfits ADD COLUMN name VARCHAR(100) NULL;
ALTER TABLE outfits ADD COLUMN score_status VARCHAR(32) NOT NULL DEFAULT 'UNSCORED';
ALTER TABLE outfits ALTER COLUMN suitability_score DROP NOT NULL;

-- Invalidate unverified legacy client-submitted scores from Stage 4C.2
UPDATE outfits 
SET score_status = 'UNSCORED', 
    suitability_score = NULL;

-- 2. Evolve 'outfit_items' table: immutable garment metadata snapshots
ALTER TABLE outfit_items ADD COLUMN category VARCHAR(32) NULL;
ALTER TABLE outfit_items ADD COLUMN subcategory VARCHAR(64) NULL;
ALTER TABLE outfit_items ADD COLUMN primary_color VARCHAR(32) NULL;
ALTER TABLE outfit_items ADD COLUMN secondary_color VARCHAR(32) NULL;
ALTER TABLE outfit_items ADD COLUMN pattern VARCHAR(32) NULL;
ALTER TABLE outfit_items ADD COLUMN fabric VARCHAR(32) NULL;
ALTER TABLE outfit_items ADD COLUMN brand VARCHAR(64) NULL;
ALTER TABLE outfit_items ADD COLUMN original_storage_key VARCHAR(512) NULL;

-- Backfill immutable garment metadata from active wardrobe items
UPDATE outfit_items oi
SET category = wi.category,
    subcategory = wi.subcategory,
    primary_color = wi.primary_color,
    secondary_color = wi.secondary_color,
    pattern = wi.pattern,
    original_storage_key = wi.storage_key
FROM wardrobe_items wi
WHERE oi.wardrobe_item_id = wi.id;

-- 3. Evolve 'outfit_wear_logs' table: immutable JSONB snapshot
ALTER TABLE outfit_wear_logs ADD COLUMN snapshot JSONB NULL;

-- Backfill versioned JSONB snapshot for historical wear logs from existing outfits and items
WITH outfit_snapshots AS (
    SELECT 
        o.id AS outfit_id,
        jsonb_build_object(
            'version', 1,
            'outfitId', o.id,
            'name', o.name,
            'occasion', o.occasion,
            'temperatureBracket', o.temperature_bracket,
            'weatherCondition', o.weather_condition,
            'stylingRationale', o.styling_rationale,
            'suitabilityScore', o.suitability_score,
            'scoreStatus', o.score_status,
            'items', COALESCE(
                jsonb_agg(
                    jsonb_build_object(
                        'wardrobeItemId', oi.wardrobe_item_id,
                        'itemRole', oi.item_role,
                        'category', oi.category,
                        'subcategory', oi.subcategory,
                        'primaryColor', oi.primary_color,
                        'secondaryColor', oi.secondary_color,
                        'pattern', oi.pattern,
                        'fabric', oi.fabric,
                        'brand', oi.brand,
                        'originalStorageKey', oi.original_storage_key
                    ) ORDER BY oi.id
                ) FILTER (WHERE oi.id IS NOT NULL),
                '[]'::jsonb
            )
        ) AS snapshot_json
    FROM outfits o
    LEFT JOIN outfit_items oi ON oi.outfit_id = o.id
    GROUP BY o.id
)
UPDATE outfit_wear_logs owl
SET snapshot = os.snapshot_json
FROM outfit_snapshots os
WHERE owl.outfit_id = os.outfit_id
  AND owl.snapshot IS NULL;

-- 4. Reconcile existing duplicate wear logs and merge non-empty notes without truncation
WITH duplicate_groups AS (
    SELECT user_id, outfit_id, worn_date
    FROM outfit_wear_logs
    WHERE outfit_id IS NOT NULL
    GROUP BY user_id, outfit_id, worn_date
    HAVING COUNT(*) > 1
),
ranked_logs AS (
    SELECT 
        owl.id,
        owl.user_id,
        owl.outfit_id,
        owl.worn_date,
        ROW_NUMBER() OVER (
            PARTITION BY owl.user_id, owl.outfit_id, owl.worn_date 
            ORDER BY owl.created_at ASC, owl.id ASC
        ) AS rnk
    FROM outfit_wear_logs owl
    JOIN duplicate_groups dg ON owl.user_id = dg.user_id 
                            AND owl.outfit_id = dg.outfit_id 
                            AND owl.worn_date = dg.worn_date
),
merged_notes_per_group AS (
    SELECT 
        owl.user_id,
        owl.outfit_id,
        owl.worn_date,
        string_agg(DISTINCT TRIM(owl.notes), E'\n---\n') AS merged_notes
    FROM outfit_wear_logs owl
    JOIN duplicate_groups dg ON owl.user_id = dg.user_id 
                            AND owl.outfit_id = dg.outfit_id 
                            AND owl.worn_date = dg.worn_date
    WHERE owl.notes IS NOT NULL AND TRIM(owl.notes) <> ''
    GROUP BY owl.user_id, owl.outfit_id, owl.worn_date
)
UPDATE outfit_wear_logs owl
SET notes = m.merged_notes
FROM ranked_logs r
JOIN merged_notes_per_group m ON r.user_id = m.user_id 
                             AND r.outfit_id = m.outfit_id 
                             AND r.worn_date = m.worn_date
WHERE owl.id = r.id AND r.rnk = 1;

-- 5. Delete redundant duplicate records (rnk > 1) using the identical survivor ranking
WITH duplicate_groups AS (
    SELECT user_id, outfit_id, worn_date
    FROM outfit_wear_logs
    WHERE outfit_id IS NOT NULL
    GROUP BY user_id, outfit_id, worn_date
    HAVING COUNT(*) > 1
),
ranked_logs AS (
    SELECT 
        owl.id,
        ROW_NUMBER() OVER (
            PARTITION BY owl.user_id, owl.outfit_id, owl.worn_date 
            ORDER BY owl.created_at ASC, owl.id ASC
        ) AS rnk
    FROM outfit_wear_logs owl
    JOIN duplicate_groups dg ON owl.user_id = dg.user_id 
                            AND owl.outfit_id = dg.outfit_id 
                            AND owl.worn_date = dg.worn_date
)
DELETE FROM outfit_wear_logs
WHERE id IN (
    SELECT id FROM ranked_logs WHERE rnk > 1
);

-- 6. Enforce strict database-level unique constraint to prevent duplicate wear logs
CREATE UNIQUE INDEX uq_outfit_wear_logs_user_outfit_date 
ON outfit_wear_logs (user_id, outfit_id, worn_date) 
WHERE outfit_id IS NOT NULL;
