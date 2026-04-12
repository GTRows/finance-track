-- Rename legacy BIREYSEL portfolio type to INDIVIDUAL and update the default.
-- New supported values: INDIVIDUAL, BES, RETIREMENT, EMERGENCY, STOCKS, CRYPTO, REAL_ESTATE, OTHER

UPDATE portfolios SET portfolio_type = 'INDIVIDUAL' WHERE portfolio_type = 'BIREYSEL';

ALTER TABLE portfolios ALTER COLUMN portfolio_type SET DEFAULT 'INDIVIDUAL';
