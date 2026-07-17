-- Row Level Security (RLS) para aislamiento multi-tenant a nivel de base de datos.
--
-- IMPORTANTE: queda INACTIVO mientras la app conecte como superusuario `postgres`
-- (los superusuarios y BYPASSRLS ignoran RLS). Para ACTIVARLO:
--   1) definir contrasena del rol:   ALTER ROLE traveldesk_app WITH PASSWORD '...';
--   2) apuntar el datasource de la app a traveldesk_app (dev/prod/Railway);
--   3) mantener Flyway con un usuario admin (postgres) via spring.flyway.username/password,
--      porque traveldesk_app no tiene privilegios de DDL;
--   4) activar el wiring del GUC: RLS_ENABLED=true (aplica SET LOCAL app.current_agency).

-- 1) Rol de aplicacion SIN superusuario ni BYPASSRLS. La contrasena se define fuera del repo.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'traveldesk_app') THEN
        CREATE ROLE traveldesk_app WITH LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
    END IF;
END$$;

GRANT USAGE ON SCHEMA public TO traveldesk_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO traveldesk_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO traveldesk_app;

-- 2) Politica de aislamiento por agencia en cada tabla que tiene agency_id.
--    current_setting('app.current_agency', true) devuelve NULL si no esta fijado -> 0 filas (fail-closed).
DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'tbl_customers', 'tbl_suppliers', 'tbl_sales', 'tbl_bookings',
        'account_payments', 'tbl_payments'
    ]
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('DROP POLICY IF EXISTS agency_isolation ON %I', t);
        -- NULLIF(..., '') convierte tanto "no fijado" (NULL) como cadena vacia en NULL,
        -- de modo que la comparacion da 0 filas (fail-closed) sin error de cast de uuid.
        EXECUTE format(
            'CREATE POLICY agency_isolation ON %I '
            'USING (agency_id = NULLIF(current_setting(''app.current_agency'', true), '''')::uuid) '
            'WITH CHECK (agency_id = NULLIF(current_setting(''app.current_agency'', true), '''')::uuid)',
            t);
    END LOOP;
END$$;
