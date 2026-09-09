CREATE TABLE category_group
(
    id       uuid PRIMARY KEY,
    code     varchar(64)  NOT NULL UNIQUE,
    label_fr varchar(160) NOT NULL,
    label_ar varchar(160) NOT NULL,
    sort     int          NOT NULL DEFAULT 0,
    active   boolean      NOT NULL DEFAULT true
);

CREATE TABLE service_type
(
    id                   uuid PRIMARY KEY,
    group_id             uuid         NOT NULL REFERENCES category_group (id),
    code                 varchar(80)  NOT NULL UNIQUE,
    label_fr             varchar(200) NOT NULL,
    label_ar             varchar(200) NOT NULL,
    help_fr              text,
    help_ar              text,
    -- La description standard peut être désactivée par type (variante affiches politiques observée)
    standard_description boolean      NOT NULL DEFAULT true,
    active               boolean      NOT NULL DEFAULT true,
    sort                 int          NOT NULL DEFAULT 0
);

CREATE INDEX idx_service_type_group ON service_type (group_id);

CREATE TABLE field_definition
(
    id       uuid PRIMARY KEY,
    code     varchar(64)  NOT NULL UNIQUE,
    label_fr varchar(200) NOT NULL,
    label_ar varchar(200) NOT NULL,
    -- TEXT | SELECT : schéma volontairement borné, aucune exécution de code stocké
    kind     varchar(16)  NOT NULL CHECK (kind IN ('TEXT', 'SELECT')),
    required boolean      NOT NULL DEFAULT false,
    -- private = jamais publié (ex. plaque d'immatriculation)
    is_public boolean     NOT NULL DEFAULT false,
    max_len  int          NOT NULL DEFAULT 120,
    sort     int          NOT NULL DEFAULT 0
);

CREATE TABLE service_type_field
(
    service_type_id     uuid NOT NULL REFERENCES service_type (id) ON DELETE CASCADE,
    field_definition_id uuid NOT NULL REFERENCES field_definition (id),
    PRIMARY KEY (service_type_id, field_definition_id)
);

CREATE TABLE field_option
(
    id                  uuid PRIMARY KEY,
    field_definition_id uuid         NOT NULL REFERENCES field_definition (id) ON DELETE CASCADE,
    code                varchar(64)  NOT NULL,
    label_fr            varchar(200) NOT NULL,
    label_ar            varchar(200) NOT NULL,
    active              boolean      NOT NULL DEFAULT true,
    sort                int          NOT NULL DEFAULT 0,
    UNIQUE (field_definition_id, code)
);

CREATE TABLE routing_rule
(
    id              uuid PRIMARY KEY,
    service_type_id uuid    NOT NULL REFERENCES service_type (id) ON DELETE CASCADE,
    department_id   uuid    NOT NULL REFERENCES department (id),
    active          boolean NOT NULL DEFAULT true,
    UNIQUE (service_type_id)
);
