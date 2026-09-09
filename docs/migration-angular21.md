# Migration frontend : Vaadin Flow 25 → Angular 21

Dépôt : copie de `CleanCity` (baseline commit `db2f80e`, branche `migration/angular21`).
Le backend (Java 25, Spring Boot 4.1.1, PostgreSQL 18 + PostGIS 3.6, Flyway V1–V10,
médias, jobs, Open311, OpenTelemetry) est **préservé à l'identique**.

## État de référence (exécuté avant toute modification)

- `./mvnw verify` sur la copie : **BUILD SUCCESS — 49 tests d'intégration + 9 unitaires,
  0 échec** (Testcontainers PostGIS réel).
- Base de démonstration existante (65+ dossiers) non touchée ; l'exécution applicative de
  la migration se fait sur une **copie dédiée** de la base (`civiccare_ng`, créée par
  `CREATE DATABASE … TEMPLATE civiccare`), jamais sur la base d'origine.
- Captures de référence Vaadin : `docs/screenshots/*.png` (23 captures FR/AR/EN,
  3 largeurs) + `marketing/` (vidéos des parcours).

## Audit du frontend Vaadin à remplacer

### Routes/vues Vaadin (annotations `@Route`, layouts `PublicLayout`/`AdminLayout`)

| Route | Vue | Accès |
|---|---|---|
| `/` | ExploreView (carte MapLibre + liste, filtres, recherche, pagination) | anonyme |
| `/report` | ReportWizardView (4 étapes, photos, doublons, idempotence) | anonyme |
| `/requests/{ref}` | RequestDetailView (fiche publique, suivre, abonnement) | anonyme |
| `/following` | FollowingView (favoris appareil via cookie `cc_device`) | anonyme |
| `/info[/slug]` | InfoView (7 pages de contenu FR/AR/EN) | anonyme |
| `/contact` | ContactView (formulaire + honeypot) | anonyme |
| `/s/confirm/{t}`, `/s/unsubscribe/{t}` | confirmation d'action e-mail (GET → bouton → action) | anonyme |
| `/login` | LoginView (Spring Security formLogin) | anonyme |
| `/admin` | AdminDashboardView | AGENT/MODERATOR/ADMIN |
| `/admin/reports`, `/admin/report/{id}` | file de travail + dossier interne | AGENT+ (scope équipe au service) |
| `/admin/moderation` | file de modération | MODERATOR/ADMIN |
| `/admin/catalog` | activation types + routage | ADMIN |
| `/admin/departments` | équipes (lecture) | ADMIN |
| `/admin/users` | gestion utilisateurs | ADMIN |
| `/admin/content` | édition pages FR/AR/EN | ADMIN |
| `/admin/contact` | boîte de contact | AGENT+ |
| `/admin/settings` | paramètres effectifs + audit | ADMIN |

### Services Java appelés par les vues (tous conservés)

`ReportService`, `ReportQueryService`, `PublicReportFacade`, `WorkflowService`,
`ModerationService`, `CatalogService`, `SubscriptionService`, `MediaService`,
`ContentService`, `AdminFacade`, `StaffUserService`, `BoundaryService`, `Geocoder`,
`ReportAdminQueries`, `AuditService`, `RateLimiter`, `Telemetry`.

**Règles trouvées dans l'UI Vaadin à re-vérifier côté serveur** : aucune règle métier
n'était uniquement dans l'UI (validations dupliquées côté serveur dès l'origine, testées
par `ReportCreationIT` etc.). Les seules logiques purement UI : progression de
l'assistant, compteur de description, prévisualisation photo — re-implémentées en Angular
sans autorité.

### API HTTP existantes

- Open311 GET `/api/georeport/v2/*` (JSON/XML) — inchangée.
- `/api/v1/reports/map` (bbox), `/api/v1/reports/{ref}` (détail public) — conservées et
  étendues.
- `/media/{key}` (dérivés approuvés ; staff voit PENDING) — inchangée.
- `/actuator/health|prometheus` — inchangés.

### Points spécifiques Vaadin identifiés (à retirer en fin de migration)

- Dépendances `com.vaadin:*` (starter, core, dev), plugin `vaadin-maven-plugin`,
  thème `src/main/frontend/themes/civiccare`, wrapper carte `civiccare-map.js`,
  `VaadinSecurityConfigurer`, annotations `@Route`/`@AnonymousAllowed`/`@RolesAllowed`
  sur les vues, `I18NProvider` Vaadin (la logique de bundles est réutilisée côté REST),
  cookie d'appareil posé via `VaadinRequest/Response` (`DeviceCookie`).
