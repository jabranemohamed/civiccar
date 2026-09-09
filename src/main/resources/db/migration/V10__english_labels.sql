-- Ajout de l'anglais (3e langue). Colonnes nullables : l'interface replie sur le
-- français lorsque la traduction anglaise n'est pas renseignée.

ALTER TABLE category_group ADD COLUMN label_en varchar(160);
ALTER TABLE service_type ADD COLUMN label_en varchar(200);
ALTER TABLE service_type ADD COLUMN help_en text;
ALTER TABLE field_definition ADD COLUMN label_en varchar(200);
ALTER TABLE field_option ADD COLUMN label_en varchar(200);
ALTER TABLE department ADD COLUMN name_en varchar(160);
ALTER TABLE content_page ADD COLUMN title_en varchar(200);
ALTER TABLE content_page ADD COLUMN body_en text;

-- ===== Familles =====
UPDATE category_group SET label_en = v.label FROM (VALUES
 ('CLOTHING_CONTAINERS', 'Clothing containers'),
 ('TRAFFIC_LIGHTS', 'Traffic lights'),
 ('BENCHES', 'Benches'),
 ('FOUNTAINS', 'Fountains'),
 ('MONUMENTS', 'Monuments'),
 ('PATHS', 'Footpaths and cycle paths'),
 ('GREEN_STRIPS', 'Green strips'),
 ('TRASH_BINS', 'Litter bins'),
 ('PARKS', 'Parks'),
 ('POLITICAL_POSTERS', 'Political posters'),
 ('PLAYGROUNDS', 'Playgrounds'),
 ('ROADS', 'Roads'),
 ('STREET_LIGHTING', 'Street lighting'),
 ('TRAFFIC_SIGNS', 'Traffic signs'),
 ('RECYCLING_POINTS', 'Recycling points')
) AS v(code, label) WHERE category_group.code = v.code;

-- ===== Types =====
UPDATE service_type SET label_en = v.label FROM (VALUES
 ('CLOTHING_CONTAINER_DAMAGED', 'Damaged container'),
 ('CLOTHING_CONTAINER_FULL', 'Full container'),
 ('CLOTHING_CONTAINER_DIRTY', 'Dirty container'),
 ('CLOTHING_CONTAINER_SURROUNDINGS_DIRTY', 'Dirty surroundings'),
 ('TRAFFIC_LIGHT_DIRTY', 'Dirty traffic light'),
 ('TRAFFIC_LIGHT_DAMAGED', 'Damaged traffic light'),
 ('TRAFFIC_LIGHT_OUT_OF_ORDER', 'Traffic light out of order'),
 ('BENCH_DAMAGED', 'Damaged bench'),
 ('BENCH_DIRTY', 'Dirty bench'),
 ('FOUNTAIN_DAMAGED', 'Damaged fountain'),
 ('FOUNTAIN_DIRTY', 'Dirty fountain'),
 ('MONUMENT_DAMAGED', 'Damaged monument'),
 ('MONUMENT_DIRTY', 'Dirty monument'),
 ('PATH_DIRTY', 'Dirty footpath or cycle path'),
 ('GREEN_STRIP_DIRTY', 'Dirty green strip'),
 ('TRASH_BIN_FULL', 'Full litter bin'),
 ('PARK_DOG_BAG_DISPENSER_EMPTY', 'Empty dog-waste bag dispenser'),
 ('PARK_DAMAGED', 'Damaged park'),
 ('PARK_DIRTY', 'Dirty park'),
 ('POSTER_FORBIDDEN_AREA', 'Poster in a forbidden area or at a bus stop'),
 ('POSTER_DAMAGED', 'Damaged poster'),
 ('POSTER_OBSTRUCTING', 'Poster obstructing visibility or blocking the road or a path'),
 ('POSTER_POORLY_FIXED', 'Poorly fixed or poorly installed poster'),
 ('POSTER_NOT_REMOVED', 'Poster not removed'),
 ('PLAYGROUND_DAMAGED', 'Damaged playground'),
 ('PLAYGROUND_DIRTY', 'Dirty playground'),
 ('ROAD_DIRTY', 'Dirty street'),
 ('ROAD_NOISY_MANHOLE', 'Noisy drain or manhole cover'),
 ('ROAD_BLOCKED_DRAIN', 'Blocked drain'),
 ('ROAD_ABANDONED_VEHICLE', 'Abandoned vehicle'),
 ('STREET_LIGHT_LAMP_OUT', 'Street lamp out of order'),
 ('STREET_LIGHT_STREET_OUT', 'Whole street lighting out of order'),
 ('STREET_LIGHT_DAMAGED', 'Damaged street lamp'),
 ('STREET_LIGHT_DIRTY', 'Dirty street lamp'),
 ('TRAFFIC_SIGN_DAMAGED', 'Damaged sign'),
 ('TRAFFIC_SIGN_DIRTY', 'Dirty sign'),
 ('RECYCLING_SURROUNDINGS_DIRTY', 'Dirty surroundings'),
 ('RECYCLING_CONTAINER_DAMAGED', 'Damaged container'),
 ('RECYCLING_CONTAINER_FULL', 'Full container'),
 ('RECYCLING_CONTAINER_DIRTY', 'Dirty container')
) AS v(code, label) WHERE service_type.code = v.code;

