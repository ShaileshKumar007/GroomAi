-- V4__style_preferences_evolution.sql
-- Groom.ai Stage 4C.3.1: Personal Style Profile Foundation

-- 1. Add explicit profile completion flag
ALTER TABLE style_preferences 
    ADD COLUMN profile_completed BOOLEAN NOT NULL DEFAULT FALSE;

-- 2. Add pattern preferences and style goals JSONB columns
ALTER TABLE style_preferences
    ADD COLUMN pattern_preferences JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN avoided_patterns JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN style_goals JSONB NOT NULL DEFAULT '[]'::jsonb;
