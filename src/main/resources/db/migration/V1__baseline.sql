-- Baseline schema for TravelDesk API.
-- Generado con pg_dump del esquema creado por Hibernate (ddl-auto) el 2026-07-17.
-- A partir de aqui el esquema se versiona con Flyway; ddl-auto pasa a 'validate'.

CREATE TABLE public.account_payments (
    id uuid NOT NULL,
    amount numeric(38,2),
    currency character varying(255),
    date date,
    description character varying(255),
    user_id uuid,
    CONSTRAINT account_payments_currency_check CHECK (((currency)::text = ANY ((ARRAY['EUR'::character varying, 'USD'::character varying])::text[])))
);
CREATE TABLE public.tbl_agencies (
    id uuid NOT NULL,
    commission_type character varying(30) NOT NULL,
    commission_value numeric(10,2) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    currency character varying(10) NOT NULL,
    name character varying(120) NOT NULL,
    normalized_name character varying(120) NOT NULL,
    primary_color character varying(20) NOT NULL,
    secondary_color character varying(20) NOT NULL,
    theme_mode character varying(20) NOT NULL,
    time_zone character varying(60) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT tbl_agencies_commission_type_check CHECK (((commission_type)::text = ANY ((ARRAY['FIXED'::character varying, 'PERCENTAGE'::character varying])::text[]))),
    CONSTRAINT tbl_agencies_theme_mode_check CHECK (((theme_mode)::text = ANY ((ARRAY['LIGHT'::character varying, 'DARK'::character varying])::text[])))
);
CREATE TABLE public.tbl_bookings (
    id uuid NOT NULL,
    amount numeric(12,2) NOT NULL,
    converted_amount numeric(12,2),
    created_at timestamp(6) with time zone NOT NULL,
    currency character varying(10) NOT NULL,
    departure_date date,
    description character varying(120),
    exchange_rate numeric(12,6),
    original_amount numeric(12,2),
    payment_date date,
    reference character varying(50),
    return_date date,
    source_currency character varying(10),
    status character varying(20) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    agency_id uuid NOT NULL,
    created_by_user_id uuid NOT NULL,
    customer_id uuid NOT NULL,
    supplier_id uuid,
    CONSTRAINT tbl_bookings_status_check CHECK (((status)::text = ANY ((ARRAY['CREATED'::character varying, 'PENDING'::character varying, 'CONFIRMED'::character varying, 'PAID'::character varying, 'CANCELLED'::character varying])::text[])))
);
CREATE TABLE public.tbl_customers (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    email character varying(150),
    full_name character varying(120) NOT NULL,
    passport_number character varying(10),
    phone character varying(30),
    updated_at timestamp(6) with time zone NOT NULL,
    agency_id uuid NOT NULL
);
CREATE TABLE public.tbl_email_verification_tokens (
    id uuid NOT NULL,
    code character varying(255) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    expiry_date timestamp(6) with time zone NOT NULL,
    used boolean NOT NULL,
    user_id uuid NOT NULL
);
CREATE TABLE public.tbl_invitation_tokens (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    email character varying(150) NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    role character varying(20) NOT NULL,
    token character varying(200) NOT NULL,
    used boolean NOT NULL,
    agency_id uuid,
    invited_by_user_id uuid,
    CONSTRAINT tbl_invitation_tokens_role_check CHECK (((role)::text = ANY ((ARRAY['ADMIN'::character varying, 'USER'::character varying])::text[])))
);
CREATE TABLE public.tbl_password_reset_tokens (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    expiry_date timestamp(6) with time zone NOT NULL,
    token character varying(255) NOT NULL,
    used boolean NOT NULL,
    user_id uuid NOT NULL
);
CREATE TABLE public.tbl_payments (
    id uuid NOT NULL,
    converted_amount numeric(12,2) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    description character varying(255),
    exchange_rate numeric(12,6) NOT NULL,
    original_amount numeric(12,2) NOT NULL,
    payment_date timestamp(6) with time zone NOT NULL,
    source_currency character varying(10) NOT NULL,
    sale_id uuid NOT NULL
);
CREATE TABLE public.tbl_refresh_tokens (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    revoked boolean NOT NULL,
    token character varying(500) NOT NULL,
    user_id uuid NOT NULL
);
CREATE TABLE public.tbl_sales (
    id uuid NOT NULL,
    amount numeric(12,2) NOT NULL,
    commission_percentage numeric(5,2),
    created_at timestamp(6) with time zone NOT NULL,
    currency character varying(10) NOT NULL,
    departure_date date,
    description character varying(255),
    destination character varying(120) NOT NULL,
    paid_amount numeric(12,2) NOT NULL,
    sale_date timestamp(6) with time zone NOT NULL,
    status character varying(20) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    agency_id uuid NOT NULL,
    created_by_user_id uuid NOT NULL,
    customer_id uuid NOT NULL,
    CONSTRAINT tbl_sales_status_check CHECK (((status)::text = ANY ((ARRAY['CREATED'::character varying, 'CONFIRMED'::character varying, 'CANCELLED'::character varying])::text[])))
);
CREATE TABLE public.tbl_security_accounts (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    email_verified boolean NOT NULL,
    enabled boolean NOT NULL,
    locked boolean NOT NULL,
    password_hash character varying(100) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    user_id uuid NOT NULL
);
CREATE TABLE public.tbl_suppliers (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    currency character varying(10) NOT NULL,
    email character varying(150),
    name character varying(120) NOT NULL,
    phone character varying(30) NOT NULL,
    type character varying(30) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    agency_id uuid NOT NULL,
    CONSTRAINT tbl_suppliers_type_check CHECK (((type)::text = ANY ((ARRAY['AIRLINE'::character varying, 'AIR_CONSOLIDATOR'::character varying, 'BED_BANK'::character varying, 'TOUR_OPERATOR'::character varying, 'TRANSFER'::character varying, 'CRUISE'::character varying, 'FERRY'::character varying, 'TRAIN'::character varying, 'TICKET_PROVIDER'::character varying, 'LOCAL_TOUR_OPERATOR'::character varying, 'INSURANCE'::character varying, 'OTHER'::character varying])::text[])))
);
CREATE TABLE public.tbl_users (
    id uuid NOT NULL,
    commission_percentage numeric(5,2) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    email character varying(150) NOT NULL,
    full_name character varying(120) NOT NULL,
    role character varying(20) NOT NULL,
    status character varying(30) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    agency_id uuid NOT NULL,
    CONSTRAINT tbl_users_role_check CHECK (((role)::text = ANY ((ARRAY['ADMIN'::character varying, 'USER'::character varying])::text[]))),
    CONSTRAINT tbl_users_status_check CHECK (((status)::text = ANY ((ARRAY['INVITED'::character varying, 'ACTIVE'::character varying, 'PENDING_VERIFICATION'::character varying, 'INACTIVE'::character varying, 'BLOCKED'::character varying])::text[])))
);
ALTER TABLE ONLY public.account_payments
    ADD CONSTRAINT account_payments_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.tbl_agencies
    ADD CONSTRAINT tbl_agencies_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.tbl_bookings
    ADD CONSTRAINT tbl_bookings_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.tbl_customers
    ADD CONSTRAINT tbl_customers_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.tbl_email_verification_tokens
    ADD CONSTRAINT tbl_email_verification_tokens_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.tbl_invitation_tokens
    ADD CONSTRAINT tbl_invitation_tokens_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.tbl_password_reset_tokens
    ADD CONSTRAINT tbl_password_reset_tokens_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.tbl_payments
    ADD CONSTRAINT tbl_payments_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.tbl_refresh_tokens
    ADD CONSTRAINT tbl_refresh_tokens_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.tbl_sales
    ADD CONSTRAINT tbl_sales_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.tbl_security_accounts
    ADD CONSTRAINT tbl_security_accounts_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.tbl_suppliers
    ADD CONSTRAINT tbl_suppliers_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.tbl_users
    ADD CONSTRAINT tbl_users_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.tbl_bookings
    ADD CONSTRAINT uk_booking_agency_reference UNIQUE (agency_id, reference);
