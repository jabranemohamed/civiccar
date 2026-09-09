-- Catalogue initial : 15 familles / 40 types, libellés français et arabes.
-- Base fonctionnelle configurable pour Tunis ; ce n'est PAS le catalogue officiel de la
-- municipalité de Tunis. Codes internes stables, indépendants des traductions.

-- ===== Familles =====
INSERT INTO category_group (id, code, label_fr, label_ar, sort, active) VALUES
('a0000000-0000-0000-0000-000000000001', 'CLOTHING_CONTAINERS', 'Conteneurs à vêtements', 'حاويات الملابس المستعملة', 1, true),
('a0000000-0000-0000-0000-000000000002', 'TRAFFIC_LIGHTS', 'Feux de circulation', 'إشارات المرور الضوئية', 2, true),
('a0000000-0000-0000-0000-000000000003', 'BENCHES', 'Bancs', 'المقاعد العمومية', 3, true),
('a0000000-0000-0000-0000-000000000004', 'FOUNTAINS', 'Fontaines', 'النافورات', 4, true),
('a0000000-0000-0000-0000-000000000005', 'MONUMENTS', 'Monuments', 'المعالم والنصب', 5, true),
('a0000000-0000-0000-0000-000000000006', 'PATHS', 'Chemins piétons et cyclables', 'ممرات المشاة والدراجات', 6, true),
('a0000000-0000-0000-0000-000000000007', 'GREEN_STRIPS', 'Bandes végétalisées', 'الشرائط الخضراء', 7, true),
('a0000000-0000-0000-0000-000000000008', 'TRASH_BINS', 'Poubelles', 'سلال المهملات', 8, true),
('a0000000-0000-0000-0000-000000000009', 'PARKS', 'Parcs', 'المنتزهات', 9, true),
('a0000000-0000-0000-0000-000000000010', 'POLITICAL_POSTERS', 'Affiches politiques', 'المعلقات السياسية', 10, true),
('a0000000-0000-0000-0000-000000000011', 'PLAYGROUNDS', 'Aires de jeux', 'ساحات اللعب', 11, true),
('a0000000-0000-0000-0000-000000000012', 'ROADS', 'Voirie', 'الطرقات', 12, true),
('a0000000-0000-0000-0000-000000000013', 'STREET_LIGHTING', 'Éclairage public', 'الإنارة العمومية', 13, true),
('a0000000-0000-0000-0000-000000000014', 'TRAFFIC_SIGNS', 'Panneaux de signalisation', 'علامات المرور', 14, true),
('a0000000-0000-0000-0000-000000000015', 'RECYCLING_POINTS', 'Points de recyclage', 'نقاط الرسكلة', 15, true);

