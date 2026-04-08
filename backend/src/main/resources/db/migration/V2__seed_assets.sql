-- ============================================================
-- V2: Seed known financial instruments
-- ============================================================

INSERT INTO assets (symbol, name, asset_type, currency) VALUES
    ('BTC',  'Bitcoin',              'CRYPTO',   'USD'),
    ('ETH',  'Ethereum',             'CRYPTO',   'USD'),
    ('TTA',  'Altin (TTA)',          'GOLD',     'TRY'),
    ('ITP',  'ITP Teknoloji Fonu',   'FUND',     'TRY'),
    ('TIE',  'TIE BIST30 Fonu',     'FUND',     'TRY'),
    ('TMG',  'TMG Yabanci Fonu',     'FUND',     'TRY'),
    ('TI1',  'TI1 Para Piyasasi',   'FUND',     'TRY'),
    ('ABE',  'ABE S&P500 (BES)',     'FUND',     'TRY'),
    ('AH5',  'AH5 Hisse (BES)',     'FUND',     'TRY'),
    ('BHT',  'BHT Teknoloji (BES)', 'FUND',     'TRY'),
    ('BGL',  'BGL Altin (BES)',     'FUND',     'TRY'),
    ('AH3',  'AH3 Eurobond (BES)',  'FUND',     'TRY'),
    ('USD',  'Dolar',               'CURRENCY', 'USD'),
    ('EUR',  'Euro',                'CURRENCY', 'EUR');
