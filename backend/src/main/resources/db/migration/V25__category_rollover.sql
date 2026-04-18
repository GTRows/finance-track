ALTER TABLE expense_categories
    ADD COLUMN rollover_enabled BOOLEAN NOT NULL DEFAULT FALSE;