-- ===== Types (40) =====
INSERT INTO service_type (id, group_id, code, label_fr, label_ar, help_fr, help_ar, standard_description, active, sort) VALUES
-- Conteneurs à vêtements (4)
('aa000000-0000-0000-0000-000000000101', 'a0000000-0000-0000-0000-000000000001', 'CLOTHING_CONTAINER_DAMAGED', 'Conteneur endommagé', 'حاوية متضررة', NULL, NULL, true, true, 1),
('aa000000-0000-0000-0000-000000000102', 'a0000000-0000-0000-0000-000000000001', 'CLOTHING_CONTAINER_FULL', 'Conteneur plein', 'حاوية ممتلئة', NULL, NULL, true, true, 2),
('aa000000-0000-0000-0000-000000000103', 'a0000000-0000-0000-0000-000000000001', 'CLOTHING_CONTAINER_DIRTY', 'Conteneur sale', 'حاوية متسخة', NULL, NULL, true, true, 3),
('aa000000-0000-0000-0000-000000000104', 'a0000000-0000-0000-0000-000000000001', 'CLOTHING_CONTAINER_SURROUNDINGS_DIRTY', 'Abords sales', 'محيط الحاوية متسخ', NULL, NULL, true, true, 4),
-- Feux de circulation (3)
('aa000000-0000-0000-0000-000000000201', 'a0000000-0000-0000-0000-000000000002', 'TRAFFIC_LIGHT_DIRTY', 'Feu sale', 'إشارة ضوئية متسخة', NULL, NULL, true, true, 1),
('aa000000-0000-0000-0000-000000000202', 'a0000000-0000-0000-0000-000000000002', 'TRAFFIC_LIGHT_DAMAGED', 'Feu endommagé', 'إشارة ضوئية متضررة', NULL, NULL, true, true, 2),
('aa000000-0000-0000-0000-000000000203', 'a0000000-0000-0000-0000-000000000002', 'TRAFFIC_LIGHT_OUT_OF_ORDER', 'Feu en panne', 'إشارة ضوئية معطلة', NULL, NULL, true, true, 3),
-- Bancs (2)
('aa000000-0000-0000-0000-000000000301', 'a0000000-0000-0000-0000-000000000003', 'BENCH_DAMAGED', 'Banc endommagé', 'مقعد متضرر', NULL, NULL, true, true, 1),
('aa000000-0000-0000-0000-000000000302', 'a0000000-0000-0000-0000-000000000003', 'BENCH_DIRTY', 'Banc sale', 'مقعد متسخ', NULL, NULL, true, true, 2),
-- Fontaines (2)
('aa000000-0000-0000-0000-000000000401', 'a0000000-0000-0000-0000-000000000004', 'FOUNTAIN_DAMAGED', 'Fontaine endommagée', 'نافورة متضررة', NULL, NULL, true, true, 1),
('aa000000-0000-0000-0000-000000000402', 'a0000000-0000-0000-0000-000000000004', 'FOUNTAIN_DIRTY', 'Fontaine sale', 'نافورة متسخة', NULL, NULL, true, true, 2),
-- Monuments (2)
('aa000000-0000-0000-0000-000000000501', 'a0000000-0000-0000-0000-000000000005', 'MONUMENT_DAMAGED', 'Monument endommagé', 'نصب متضرر', NULL, NULL, true, true, 1),
('aa000000-0000-0000-0000-000000000502', 'a0000000-0000-0000-0000-000000000005', 'MONUMENT_DIRTY', 'Monument sale', 'نصب متسخ', NULL, NULL, true, true, 2),
-- Chemins piétons et cyclables (1)
('aa000000-0000-0000-0000-000000000601', 'a0000000-0000-0000-0000-000000000006', 'PATH_DIRTY', 'Chemin piéton ou cyclable sale', 'ممر مشاة أو دراجات متسخ', NULL, NULL, true, true, 1),
-- Bandes végétalisées (1)
('aa000000-0000-0000-0000-000000000701', 'a0000000-0000-0000-0000-000000000007', 'GREEN_STRIP_DIRTY', 'Bande végétalisée sale', 'شريط أخضر متسخ', NULL, NULL, true, true, 1),
-- Poubelles (1)
('aa000000-0000-0000-0000-000000000801', 'a0000000-0000-0000-0000-000000000008', 'TRASH_BIN_FULL', 'Poubelle pleine', 'سلة مهملات ممتلئة', NULL, NULL, true, true, 1),
-- Parcs (3)
('aa000000-0000-0000-0000-000000000901', 'a0000000-0000-0000-0000-000000000009', 'PARK_DOG_BAG_DISPENSER_EMPTY', 'Distributeur de sacs pour déjections canines vide', 'موزع أكياس فضلات الكلاب فارغ', NULL, NULL, true, true, 1),
('aa000000-0000-0000-0000-000000000902', 'a0000000-0000-0000-0000-000000000009', 'PARK_DAMAGED', 'Parc endommagé', 'منتزه متضرر', NULL, NULL, true, true, 2),
('aa000000-0000-0000-0000-000000000903', 'a0000000-0000-0000-0000-000000000009', 'PARK_DIRTY', 'Parc sale', 'منتزه متسخ', NULL, NULL, true, true, 3),
-- Affiches politiques (5) — description standard désactivée (variante observée), champ parti requis
('aa000000-0000-0000-0000-000000001001', 'a0000000-0000-0000-0000-000000000010', 'POSTER_FORBIDDEN_AREA', 'Affiche dans une zone interdite ou un arrêt de bus', 'معلقة في منطقة ممنوعة أو محطة حافلات',
 'Indiquez le parti concerné par l''affiche. Cette information désigne l''affiche et ne sert jamais à déduire vos opinions.',
 'حدد الحزب المعني بالمعلقة. تُستخدم هذه المعلومة لتحديد المعلقة فقط ولا تُستعمل أبداً لاستنتاج آرائكم.', false, true, 1),
