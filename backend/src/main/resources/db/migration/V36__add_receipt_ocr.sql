-- Receipt OCR enrichment columns (Phase 23.A9). The OCR worker fans out
-- pending receipts asynchronously, persists extracted text in `ocr_text`,
-- and tracks lifecycle in `ocr_status` ({PENDING, IN_PROGRESS, SUCCESS,
-- FAILED, RETRY}). The columns are nullable because they are only
-- meaningful when a receipt is attached.
ALTER TABLE transactions
    ADD COLUMN ocr_status VARCHAR(16),
    ADD COLUMN ocr_text TEXT,
    ADD COLUMN ocr_completed_at TIMESTAMPTZ;

-- Partial index keeps the worker scan cheap: only rows that need OCR
-- attention show up here, regardless of how many transactions exist.
CREATE INDEX idx_transactions_ocr_pending
    ON transactions (ocr_status)
    WHERE ocr_status IN ('PENDING', 'RETRY');
