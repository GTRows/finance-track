-- Receipt attachment for transactions (Phase 10.4). Paths are relative to
-- the backend's configured receipt root; each file lives under
-- {user_id}/{txn_id}.{ext} to guarantee per-user isolation even if the
-- storage root is ever shared across deployments.
ALTER TABLE transactions
    ADD COLUMN receipt_path VARCHAR(512);
