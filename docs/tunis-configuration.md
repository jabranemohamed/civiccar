# Configuration Tunis

Ce document décrit la configuration géographique, linguistique et opérationnelle de
CivicCare Tunis, ainsi que ce qui doit être complété avant tout usage public.

## Ville cible et périmètre administratif

- Ville cible : **municipalité de Tunis**, Tunisie, code pays `TN`.
- La municipalité de Tunis est distincte du **gouvernorat de Tunis** (qui inclut Carthage,
  La Marsa, Sidi Bou Saïd, Le Bardo…) et du **Grand Tunis** (qui ajoute l'Ariana, Ben Arous,
  La Manouba). Aucune commune voisine n'est incluse automatiquement.

### Constat vérifié sur OpenStreetMap (2026-09-08)

Recherches effectuées via Nominatim (requêtes ponctuelles identifiées, conformes à la
politique du serveur public : pas d'autocomplétion) et Overpass :

| Relation OSM | Contenu | Niveau |
|---|---|---|
| 192757 | Tunisie (pays) | admin_level 2 |
| 1435835 | Gouvernorat de Tunis (ولاية تونس) | admin_level 4 |
| 8896976 | « Tunis » (place=city, Wikidata Q3572) | *pas de admin_level* |

**OSM ne contient aucune relation `boundary=administrative` de niveau municipal pour
Tunis.** Le polygone de la relation 8896976 a été testé par points de référence : il
contient Carthage, Sidi Bou Saïd, La Marsa et Le Bardo (communes distinctes) et exclut
l'Ariana et Ben Arous. C'est donc une emprise d'**agglomération**, pas la limite
municipale — il est stocké sous le code `OSM_TUNIS_AGGLO` avec ce libellé explicite,
attribué « © contributeurs OpenStreetMap, ODbL » et jamais présenté comme officiel.

### Périmètre effectivement utilisé

Faute de contour municipal fiable et réutilisable, le périmètre **actif par défaut** est
`DEMO_TUNIS` : un polygone de démonstration dessiné manuellement (13 sommets, licence CC0),
explicitement non officiel et remplaçable, approximant la commune de Tunis :

- exclut **Le Bardo** par une encoche à l'ouest (vérifié : musée du Bardo hors périmètre) ;
- exclut **Carthage / La Goulette / La Marsa** à l'est ;
- exclut **l'Ariana** au nord et **Ben Arous** au sud (vérifié par points de référence) ;
- inclut la médina, Bab Souika, El Menzah, El Omrane, Montfleury, El Kabaria,
  Sidi Hassine et les Berges du Lac.

Règle de bord : un point exactement **sur la limite est considéré à l'intérieur**
(`ST_Covers`, testé dans `ReportCreationIT`).

**Blocage production** : le profil `production` refuse de démarrer tant que
`GEO_BOUNDARY_VALIDATED=true` n'est pas positionné (après validation humaine d'un contour
vérifié, source et licence documentées ici). Variables :

| Variable | Valeurs | Défaut |
|---|---|---|
| `GEO_BOUNDARY_SOURCE` | `DEMO`, `OSM_AGGLO`, ou code d'un contour chargé par l'exploitant | `DEMO` |
| `GEO_BOUNDARY_VALIDATED` | `true`/`false` | `false` |

## Fournisseurs géographiques

- **Moteur cartographique** : MapLibre GL JS (encapsulé dans un composant Angular). MapLibre
  n'est ni un fournisseur de tuiles ni un fournisseur d'adresses.
- **Tuiles** : `MAP_TILE_URL` (+ `MAP_TILE_ATTRIBUTION`). Défaut de démonstration :
  tuiles raster OpenStreetMap avec attribution ODbL. Avant tout usage public, vérifier la
  politique d'usage des tuiles OSMF (https://operations.osmfoundation.org/policies/tiles/)
  ou configurer un fournisseur commercial autorisé. Un échec de tuiles n'empêche ni la
  liste ni la saisie manuelle de coordonnées.
- **Géocodage** : `GEOCODER_MODE`
  - `DEMO` (défaut) : adaptateur local déterministe, ~14 lieux publics de la ville de Tunis
    avec noms français et arabes, coordonnées vérifiées dans le périmètre. Fonctionne sans
    réseau ; les résultats sont étiquetés « démonstration » dans l'interface et ne prouvent
    pas la qualité d'un fournisseur externe.
  - `NOMINATIM` : exige `GEOCODER_BASE_URL` (instance auto-hébergée ou fournisseur dont les
    conditions ont été vérifiées). **Le serveur public nominatim.openstreetmap.org n'est
    jamais configuré implicitement** : sa politique interdit l'autocomplétion et impose
    1 req/s, identification et attribution. L'adaptateur applique quoi qu'il en soit un
    verrou global de 1 req/s, un cache local, un User-Agent identifiant et des timeouts.
    L'autocomplétion n'est activée pour aucun fournisseur (recherche explicite uniquement).
  - La restriction `countrycodes=tn` ne remplace pas la validation serveur du polygone
    municipal : un résultat tunisien hors périmètre est refusé au dépôt.

