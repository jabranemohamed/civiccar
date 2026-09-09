-- Outbox transactionnelle : les e-mails sont enregistrés dans la même transaction
-- que l'action métier, puis envoyés par un job avec reprise bornée.
CREATE TABLE outbox_message
(
    id              uuid PRIMARY KEY,
    kind            varchar(40)  NOT NULL,
    payload         jsonb        NOT NULL,
    status          varchar(24)  NOT NULL
        CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    attempts        int          NOT NULL DEFAULT 0,
    next_attempt_at timestamptz  NOT NULL,
    dedup_key       varchar(160) UNIQUE,
    created_at      timestamptz  NOT NULL,
    sent_at         timestamptz,
    last_error      varchar(500)
);
CREATE INDEX idx_outbox_pending ON outbox_message (next_attempt_at) WHERE status = 'PENDING';

CREATE TABLE notification_delivery
(
    id              uuid PRIMARY KEY,
    subscription_id uuid REFERENCES subscription (id) ON DELETE CASCADE,
    report_id       uuid REFERENCES report (id) ON DELETE CASCADE,
    kind            varchar(40)  NOT NULL,
    dedup_key       varchar(160) NOT NULL UNIQUE,
    status          varchar(24)  NOT NULL,
    created_at      timestamptz  NOT NULL
);

CREATE TABLE contact_message
(
    id             uuid PRIMARY KEY,
    name           varchar(160)  NOT NULL,
    email          varchar(254)  NOT NULL,
    body           text          NOT NULL,
    copy_requested boolean       NOT NULL DEFAULT false,
    consent_at     timestamptz   NOT NULL,
    status         varchar(24)   NOT NULL DEFAULT 'NEW'
        CHECK (status IN ('NEW', 'PROCESSED')),
    created_at     timestamptz   NOT NULL,
    processed_by   uuid REFERENCES staff_user (id),
    processed_at   timestamptz
);

CREATE TABLE content_page
(
    id         uuid PRIMARY KEY,
    slug       varchar(64)  NOT NULL UNIQUE,
    title_fr   varchar(200) NOT NULL,
    title_ar   varchar(200) NOT NULL,
    body_fr    text         NOT NULL,
    body_ar    text         NOT NULL,
    updated_at timestamptz  NOT NULL,
    updated_by uuid REFERENCES staff_user (id)
);

CREATE TABLE audit_event
(
    id          uuid PRIMARY KEY,
    actor       varchar(120) NOT NULL,
    action      varchar(64)  NOT NULL,
    target_type varchar(40)  NOT NULL,
    target_id   varchar(64)  NOT NULL,
    detail      jsonb,
    created_at  timestamptz  NOT NULL
);
CREATE INDEX idx_audit_target ON audit_event (target_type, target_id, created_at);

CREATE TABLE app_setting
(
    key   varchar(80) PRIMARY KEY,
    value text NOT NULL
);