-- Aides contextuelles
UPDATE service_type SET help_en =
 'Indicate the party concerned by the poster. This information identifies the poster and is never used to infer your opinions.'
WHERE code IN ('POSTER_FORBIDDEN_AREA', 'POSTER_DAMAGED', 'POSTER_OBSTRUCTING',
               'POSTER_POORLY_FIXED', 'POSTER_NOT_REMOVED');

UPDATE service_type SET help_en =
 'If it is visible, enter the licence plate in the dedicated field. It remains private and is never published.'
WHERE code = 'ROAD_ABANDONED_VEHICLE';

-- ===== Champs conditionnels =====
UPDATE field_definition SET label_en = 'Party concerned by the poster' WHERE code = 'POLITICAL_PARTY';
UPDATE field_definition SET label_en = 'Licence plate (optional, not published)' WHERE code = 'VEHICLE_PLATE';

UPDATE field_option SET label_en = v.label FROM (VALUES
 ('PARTY_ALPHA', 'Demonstration party Alpha (fictitious)'),
 ('PARTY_BETA', 'Demonstration party Beta (fictitious)'),
 ('PARTY_GAMMA', 'Demonstration party Gamma (fictitious)'),
 ('PARTY_OTHER', 'Other / unknown')
) AS v(code, label) WHERE field_option.code = v.code;

-- ===== Équipes de démonstration =====
UPDATE department SET name_en = v.name FROM (VALUES
 ('DEMO_CLEANLINESS', 'Demo team — Urban cleanliness'),
 ('DEMO_ROADS', 'Demo team — Roads'),
 ('DEMO_LIGHTING', 'Demo team — Street lighting'),
 ('DEMO_PUBLIC_SPACES', 'Demo team — Public spaces'),
 ('TRIAGE', 'Triage queue')
) AS v(code, name) WHERE department.code = v.code;

-- ===== Pages de contenu =====
UPDATE content_page SET title_en = 'Frequently asked questions', body_en =
E'How do I submit a report?\nUse the "Report a problem" button, place the point on the map, choose the type, describe the problem and enter your e-mail. No account is needed.\n\nWhat happens to my contact details?\nYour e-mail and phone remain private and are never published. They are only used to process your report and are automatically deleted 90 days after closure (configurable demonstration choice).\n\nWhy is my report not visible?\nEvery report is reviewed before publication. An unpublished report is nevertheless forwarded and processed.\n\nHow do I follow a report?\nOn a report page, use "Follow" to save it on this device, or subscribe by e-mail: the subscription only becomes active after confirmation via a link.\n\nWhat does "resolved or work ordered" mean?\nThe case has been handled or work has been ordered from the competent team. It does not guarantee that the problem is already physically repaired.\n\nCan I search for a specific report?\nYes, by its reference (for example 000123-2026) or by a word from its description, in French, Arabic or English.'
WHERE slug = 'faq';