- Push/WebSocket : **aucun usage applicatif de Vaadin Push** (uniquement l'UIDL standard) →
  aucun besoin temps réel à remplacer ; le rafraîchissement des données se fait par requête.
- Sessions : sessions HTTP Spring Security standard (cookie JSESSIONID HttpOnly) —
  conservées pour Angular. Les agents devront se reconnecter à la bascule (sessions UI
  Vaadin non transférables) — documenté ci-dessous.

### Authentification / cookies / jetons

- formLogin Spring Security (`/login`), rôles `AGENT|MODERATOR|ADMIN`, contrôle d'accès
  objet (équipe) dans les services (`@PreAuthorize` + vérifs explicites) — **conservés**.
- Favoris anonymes : cookie `cc_device` (jeton haché en base) — conservé, pose du cookie
  déplacée vers un endpoint REST (même nom/chemin → favoris existants compatibles).
- Liens e-mail `/s/confirm/{token}`, `/s/unsubscribe/{token}` : chemins **préservés**
  (routes Angular identiques ; l'action reste un POST déclenché par clic).

## Décisions de migration

| Sujet | Décision |
|---|---|
| Angular | **21.2.22** (dernier correctif 21.x ; Angular 22 exclu volontairement), CLI 21.2.23 |
| UI | **@angular/material 21.2.14 + @angular/cdk 21.2.14**, thème M3 Sass centralisé (couleurs CivicCare : #155E75 / #B45336 / fond #FAF8F4) |
| Node / TS / RxJS | Node 24.20.0 (matrice ^20.19 ∥ ^22.12 ∥ ^24) ; TypeScript 5.9.x ; RxJS 7.8.x ; `npm ci` + lockfile, aucun `--force`/`--legacy-peer-deps` |
| Détection de changements | **Zoneless** (`provideZonelessChangeDetection`), signals pour l'état, RxJS pour HTTP/debounce/switchMap |
| i18n | Service de traduction **à l'exécution maison** (JSON par langue générés depuis les bundles Java existants, signal `locale`, pipe `t`, `lang`/`dir` dynamiques, `Directionality` CDK) — le besoin de bascule FR/AR/EN à chaud exclut l'i18n compilé (3 builds) ; aucune dépendance externe à risquer |
| Carte | MapLibre GL JS 6.7.0 conservé, wrapper **composant Angular** (destruction propre, resize, RTL non inversé) ; fournisseurs tuiles/géocodage inchangés |
| Auth SPA | Sessions Spring conservées, même origine, chemins relatifs `/api`. formLogin adapté : handlers JSON (200/401) pour les requêtes XHR ; logout POST 204 ; `GET /api/v1/auth/me` |
| CSRF | `CookieCsrfTokenRepository.withHttpOnlyFalse()` + gestion SPA (token différé/BREACH per doc Spring Security 7) + `CsrfCookieFilter` (cookie émis dès le premier GET, y compris dépôt anonyme) ; Angular `withXsrfConfiguration` (XSRF-TOKEN / X-XSRF-TOKEN) |
| Erreurs API | RFC 9457 Problem Details (`application/problem+json`) : `type` stable, `errors` par champ, 400/401/403/404/409/422 ; jamais de page HTML pour `/api/**` |
| Livraison | Build Angular prod copié dans les ressources statiques du jar (profil Maven `production` : `npm ci && ng build` puis copie) ; SPA fallback **liste blanche** de routes UI ; `/api/**`, `/media/**`, `/actuator/**`, assets → jamais index.html |
| OTel frontend | `@opentelemetry/sdk-trace-web` + instrumentation fetch (une seule capture), propagation `traceparent` **uniquement** vers `/api` même origine ; ingestion via proxy même origine `/api/telemetry/traces` (borné, traces seulement, désactivable) ; pannes télémétrie sans impact métier |
| Vaadin | Retiré à l'étape finale, après preuve de parité (le dépôt d'origine `CleanCity` reste la version Vaadin de rollback) |

## Endpoints REST ajoutés (contrat documenté dans docs/api.md)

Public (`/api/v1`) : `GET /config`, `GET /catalog`, `GET /content/{slug}`,
`GET /reports` (recherche paginée), `GET /reports/map` (étendu filtres),
`GET /reports/{ref}` (étendu : frise, médias, champs publics),
`GET /reports/duplicates`, `POST /reports` (multipart, idempotence, CSRF),
`GET /geocode`, `GET /geocode/reverse`, `GET|POST /bookmarks`,
`POST /subscriptions`, `POST /subscriptions/confirm/{t}`, `POST /subscriptions/unsubscribe/{t}`,
`POST /contact`, `GET /auth/me`, `POST /login` (form), `POST /logout`,
`POST /api/telemetry/traces` (proxy OTLP borné, optionnel).

Admin (`/api/v1/admin`, rôles + scope objet) : `GET /dashboard`, `GET /reports`,
`GET /reports/{id}`, `POST /reports/{id}/assign|status|public-update|note|publication|`
`category|duplicate|media/{mediaId}`, `GET /reports/export.csv`, `GET|PATCH /catalog/*`,
`GET /departments`, `GET|POST|PATCH /users`, `GET|PUT /content/{slug}`,
`GET /contact-messages`, `POST /contact-messages/{id}/processed`, `GET /settings`, `GET /audit`.

## Matrice de parité

État : ✅ migré+vérifié · 🔄 en cours · ⬜ à faire.

| Fonction/route existante | Comportement actuel | Service & autorisation | API | Écran Angular | Test/preuve | État |
|---|---|---|---|---|---|---|
| `/` carte+liste | filtres, recherche FR/AR/EN, pagination, clusters, périmètre | ReportQueryService (public) | GET /reports, /reports/map, /config, /catalog | features/public-map | E2E + captures | ⬜ |
| `/report` assistant | 4 étapes, photos≤3, champs conditionnels, doublons 50 m, idempotence | ReportService (public+CSRF+rate limit) | POST /reports, GET /duplicates, /geocode | features/report-form (MatStepper) | E2E dépôt + IT API | ⬜ |
| `/requests/{ref}` | fiche publiée uniquement, frise, photos, suivre, abonnement | PublicReportFacade, SubscriptionService | GET /reports/{ref}, POST /bookmarks, /subscriptions | features/reports/detail | E2E + IT non-divulgation | ⬜ |
| `/following` | favoris cookie `cc_device`, publiés seulement | SubscriptionService | GET /bookmarks | features/following | E2E cookie conservé | ⬜ |
| `/s/confirm`, `/s/unsubscribe` | GET → confirmation → action au clic, jetons usage unique | SubscriptionService | POST /subscriptions/confirm\|unsubscribe/{t} | features/subscriptions | IT jetons (existants) + E2E | ⬜ |
| `/info/*`, `/contact` | contenus FR/AR/EN, contact + honeypot + copie | ContentService | GET /content/{slug}, POST /contact | features/information, contact | IT ContactIT + E2E | ⬜ |
| `/login` | formLogin sessions, erreurs génériques | Spring Security | POST /login (JSON handlers), GET /auth/me | features/auth | IT MockMvc + E2E | ⬜ |
| `/admin` dashboard | stats réelles | AdminFacade (AGENT+) | GET /admin/dashboard | features/admin/dashboard | IT + E2E | ⬜ |
| `/admin/reports` + détail | file scopée équipe, affectation, transitions+motif, verrou optimiste | AdminFacade, WorkflowService | GET/POST /admin/reports/* | features/admin/reports (MatTable serveur) | IT permissions + E2E | ⬜ |
| `/admin/moderation` | publier/masquer, texte public, médias, catégorie, doublon | ModerationService (MODERATOR+) | POST /admin/reports/{id}/… | features/admin/moderation | IT + E2E | ⬜ |
| `/admin/catalog` | activation type, routage | CatalogService (ADMIN) | PATCH /admin/catalog/* | features/admin/catalog | IT | ⬜ |
| `/admin/users` | création, rôles, équipes, activation | StaffUserService (ADMIN) | /admin/users | features/admin/users | IT | ⬜ |
| `/admin/content` | édition FR/AR/EN | ContentService (ADMIN) | PUT /admin/content/{slug} | features/admin/content | IT | ⬜ |
| `/admin/contact` | boîte interne, marquer traité | ContentService (AGENT+) | /admin/contact-messages | features/admin/contact | IT | ⬜ |
| `/admin/settings` | paramètres effectifs + audit + périmètre démo | AppProperties/Boundary/Audit (ADMIN) | GET /admin/settings, /admin/audit | features/admin/settings | E2E | ⬜ |
| Export CSV | file filtrée, anti-injection formule | AdminFacade | GET /admin/reports/export.csv | bouton admin | IT en-têtes/contenu | ⬜ |
| Open311 + jobs | inchangés | — | existants | — | IT existants (49) | ✅ |

## Bascule et retour arrière

- Bascule : déployer ce dépôt (`CleanCityAngular`) — build unique Spring Boot servant la
  SPA. Les **sessions Vaadin ne sont pas conservées** : les agents se reconnectent une
  fois. Les données, cookies `cc_device`, jetons e-mail et références publiques restent
  valides (chemins préservés).
- Retour arrière : redéployer le dépôt `CleanCity` (version Vaadin) sur le **même schéma**
  (aucune migration destructive ajoutée ; toute migration nouvelle est additive). Les
  données créées sous Angular restent lisibles par la version Vaadin.

## Journal des vérifications

(Complété au fil de la migration — voir aussi le rapport final.)

- 2026-09-09 : état de référence exécuté (verify 58 tests OK) avant toute modification.
