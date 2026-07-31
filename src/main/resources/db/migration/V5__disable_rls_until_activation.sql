-- RLS queda DESHABILITADO en el esquema desplegado hasta su activacion manual.
--
-- Motivo: V4 hacia ENABLE + FORCE ROW LEVEL SECURITY. Si la app se conecta con un
-- usuario que NO es superusuario pero SI es dueno de las tablas (posible en Railway),
-- FORCE aplicaria las politicas y, al no estar fijado el GUC app.current_agency
-- (RLS_ENABLED=false), toda consulta devolveria 0 filas -> datos "invisibles".
--
-- Las politicas y el rol creados en V4 se conservan (inertes con RLS deshabilitado).
-- Para ACTIVAR RLS mas adelante, ademas de cambiar el datasource al rol traveldesk_app
-- y poner RLS_ENABLED=true (ver docs/audit/03), hay que re-habilitarlo con:
--   ALTER TABLE <tabla> ENABLE ROW LEVEL SECURITY;
--   ALTER TABLE <tabla> FORCE ROW LEVEL SECURITY;

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'tbl_customers', 'tbl_suppliers', 'tbl_sales', 'tbl_bookings',
        'account_payments', 'tbl_payments'
    ]
    LOOP
        EXECUTE format('ALTER TABLE %I NO FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I DISABLE ROW LEVEL SECURITY', t);
    END LOOP;
END$$;
