-- Delete admin user to reset password
DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username = 'admin');
DELETE FROM users WHERE username = 'admin';