('aa000000-0000-0000-0000-000000001002', 'a0000000-0000-0000-0000-000000000010', 'POSTER_DAMAGED', 'Affiche endommagée', 'معلقة متضررة',
 'Indiquez le parti concerné par l''affiche. Cette information désigne l''affiche et ne sert jamais à déduire vos opinions.',
 'حدد الحزب المعني بالمعلقة. تُستخدم هذه المعلومة لتحديد المعلقة فقط ولا تُستعمل أبداً لاستنتاج آرائكم.', false, true, 2),
('aa000000-0000-0000-0000-000000001003', 'a0000000-0000-0000-0000-000000000010', 'POSTER_OBSTRUCTING', 'Affiche gênant la visibilité ou bloquant la chaussée ou un chemin', 'معلقة تحجب الرؤية أو تسد الطريق أو الممر',
 'Indiquez le parti concerné par l''affiche. Cette information désigne l''affiche et ne sert jamais à déduire vos opinions.',
 'حدد الحزب المعني بالمعلقة. تُستخدم هذه المعلومة لتحديد المعلقة فقط ولا تُستعمل أبداً لاستنتاج آرائكم.', false, true, 3),
('aa000000-0000-0000-0000-000000001004', 'a0000000-0000-0000-0000-000000000010', 'POSTER_POORLY_FIXED', 'Affiche mal fixée ou mal installée', 'معلقة مثبتة بشكل سيئ',
 'Indiquez le parti concerné par l''affiche. Cette information désigne l''affiche et ne sert jamais à déduire vos opinions.',
 'حدد الحزب المعني بالمعلقة. تُستخدم هذه المعلومة لتحديد المعلقة فقط ولا تُستعمل أبداً لاستنتاج آرائكم.', false, true, 4),
('aa000000-0000-0000-0000-000000001005', 'a0000000-0000-0000-0000-000000000010', 'POSTER_NOT_REMOVED', 'Affiche non retirée', 'معلقة لم تتم إزالتها',
 'Indiquez le parti concerné par l''affiche. Cette information désigne l''affiche et ne sert jamais à déduire vos opinions.',
 'حدد الحزب المعني بالمعلقة. تُستخدم هذه المعلومة لتحديد المعلقة فقط ولا تُستعمل أبداً لاستنتاج آرائكم.', false, true, 5),
-- Aires de jeux (2)
('aa000000-0000-0000-0000-000000001101', 'a0000000-0000-0000-0000-000000000011', 'PLAYGROUND_DAMAGED', 'Aire de jeux endommagée', 'ساحة لعب متضررة', NULL, NULL, true, true, 1),
('aa000000-0000-0000-0000-000000001102', 'a0000000-0000-0000-0000-000000000011', 'PLAYGROUND_DIRTY', 'Aire de jeux sale', 'ساحة لعب متسخة', NULL, NULL, true, true, 2),
-- Voirie (4)
('aa000000-0000-0000-0000-000000001201', 'a0000000-0000-0000-0000-000000000012', 'ROAD_DIRTY', 'Rue sale', 'شارع متسخ', NULL, NULL, true, true, 1),
('aa000000-0000-0000-0000-000000001202', 'a0000000-0000-0000-0000-000000000012', 'ROAD_NOISY_MANHOLE', 'Bouche d''évacuation ou plaque d''égout bruyante', 'غطاء بالوعة أو فتحة تصريف مزعجة', NULL, NULL, true, true, 2),
('aa000000-0000-0000-0000-000000001203', 'a0000000-0000-0000-0000-000000000012', 'ROAD_BLOCKED_DRAIN', 'Évacuation bouchée', 'بالوعة مسدودة', NULL, NULL, true, true, 3),
('aa000000-0000-0000-0000-000000001204', 'a0000000-0000-0000-0000-000000000012', 'ROAD_ABANDONED_VEHICLE', 'Véhicule hors d''usage', 'سيارة مهملة',
 'Si elle est visible, indiquez la plaque d''immatriculation dans le champ dédié. Elle reste privée et n''est jamais publiée.',
 'إذا كانت لوحة التسجيل مرئية، أدخلها في الخانة المخصصة. تبقى هذه المعلومة خاصة ولا تُنشر أبداً.', true, true, 4),
