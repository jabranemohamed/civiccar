CREATE TABLE department
(
    id      uuid PRIMARY KEY,
    code    varchar(64)  NOT NULL UNIQUE,
    name_fr varchar(160) NOT NULL,
    name_ar varchar(160) NOT NULL,
    demo    boolean      NOT NULL DEFAULT true,
    active  boolean      NOT NULL DEFAULT true
);

CREATE TABLE staff_user
(
    id            uuid PRIMARY KEY,
    username      varchar(120) NOT NULL UNIQUE,
    password_hash varchar(255) NOT NULL,
    display_name  varchar(160) NOT NULL,
    enabled       boolean      NOT NULL DEFAULT true,
    created_at    timestamptz  NOT NULL
);

CREATE TABLE staff_user_role
(
    staff_user_id uuid        NOT NULL REFERENCES staff_user (id) ON DELETE CASCADE,
    role          varchar(32) NOT NULL,
    PRIMARY KEY (staff_user_id, role)
);

CREATE TABLE department_membership
(
    staff_user_id uuid NOT NULL REFERENCES staff_user (id) ON DELETE CASCADE,
    department_id uuid NOT NULL REFERENCES department (id) ON DELETE CASCADE,
    PRIMARY KEY (staff_user_id, department_id)
);

-- Équipes de démonstration : fictives, aucune correspondance vérifiée avec une
-- administration ou un opérateur tunisien réel. Aucune transmission automatique
-- à une autorité réelle.
INSERT INTO department (id, code, name_fr, name_ar, demo, active)
VALUES ('c1000000-0000-0000-0000-000000000001', 'DEMO_CLEANLINESS', 'Équipe démo — Propreté urbaine',
        'فريق تجريبي — النظافة الحضرية', true, true),
       ('c1000000-0000-0000-0000-000000000002', 'DEMO_ROADS', 'Équipe démo — Voirie',
        'فريق تجريبي — الطرقات', true, true),
       ('c1000000-0000-0000-0000-000000000003', 'DEMO_LIGHTING', 'Équipe démo — Éclairage public',
        'فريق تجريبي — الإنارة العمومية', true, true),
       ('c1000000-0000-0000-0000-000000000004', 'DEMO_PUBLIC_SPACES', 'Équipe démo — Espaces publics',
        'فريق تجريبي — الفضاءات العمومية', true, true),
       ('c1000000-0000-0000-0000-000000000005', 'TRIAGE', 'File de triage',
        'قائمة الفرز', true, true);