ALTER TABLE ONLY public.tbl_customers
    ADD CONSTRAINT uk_customer_agency_email UNIQUE (agency_id, email);
ALTER TABLE ONLY public.tbl_security_accounts
    ADD CONSTRAINT uk_security_account_user UNIQUE (user_id);
ALTER TABLE ONLY public.tbl_suppliers
    ADD CONSTRAINT uk_supplier_agency_email UNIQUE (agency_id, email);
ALTER TABLE ONLY public.tbl_password_reset_tokens
    ADD CONSTRAINT ukcuasqc7049kj092a8dxqdm4xj UNIQUE (token);
ALTER TABLE ONLY public.tbl_agencies
    ADD CONSTRAINT ukfb63qp6vfoalbvd7viw6g2e80 UNIQUE (normalized_name);
ALTER TABLE ONLY public.tbl_invitation_tokens
    ADD CONSTRAINT ukiqufbu4rxsyspkd4296xs8lb1 UNIQUE (token);
ALTER TABLE ONLY public.tbl_users
    ADD CONSTRAINT ukj562wwmipqt96rkoqbo0jc34 UNIQUE (email);
ALTER TABLE ONLY public.tbl_refresh_tokens
    ADD CONSTRAINT ukoe5onsg4tlagf6payo6yvhm44 UNIQUE (token);