-- Éclairage public (4)
('aa000000-0000-0000-0000-000000001301', 'a0000000-0000-0000-0000-000000000013', 'STREET_LIGHT_LAMP_OUT', 'Luminaire en panne', 'مصباح معطل', NULL, NULL, true, true, 1),
('aa000000-0000-0000-0000-000000001302', 'a0000000-0000-0000-0000-000000000013', 'STREET_LIGHT_STREET_OUT', 'Éclairage de toute une rue en panne', 'انقطاع إنارة شارع بأكمله', NULL, NULL, true, true, 2),
('aa000000-0000-0000-0000-000000001303', 'a0000000-0000-0000-0000-000000000013', 'STREET_LIGHT_DAMAGED', 'Luminaire endommagé', 'مصباح متضرر', NULL, NULL, true, true, 3),
('aa000000-0000-0000-0000-000000001304', 'a0000000-0000-0000-0000-000000000013', 'STREET_LIGHT_DIRTY', 'Luminaire sale', 'مصباح متسخ', NULL, NULL, true, true, 4),
-- Panneaux de signalisation (2)
('aa000000-0000-0000-0000-000000001401', 'a0000000-0000-0000-0000-000000000014', 'TRAFFIC_SIGN_DAMAGED', 'Panneau endommagé', 'علامة مرور متضررة', NULL, NULL, true, true, 1),
('aa000000-0000-0000-0000-000000001402', 'a0000000-0000-0000-0000-000000000014', 'TRAFFIC_SIGN_DIRTY', 'Panneau sale', 'علامة مرور متسخة', NULL, NULL, true, true, 2),
-- Points de recyclage (4)
('aa000000-0000-0000-0000-000000001501', 'a0000000-0000-0000-0000-000000000015', 'RECYCLING_SURROUNDINGS_DIRTY', 'Abords sales', 'محيط نقطة الرسكلة متسخ', NULL, NULL, true, true, 1),
('aa000000-0000-0000-0000-000000001502', 'a0000000-0000-0000-0000-000000000015', 'RECYCLING_CONTAINER_DAMAGED', 'Conteneur endommagé', 'حاوية رسكلة متضررة', NULL, NULL, true, true, 2),
('aa000000-0000-0000-0000-000000001503', 'a0000000-0000-0000-0000-000000000015', 'RECYCLING_CONTAINER_FULL', 'Conteneur plein', 'حاوية رسكلة ممتلئة', NULL, NULL, true, true, 3),
('aa000000-0000-0000-0000-000000001504', 'a0000000-0000-0000-0000-000000000015', 'RECYCLING_CONTAINER_DIRTY', 'Conteneur sale', 'حاوية رسكلة متسخة', NULL, NULL, true, true, 4);

-- ===== Champs conditionnels =====
-- Parti politique : requis pour les 5 types d'affiches, catalogue de partis FICTIFS.
INSERT INTO field_definition (id, code, label_fr, label_ar, kind, required, is_public, max_len, sort) VALUES
('f0000000-0000-0000-0000-000000000001', 'POLITICAL_PARTY', 'Parti concerné par l''affiche', 'الحزب المعني بالمعلقة', 'SELECT', true, true, 64, 1),
('f0000000-0000-0000-0000-000000000002', 'VEHICLE_PLATE', 'Plaque d''immatriculation (facultative, non publiée)', 'لوحة التسجيل (اختيارية، لا تُنشر)', 'TEXT', false, false, 32, 1);

INSERT INTO field_option (id, field_definition_id, code, label_fr, label_ar, active, sort) VALUES
('f1000000-0000-0000-0000-000000000001', 'f0000000-0000-0000-0000-000000000001', 'PARTY_ALPHA', 'Parti de démonstration Alpha (fictif)', 'حزب تجريبي ألفا (وهمي)', true, 1),
('f1000000-0000-0000-0000-000000000002', 'f0000000-0000-0000-0000-000000000001', 'PARTY_BETA', 'Parti de démonstration Bêta (fictif)', 'حزب تجريبي بيتا (وهمي)', true, 2),
('f1000000-0000-0000-0000-000000000003', 'f0000000-0000-0000-0000-000000000001', 'PARTY_GAMMA', 'Parti de démonstration Gamma (fictif)', 'حزب تجريبي غاما (وهمي)', true, 3),
('f1000000-0000-0000-0000-000000000004', 'f0000000-0000-0000-0000-000000000001', 'PARTY_OTHER', 'Autre / inconnu', 'آخر / غير معروف', true, 4);

