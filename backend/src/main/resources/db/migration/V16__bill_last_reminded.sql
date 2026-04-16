-- Track the date we last sent a reminder for each bill so the daily scheduler
-- does not email the user repeatedly within the same reminder window.

ALTER TABLE bills
    ADD COLUMN last_reminded_on DATE;