CREATE INDEX idx_email_verification_code ON public.tbl_email_verification_tokens USING btree (code);
CREATE INDEX idx_email_verification_expiry ON public.tbl_email_verification_tokens USING btree (expiry_date);
CREATE INDEX idx_invitation_token_email ON public.tbl_invitation_tokens USING btree (email);
CREATE INDEX idx_invitation_token_expires_at ON public.tbl_invitation_tokens USING btree (expires_at);
CREATE INDEX idx_invitation_token_value ON public.tbl_invitation_tokens USING btree (token);
CREATE INDEX idx_reset_token_expiry ON public.tbl_password_reset_tokens USING btree (expiry_date);
CREATE INDEX idx_reset_token_token ON public.tbl_password_reset_tokens USING btree (token);
ALTER TABLE ONLY public.tbl_bookings
    ADD CONSTRAINT fk4os8njuq0bbb019c7gxds11yv FOREIGN KEY (customer_id) REFERENCES public.tbl_customers(id);
ALTER TABLE ONLY public.tbl_refresh_tokens
    ADD CONSTRAINT fk9jmfuqwxrf8vt2uplm1kp4u7e FOREIGN KEY (user_id) REFERENCES public.tbl_users(id);
ALTER TABLE ONLY public.tbl_password_reset_tokens
    ADD CONSTRAINT fkastlpmqn8rfp3a7deljg4s9rq FOREIGN KEY (user_id) REFERENCES public.tbl_users(id);
ALTER TABLE ONLY public.tbl_sales
    ADD CONSTRAINT fkd56ji9ncra86ln4bv8m670k06 FOREIGN KEY (customer_id) REFERENCES public.tbl_customers(id);
ALTER TABLE ONLY public.tbl_invitation_tokens
    ADD CONSTRAINT fke7jpnx505pwpx8r3ho7owwhhq FOREIGN KEY (agency_id) REFERENCES public.tbl_agencies(id);
ALTER TABLE ONLY public.tbl_email_verification_tokens
    ADD CONSTRAINT fkf4n7lqa9904xn7mjmca5q3fl9 FOREIGN KEY (user_id) REFERENCES public.tbl_users(id);
ALTER TABLE ONLY public.tbl_payments
    ADD CONSTRAINT fkg4kqbo9e1m5b0dpdc9rdk32b8 FOREIGN KEY (sale_id) REFERENCES public.tbl_sales(id);
ALTER TABLE ONLY public.tbl_security_accounts
    ADD CONSTRAINT fkka6u6x5c7076ng4ea6pdbfsk5 FOREIGN KEY (user_id) REFERENCES public.tbl_users(id);
ALTER TABLE ONLY public.tbl_customers
    ADD CONSTRAINT fkl52mt7yukyr8swg6eagmwwi64 FOREIGN KEY (agency_id) REFERENCES public.tbl_agencies(id);
ALTER TABLE ONLY public.tbl_sales
    ADD CONSTRAINT fklwgjf0eapygsodcgi8ogixvhc FOREIGN KEY (agency_id) REFERENCES public.tbl_agencies(id);
ALTER TABLE ONLY public.tbl_bookings
    ADD CONSTRAINT fklxy8fnsid3gcsdrysyipfl5ny FOREIGN KEY (agency_id) REFERENCES public.tbl_agencies(id);
ALTER TABLE ONLY public.tbl_invitation_tokens
    ADD CONSTRAINT fkr6ovqsii2ujdp1mh0bhb395ph FOREIGN KEY (invited_by_user_id) REFERENCES public.tbl_users(id);
ALTER TABLE ONLY public.tbl_suppliers
    ADD CONSTRAINT fkr9geubg142oq3arpeb19w637 FOREIGN KEY (agency_id) REFERENCES public.tbl_agencies(id);
ALTER TABLE ONLY public.tbl_sales
    ADD CONSTRAINT fks3rfhoisohube1wxo3ta7fxxn FOREIGN KEY (created_by_user_id) REFERENCES public.tbl_users(id);
ALTER TABLE ONLY public.tbl_bookings
    ADD CONSTRAINT fksi4id1il0j2saplugjjblph66 FOREIGN KEY (created_by_user_id) REFERENCES public.tbl_users(id);
ALTER TABLE ONLY public.tbl_users
    ADD CONSTRAINT fkte91iffeverf5wrsijckdio2y FOREIGN KEY (agency_id) REFERENCES public.tbl_agencies(id);
ALTER TABLE ONLY public.tbl_bookings
    ADD CONSTRAINT fkw08bvrb309khdchd7abonv1f FOREIGN KEY (supplier_id) REFERENCES public.tbl_suppliers(id);