INSERT INTO service_type_field (service_type_id, field_definition_id)
SELECT st.id, 'f0000000-0000-0000-0000-000000000001'
FROM service_type st
WHERE st.code IN ('POSTER_FORBIDDEN_AREA', 'POSTER_DAMAGED', 'POSTER_OBSTRUCTING', 'POSTER_POORLY_FIXED', 'POSTER_NOT_REMOVED');

INSERT INTO service_type_field (service_type_id, field_definition_id)
SELECT st.id, 'f0000000-0000-0000-0000-000000000002'
FROM service_type st
WHERE st.code = 'ROAD_ABANDONED_VEHICLE';

-- ===== Routage de démonstration =====
-- Propreté urbaine : saleté, conteneurs, poubelles, recyclage
INSERT INTO routing_rule (id, service_type_id, department_id, active)
SELECT gen_random_uuid(), st.id, 'c1000000-0000-0000-0000-000000000001', true
FROM service_type st
WHERE st.code IN ('CLOTHING_CONTAINER_DAMAGED', 'CLOTHING_CONTAINER_FULL', 'CLOTHING_CONTAINER_DIRTY',
                  'CLOTHING_CONTAINER_SURROUNDINGS_DIRTY', 'PATH_DIRTY', 'GREEN_STRIP_DIRTY', 'TRASH_BIN_FULL',
                  'ROAD_DIRTY', 'RECYCLING_SURROUNDINGS_DIRTY', 'RECYCLING_CONTAINER_DAMAGED',
                  'RECYCLING_CONTAINER_FULL', 'RECYCLING_CONTAINER_DIRTY');

-- Voirie : chaussée, évacuations, véhicules, panneaux
INSERT INTO routing_rule (id, service_type_id, department_id, active)
SELECT gen_random_uuid(), st.id, 'c1000000-0000-0000-0000-000000000002', true
FROM service_type st
WHERE st.code IN ('ROAD_NOISY_MANHOLE', 'ROAD_BLOCKED_DRAIN', 'ROAD_ABANDONED_VEHICLE',
                  'TRAFFIC_SIGN_DAMAGED', 'TRAFFIC_SIGN_DIRTY');

-- Éclairage public : luminaires et feux
INSERT INTO routing_rule (id, service_type_id, department_id, active)
SELECT gen_random_uuid(), st.id, 'c1000000-0000-0000-0000-000000000003', true
FROM service_type st
WHERE st.code IN ('STREET_LIGHT_LAMP_OUT', 'STREET_LIGHT_STREET_OUT', 'STREET_LIGHT_DAMAGED', 'STREET_LIGHT_DIRTY',
                  'TRAFFIC_LIGHT_DIRTY', 'TRAFFIC_LIGHT_DAMAGED', 'TRAFFIC_LIGHT_OUT_OF_ORDER');

-- Espaces publics : parcs, aires de jeux, bancs, fontaines, monuments, affiches
INSERT INTO routing_rule (id, service_type_id, department_id, active)
SELECT gen_random_uuid(), st.id, 'c1000000-0000-0000-0000-000000000004', true
FROM service_type st
WHERE st.code IN ('PARK_DOG_BAG_DISPENSER_EMPTY', 'PARK_DAMAGED', 'PARK_DIRTY',
                  'PLAYGROUND_DAMAGED', 'PLAYGROUND_DIRTY', 'BENCH_DAMAGED', 'BENCH_DIRTY',
                  'FOUNTAIN_DAMAGED', 'FOUNTAIN_DIRTY', 'MONUMENT_DAMAGED', 'MONUMENT_DIRTY',
                  'POSTER_FORBIDDEN_AREA', 'POSTER_DAMAGED', 'POSTER_OBSTRUCTING',
                  'POSTER_POORLY_FIXED', 'POSTER_NOT_REMOVED');
