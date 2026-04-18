-- First-run onboarding (Phase 14.4). Tracks whether the user has finished the
-- welcome wizard (pick currency, create first expense category, set a monthly
-- savings target). Existing accounts default to TRUE so returning users do
-- not re-enter the wizard.
ALTER TABLE user_settings
    ADD COLUMN onboarding_completed BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE user_settings
    ALTER COLUMN onboarding_completed SET DEFAULT FALSE;
