# Architecture

## Vue d'ensemble

CivicCare Tunis est un **monolithe modulaire** : un seul exécutable Spring Boot 4 (Java 25),
interface Vaadin Flow 25 (Java côté serveur, un composant cartographique TypeScript/JS
encapsulé), PostgreSQL 18 + PostGIS 3.6, migrations Flyway.

```
Navigateur ── Vaadin Flow (vues Java, MapLibre encapsulé)
      │              │
      │        Services applicatifs (validations, autorisations, transactions)
REST (Open311, médias)                │
      │              ┌────────┬───────┴──────┬─────────────┐
      │         PostgreSQL/PostGIS      Outbox e-mail   MediaStorage (local)
      │              │                  (job + SMTP)        │
      └── mêmes validations/projections publiques que l'UI  │
                                        Mailpit (dev)   volume Docker
```

## Modules (packages)

| Module | Rôle |
|---|---|
| `reports` | Dossier de signalement : création, recherche publique, workflow, rétention |
| `catalog` | Familles, types, champs conditionnels bornés, règles de routage |
| `geo` | Périmètre PostGIS, port `Geocoder` (DEMO local / Nominatim), composant carte |
| `media` | Traitement d'images (signature, EXIF, réencodage), stockage, diffusion |
| `subscriptions` | Favoris d'appareil (cookie + hachage), abonnements e-mail, jetons d'action |
| `notifications` | Outbox transactionnelle, dispatcher SMTP avec reprise bornée, composeur i18n |
| `moderation` | Publication, textes publics, médias, catégories, doublons |
| `identity` | Comptes internes, rôles, équipes, Spring Security |
| `administration` | Back-office : file de travail scopée, tableau de bord, audit, seed démo |
| `content` | Pages éditoriales FR/AR, formulaire de contact |
| `open311` | API GeoReport v2 (GET JSON/XML) |
| `observability` | Façade OpenTelemetry (spans métier, métriques, jauges) |
| `shared` | Configuration, i18n, limiteur de débit, layout public |

Dans chaque module : les vues Vaadin appellent les **services applicatifs** ; aucun accès
JPA direct depuis les composants. UI et API partagent les mêmes validations, autorisations
et projections publiques.

## Choix structurants

- **Open Session in View désactivé** (`spring.jpa.open-in-view: false`) ; les besoins de
  chargement sont résolus dans les services (initialisation explicite dans la transaction).
- **Verrouillage optimiste** (`@Version`) sur `Report` : les conflits d'édition entre agents
  produisent une erreur récupérable affichée dans le back-office.
- **Transactions courtes** ; l'outbox est écrite dans la transaction métier, l'envoi SMTP
  se fait hors transaction métier, message par message, avec backoff exponentiel borné.
- **Séquence PostgreSQL** pour la référence humaine (`000123-2026`) — jamais `MAX(id)+1` ;
  l'idempotence du dépôt s'appuie sur une clé unique (`idempotency_key`).
- **Visibilité publique** : une seule règle (`publication_status='PUBLISHED'`, archives sur
  filtre explicite) appliquée par `ReportQueryService`, `PublicReportFacade`, l'API Open311
  et le contrôleur de médias.
- **Contrôle d'accès en profondeur** : annotations sur les routes Vaadin **et**
  `@PreAuthorize` + contrôle objet (équipe du dossier) dans les services. Masquer un bouton
  n'est pas un contrôle d'accès.
- **Requêtes spatiales** : `geometry(Point,4326)` + index GiST (géométrie et geography) ;
  distances métriques via `::geography` (jamais des degrés interprétés en mètres) ;
  emprise de carte plafonnée à 500 points, agrégation par clustering côté client.
- **Recherche textuelle** : PostgreSQL `pg_trgm` + `unaccent` (fonction immuable indexée) ;
  la normalisation s'applique aux expressions de recherche, jamais au texte stocké.
- **Champs conditionnels** : définitions bornées (TEXT/SELECT) en base, valeurs JSONB
  validées serveur (clés inconnues rejetées), aucun code exécutable stocké.

## Ce qui n'est pas utilisé (choix assumé)

Pas de microservices, Kafka, Redis ni Kubernetes. Une table outbox PostgreSQL suffit pour
la fiabilité des e-mails à cette échelle. Le limiteur de débit est en mémoire
(mono-instance) — limite documentée pour un déploiement multi-instance.