UPDATE content_page SET title_en = 'How the service works', body_en =
E'1. You locate the problem on the map of Tunis and choose its type from the catalogue.\n2. You describe the problem and attach up to three optional photos.\n3. You enter your e-mail (private) and confirm your consent.\n4. The report is reviewed by moderation, then published and forwarded to the competent processing team.\n5. You follow the progress: open, in progress, resolved or work ordered, or outside municipal responsibility.\n\nThis service is a demonstration application: the processing teams are fictitious and no case is forwarded to any real authority.'
WHERE slug = 'how';

UPDATE content_page SET title_en = 'Terms of use', body_en =
E'CivicCare Tunis is a demonstration application for reporting urban problems in the city of Tunis.\n\nBy using this service, you agree to:\n- only report real problems concerning public space;\n- not publish personal data of third parties (faces, plates, names) in descriptions or photos;\n- not use the service for emergencies: contact the emergency services directly;\n- not send unlawful, defamatory or off-topic content.\n\nReports may be reworded, hidden or rejected by moderation. An unpublished report is still processed.\n\nOperator (demonstration): [Operator to be defined before any public use].'
WHERE slug = 'terms';

UPDATE content_page SET title_en = 'Privacy', body_en =
E'Data collected:\n- location and description of the problem (published after moderation);\n- your e-mail and, if provided, your phone number (never published);\n- the plate of an abandoned vehicle if you enter it (never published);\n- attached photos (published only after approval, EXIF metadata removed).\n\nPurposes: processing the report, notifications if you activate them, aggregated internal statistics.\n\nRetention (configurable demonstration choices): closed cases are archived after 30 days; personal data is deleted 90 days after closure. Subscriptions are deleted with the case they relate to.\n\nYour choices: the e-mail subscription requires explicit activation and can be cancelled at any time via the unsubscribe link.\n\nThis text describes how the demonstration application works; it is not a legal commitment by a real operator. The legal requirements applicable in Tunisia (in particular Law No. 2004-63 on the protection of personal data) must be verified by the operator before any public deployment.'
WHERE slug = 'privacy';

UPDATE content_page SET title_en = 'Legal notice', body_en =
E'CivicCare Tunis — demonstration application.\n\nPublisher: [Operator to be defined before any public use].\nHosting: [Host to be defined].\nContact: via the service contact form.\n\nThis service is not an official service of the municipality of Tunis and has no institutional affiliation. The processing teams shown are fictitious.\n\nMap background: © OpenStreetMap contributors (ODbL licence). Geographic boundary derived from OpenStreetMap (relation 8896976), unofficial.'
WHERE slug = 'legal';

UPDATE content_page SET title_en = 'Accessibility', body_en =
E'CivicCare Tunis targets WCAG 2.2 level AA: full keyboard navigation, visible focus, form labels, error announcements, verified contrasts and touch targets of at least 44 px.\n\nThe interactive map has an alternative: the report list offers the same information and the same filters. The location of a report can be entered without a map, by address search or coordinates.\n\nThis statement describes a design target manually verified on the main journeys; no certification has been issued. If you encounter an obstacle, report it via the contact form.'
WHERE slug = 'accessibility';

UPDATE content_page SET title_en = 'API and open data', body_en =
E'CivicCare Tunis exposes a read API compatible with the GET subset of the Open311 GeoReport v2 standard. It only publishes published reports, without personal data.\n\nEndpoints:\n- GET /api/georeport/v2/services.json (or .xml): type catalogue.\n- GET /api/georeport/v2/requests.json (or .xml): reports, with service_code, start_date, end_date, status filters; maximum range of 90 days (default: last 90 days).\n- GET /api/georeport/v2/requests/{reference}.json (or .xml): report details.\n\nApplication jurisdiction: tunis (demonstration identifier, not officially assigned). Statuses: open (open, in progress) and closed (resolved/work ordered, outside responsibility). Creating reports via the Open311 API (POST) is not implemented: this service does not claim full GeoReport v2 compliance.\n\nExample: /api/georeport/v2/requests.json?status=open&jurisdiction_id=tunis'
WHERE slug = 'api';
