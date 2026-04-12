-- ============================================================
-- V7: Attach TEFAS fund identifiers to seeded Turkish funds so
-- the price sync scheduler can query the public TEFAS API.
--
-- tefasType values:
--   YAT -- regular investment funds
--   EMK -- pension (BES) funds
-- ============================================================

UPDATE assets
SET metadata = COALESCE(metadata, '{}'::jsonb)
    || jsonb_build_object('tefasCode', 'TTA', 'tefasType', 'YAT')
WHERE symbol = 'TTA';

UPDATE assets
SET metadata = COALESCE(metadata, '{}'::jsonb)
    || jsonb_build_object('tefasCode', 'ITP', 'tefasType', 'YAT')
WHERE symbol = 'ITP';

UPDATE assets
SET metadata = COALESCE(metadata, '{}'::jsonb)
    || jsonb_build_object('tefasCode', 'TIE', 'tefasType', 'YAT')
WHERE symbol = 'TIE';

UPDATE assets
SET metadata = COALESCE(metadata, '{}'::jsonb)
    || jsonb_build_object('tefasCode', 'TMG', 'tefasType', 'YAT')
WHERE symbol = 'TMG';

UPDATE assets
SET metadata = COALESCE(metadata, '{}'::jsonb)
    || jsonb_build_object('tefasCode', 'TI1', 'tefasType', 'YAT')
WHERE symbol = 'TI1';

UPDATE assets
SET metadata = COALESCE(metadata, '{}'::jsonb)
    || jsonb_build_object('tefasCode', 'ABE', 'tefasType', 'EMK')
WHERE symbol = 'ABE';

UPDATE assets
SET metadata = COALESCE(metadata, '{}'::jsonb)
    || jsonb_build_object('tefasCode', 'AH5', 'tefasType', 'EMK')
WHERE symbol = 'AH5';

UPDATE assets
SET metadata = COALESCE(metadata, '{}'::jsonb)
    || jsonb_build_object('tefasCode', 'BHT', 'tefasType', 'EMK')
WHERE symbol = 'BHT';

UPDATE assets
SET metadata = COALESCE(metadata, '{}'::jsonb)
    || jsonb_build_object('tefasCode', 'BGL', 'tefasType', 'EMK')
WHERE symbol = 'BGL';

UPDATE assets
SET metadata = COALESCE(metadata, '{}'::jsonb)
    || jsonb_build_object('tefasCode', 'AH3', 'tefasType', 'EMK')
WHERE symbol = 'AH3';
