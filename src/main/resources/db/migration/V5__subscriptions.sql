-- Identité navigateur : jeton aléatoire (haché en base) posé en cookie sécurisé.
-- Ne prouve pas l'identité d'un déclarant, ne donne aucun accès aux dossiers privés.
CREATE TABLE browser_identity
(
    id           uuid PRIMARY KEY,
    token_hash   varchar(64) NOT NULL UNIQUE,
    created_at   timestamptz NOT NULL,
    last_seen_at timestamptz NOT NULL
);

CREATE TABLE bookmark
(
    id                  uuid PRIMARY KEY,
    browser_identity_id uuid        NOT NULL REFERENCES browser_identity (id) ON DELETE CASCADE,
    report_id           uuid        NOT NULL REFERENCES report (id) ON DELETE CASCADE,
    created_at          timestamptz NOT NULL,
    UNIQUE (browser_identity_id, report_id)
);

-- Abonnement e-mail aux mises à jour d'un dossier : consentement explicite,
-- aucune notification avant activation par lien.
CREATE TABLE subscription
(
    id              uuid PRIMARY KEY,
    report_id       uuid         NOT NULL REFERENCES report (id) ON DELETE CASCADE,
    email           varchar(254) NOT NULL,
    status          varchar(24)  NOT NULL
        CHECK (status IN ('PENDING', 'ACTIVE', 'UNSUBSCRIBED')),
    created_at      timestamptz  NOT NULL,
    activated_at    timestamptz,
    unsubscribed_at timestamptz,
    UNIQUE (report_id, email)
);
CREATE INDEX idx_subscription_report ON subscription (report_id) WHERE status = 'ACTIVE';

-- Jetons d'action à usage unique : forte entropie, hachés, expirables, une seule finalité.
CREATE TABLE action_token
(
    id              uuid PRIMARY KEY,
    token_hash      varchar(64) NOT NULL UNIQUE,
    purpose         varchar(40) NOT NULL
        CHECK (purpose IN ('SUBSCRIPTION_CONFIRM', 'SUBSCRIPTION_UNSUBSCRIBE')),
    subscription_id uuid        NOT NULL REFERENCES subscription (id) ON DELETE CASCADE,
    expires_at      timestamptz NOT NULL,
    used_at         timestamptz,
    created_at      timestamptz NOT NULL
);
