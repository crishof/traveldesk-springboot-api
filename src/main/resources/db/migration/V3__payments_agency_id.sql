-- Refuerzo de aislamiento: tbl_payments solo se acotaba indirectamente via su Sale.
-- Se anade agency_id explicito (defensa en profundidad y base para RLS).

ALTER TABLE tbl_payments ADD COLUMN agency_id uuid;

-- Backfill: la agencia del pago es la de su venta.
UPDATE tbl_payments p
SET agency_id = s.agency_id
FROM tbl_sales s
WHERE p.sale_id = s.id;

ALTER TABLE tbl_payments ALTER COLUMN agency_id SET NOT NULL;

ALTER TABLE tbl_payments
    ADD CONSTRAINT fk_payments_agency
    FOREIGN KEY (agency_id) REFERENCES tbl_agencies (id);

CREATE INDEX idx_payments_agency_id ON tbl_payments (agency_id);