### Vérifications de géocodage réellement exécutées

- Adaptateur DEMO : testé hors ligne (recherche FR « Bourguiba », AR « المدينة », géocodage
  inverse d'un point proche de l'avenue Habib Bourguiba) — voir tests et parcours navigateur.
- Fournisseur Nominatim réel : **vérification externe non exécutée** (aucune instance
  configurée dans cet environnement). À exécuter lors de la configuration d'un fournisseur :
  une recherche française, une recherche arabe et un géocodage inverse sur des lieux publics
  de Tunis, résultats à consigner ici.

## Langues, fuseau, téléphone

- Langues : **français (défaut), arabe (RTL complet) et anglais**. Interface arabe en RTL
  (`UI.setDirection`, propriétés CSS logiques, carte géographique jamais inversée).
  Toutes les chaînes sont externalisées (`i18n/messages*.properties`), catalogue, aides,
  validations, e-mails et back-office inclus. Les libellés anglais du catalogue, des
  équipes et des pages de contenu sont en base (migration V10) avec **repli automatique
  sur le français** lorsqu'une traduction anglaise manque ; l'édition EN est disponible
  dans le back-office (Contenus). La locale initiale suit l'en-tête `Accept-Language`
  du navigateur (fr/ar/en), sinon français.

## Pays et ville configurables (Tunisie / Tunis par défaut)

| Variable | Rôle | Défaut |
|---|---|---|
| `APP_NAME` | Nom public (en-tête, connexion) | `CivicCare Tunis` |
| `APP_COUNTRY` | Pays ISO 3166-1 alpha-2 : filtre `countrycodes` du géocodeur Nominatim et région téléphonique par défaut | `TN` |
| `APP_PHONE_REGION` | Région téléphonique si différente du pays | suit `APP_COUNTRY` |
| `APP_TIMEZONE` | Fuseau IANA | `Africa/Tunis` |
| `MAP_CENTER_LAT` / `MAP_CENTER_LON` / `MAP_INITIAL_ZOOM` | Cadrage initial de la carte | Tunis (36.8065 / 10.1815 / 12.5) |
| `GEO_BOUNDARY_SOURCE` | Périmètre actif (voir ci-dessus) | `DEMO` (Tunis) |

Changer de ville reste un acte d'exploitation complet : outre ces variables, il faut un
périmètre vérifié (`GEO_BOUNDARY_SOURCE` + `GEO_BOUNDARY_VALIDATED`), un jeu de lieux pour
le géocodeur (l'adaptateur DEMO est spécifique à Tunis), et l'adaptation des textes i18n
qui citent la ville. Le filtre pays du géocodeur ne remplace jamais la validation du
polygone côté serveur.
- Fuseau : **`Africa/Tunis`** via la base IANA (`ZoneId.of`), aucun décalage UTC codé en
  dur ; stockage en UTC (`timestamptz`). Les bornes des raccourcis « aujourd'hui / cette
  semaine / ce mois » sont calculées dans ce fuseau.
- Téléphone : normalisation libphonenumber avec région par défaut `TN` ; un numéro
  international valide (préfixe `+`) reste accepté ; champ facultatif.

## Équipes de démonstration

Cinq équipes **fictives**, marquées `demo=true` et signalées comme telles dans le
back-office : Propreté urbaine, Voirie, Éclairage public, Espaces publics, File de triage.
Toute correspondance avec une administration, un arrondissement ou un opérateur réel doit
être vérifiée avant activation. **Aucun dossier n'est transmis automatiquement à une
autorité réelle.**

## Bandeau urgences

`EMERGENCY_VERIFIED=false` par défaut : l'interface affiche une **consigne générique sans
numéro**. Les numéros d'urgence tunisiens ne doivent être renseignés qu'après vérification
sur une source tunisienne officielle, en consignant ici la source et la date
(`EMERGENCY_SOURCE`, `EMERGENCY_VERIFIED_ON`). Cette vérification officielle n'a **pas**
été exécutée pendant la construction : aucun numéro n'est livré, et les numéros allemands
du site de référence ne sont jamais utilisés.

## Données opérateur à compléter avant usage public

- Identité et coordonnées de l'exploitant (pages « Mentions légales », « Conditions »).
- Hébergeur.
- Vérification des exigences juridiques tunisiennes (notamment la loi n° 2004-63 sur la
  protection des données personnelles) : les textes livrés sont des textes de démonstration,
  aucune conformité n'est certifiée et le RGPD n'est pas présenté comme automatiquement
  applicable.
- Contour municipal vérifié + `GEO_BOUNDARY_VALIDATED=true`.
- Fournisseurs de tuiles et de géocodage autorisés pour la production.
- Numéros d'urgence vérifiés (ou maintien de la consigne générique).

## Ce que cette application n'est pas

CivicCare Tunis est une **application de démonstration**. Elle n'a aucune affiliation avec
la municipalité de Tunis, n'utilise aucun logo officiel et l'API Open311 (juridiction
applicative `tunis`, identifiant non attribué officiellement) ne constitue pas une
intégration aux systèmes municipaux.
