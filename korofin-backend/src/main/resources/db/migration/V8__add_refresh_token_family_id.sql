-- Detección de reuso de refresh tokens (ver Javadoc de RefreshTokenService): family_id identifica
-- la cadena de rotación de una sesión de login. Al rotar, el token nuevo hereda el family_id del
-- viejo; si se presenta un token ya rotado (revoked_at no nulo pero firma/expiración válidas), se
-- revoca toda la familia para forzar re-login del atacante y de la sesión legítima comprometida.

ALTER TABLE refresh_tokens ADD COLUMN family_id UUID;

-- Backfill: no hay forma de reconstruir la cadena real de rotación de las filas preexistentes (esa
-- relación nunca se guardó), así que cada fila existente arranca su propia familia de un solo
-- elemento. gen_random_uuid() es una función nativa de PostgreSQL 13+ (no requiere pgcrypto).
UPDATE refresh_tokens SET family_id = gen_random_uuid() WHERE family_id IS NULL;

ALTER TABLE refresh_tokens ALTER COLUMN family_id SET NOT NULL;

CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens (family_id);
