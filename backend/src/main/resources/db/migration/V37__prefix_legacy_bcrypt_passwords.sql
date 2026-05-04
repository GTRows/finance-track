UPDATE users
SET password = '{bcrypt}' || password
WHERE password LIKE '$2_%'
  AND password NOT LIKE '{%}%';
