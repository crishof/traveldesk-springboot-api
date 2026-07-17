-- Refuerzo de aislamiento multi-tenant: account_payments solo se acotaba por user_id.
-- Se anade agency_id (defensa en profundidad y base para RLS).

ALTER TABLE account_payments ADD COLUMN agency_id uuid;

-- Backfill: la agencia del cobro es la del usuario propietario.
UPDATE account_payments ap
SET agency_id = u.agency_id
FROM tbl_users u
WHERE ap.user_id = u.id;

-- A partir de aqui es obligatoria y con integridad referencial.
ALTER TABLE account_payments ALTER COLUMN agency_id SET NOT NULL;

ALTER TABLE account_payments
    ADD CONSTRAINT fk_account_payments_agency
    FOREIGN KEY (agency_id) REFERENCES tbl_agencies (id);

CREATE INDEX idx_account_payments_agency_id ON account_payments (agency_id);
