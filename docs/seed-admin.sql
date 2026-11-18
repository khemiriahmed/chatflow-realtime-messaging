-- Promouvoir en administrateur le premier compte créé.
-- Remplacer l'email ci-dessous par celui utilisé lors de l'inscription.
UPDATE users
SET role = 'ADMIN'
WHERE email = 'admin@chatflow.local';

-- Vérification
SELECT id, name, email, role, enabled FROM users WHERE role = 'ADMIN';
