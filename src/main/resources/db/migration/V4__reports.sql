-- Séquence transactionnellement sûre pour la référence humaine (jamais MAX(id)+1)
CREATE SEQUENCE report_reference_seq START 100;

CREATE TABLE report
(
    id                  uuid PRIMARY KEY,
    reference           varchar(20)           NOT NULL UNIQUE,
    service_type_id     uuid                  NOT NULL REFERENCES service_type (id),
    location            geometry(Point, 4326) NOT NULL,
    -- Adresse publique du problème (jamais celle du déclarant)
    address             varchar(300),
    address_details     varchar(300),
    description_private text,
    description_public  text,
    field_values        jsonb                 NOT NULL DEFAULT '{}',
    workflow_status     varchar(24)           NOT NULL
        CHECK (workflow_status IN ('OPEN', 'IN_PROGRESS', 'DONE_OR_ORDERED', 'OUT_OF_SCOPE')),
    publication_status  varchar(24)           NOT NULL
        CHECK (publication_status IN ('PENDING_REVIEW', 'PUBLISHED', 'HIDDEN')),
    department_id       uuid REFERENCES department (id),
    assignee_id         uuid REFERENCES staff_user (id),
    idempotency_key     varchar(80) UNIQUE,
    created_at          timestamptz           NOT NULL,
    updated_at          timestamptz           NOT NULL,
    closed_at           timestamptz,
    archived_at         timestamptz,
    personal_data_purged_at timestamptz,
    version             bigint                NOT NULL DEFAULT 0
);

CREATE INDEX idx_report_location ON report USING gist (location);
CREATE INDEX idx_report_geog ON report USING gist ((location::geography));
CREATE INDEX idx_report_status ON report (workflow_status, publication_status);
CREATE INDEX idx_report_created ON report (created_at DESC);
CREATE INDEX idx_report_department ON report (department_id);
CREATE INDEX idx_report_type ON report (service_type_id);
CREATE INDEX idx_report_closed ON report (closed_at) WHERE closed_at IS NOT NULL;
-- Recherche plein texte insensible aux diacritiques sur la description publique
CREATE INDEX idx_report_search ON report USING gin (f_unaccent(coalesce(description_public, '')) gin_trgm_ops);

CREATE TABLE report_contact
(
    report_id       uuid PRIMARY KEY REFERENCES report (id) ON DELETE CASCADE,
    email           varchar(254),
    phone           varchar(32),
    -- Plaque du véhicule hors d'usage : privée, jamais publiée
    vehicle_plate   varchar(32),
    consent_version varchar(32) NOT NULL,
    consent_at      timestamptz NOT NULL,
    purged          boolean     NOT NULL DEFAULT false
);

CREATE TABLE status_event
(
    id          uuid PRIMARY KEY,
    report_id   uuid        NOT NULL REFERENCES report (id) ON DELETE CASCADE,
    from_status varchar(24),
    to_status   varchar(24) NOT NULL,
    actor_id    uuid REFERENCES staff_user (id),
    reason      varchar(500),
    created_at  timestamptz NOT NULL
);
CREATE INDEX idx_status_event_report ON status_event (report_id, created_at);

CREATE TABLE public_update
(
    id         uuid PRIMARY KEY,
    report_id  uuid        NOT NULL REFERENCES report (id) ON DELETE CASCADE,
    author_id  uuid REFERENCES staff_user (id),
    body       text        NOT NULL,
    created_at timestamptz NOT NULL
);
CREATE INDEX idx_public_update_report ON public_update (report_id, created_at);

CREATE TABLE internal_note
(
    id         uuid PRIMARY KEY,
    report_id  uuid        NOT NULL REFERENCES report (id) ON DELETE CASCADE,
    author_id  uuid        NOT NULL REFERENCES staff_user (id),
    body       text        NOT NULL,
    created_at timestamptz NOT NULL
);
CREATE INDEX idx_internal_note_report ON internal_note (report_id, created_at);

CREATE TABLE moderation_decision
(
    id         uuid PRIMARY KEY,
    report_id  uuid        NOT NULL REFERENCES report (id) ON DELETE CASCADE,
    decision   varchar(32) NOT NULL,
    actor_id   uuid        NOT NULL REFERENCES staff_user (id),
    reason     varchar(500),
    created_at timestamptz NOT NULL
);
CREATE INDEX idx_moderation_report ON moderation_decision (report_id, created_at);

CREATE TABLE duplicate_link
(
    id           uuid PRIMARY KEY,
    report_id    uuid        NOT NULL REFERENCES report (id) ON DELETE CASCADE,
    canonical_id uuid        NOT NULL REFERENCES report (id),
    created_by   uuid        NOT NULL REFERENCES staff_user (id),
    created_at   timestamptz NOT NULL,
    UNIQUE (report_id),
    CHECK (report_id <> canonical_id)
);

CREATE TABLE media_asset
(
    id                uuid PRIMARY KEY,
    report_id         uuid        NOT NULL REFERENCES report (id) ON DELETE CASCADE,
    storage_key       varchar(200) NOT NULL UNIQUE,
    thumb_key         varchar(200),
    content_type      varchar(60) NOT NULL,
    size_bytes        bigint      NOT NULL,
    width             int         NOT NULL,
    height            int         NOT NULL,
    moderation_status varchar(24) NOT NULL
        CHECK (moderation_status IN ('PENDING', 'APPROVED', 'REMOVED')),
    sort              int         NOT NULL DEFAULT 0,
    created_at        timestamptz NOT NULL
);
CREATE INDEX idx_media_report ON media_asset (report_id);
