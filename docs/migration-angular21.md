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
| `/` carte+liste | filtres, recherche FR/AR/EN, pagination, clusters, périmètre | ReportQueryService (public) | GET /reports, /reports/map, /config, /catalog | features/explore | E2E manuel navigateur (dev + jar prod) : liste 46 publiés, carte Tunis + clusters, bascule FR/AR/EN + RTL | ✅ |
| `/report` assistant | 4 étapes, photos≤3, champs conditionnels, doublons 50 m, idempotence | ReportService (public+CSRF+rate limit) | POST /reports, GET /duplicates, /geocode | features/report-form (MatStepper) | E2E manuel : dépôt réel 000110-2026 avec photo (multipart+CSRF, géocodage inverse, contrôle périmètre) ; IT SpaApiIT idempotence + Problem Details | ✅ |
| `/requests/{ref}` | fiche publiée uniquement, frise, photos, suivre, abonnement | PublicReportFacade, SubscriptionService | GET /reports/{ref}, POST /bookmarks, /subscriptions | features/report-detail | E2E manuel : fiche 000110-2026 résolue (photo, frise, message agent) ; IT non-divulgation (SpaApiIT) | ✅ |
| `/following` | favoris cookie `cc_device`, publiés seulement | SubscriptionService | GET /bookmarks | features/following | E2E manuel : favori Vaadin du 06/09 (000063-2026) toujours présent + nouveau favori — cookie préservé ; IT bookmarks (SpaApiIT) | ✅ |
| `/s/confirm`, `/s/unsubscribe` | GET → confirmation → action au clic, jetons usage unique | SubscriptionService | POST /subscriptions/confirm\|unsubscribe/{t} | features/subscriptions | E2E manuel : abonnement -> e-mail Mailpit -> lien /s/confirm/{t} -> activation confirmée ; IT jetons (SubscriptionIT) | ✅ |
| `/info/*`, `/contact` | contenus FR/AR/EN, contact + honeypot + copie | ContentService | GET /content/{slug}, POST /contact | features/information, contact | IT ContactIT ; pages rendues en navigation (E2E) | ✅ |
| `/login` | formLogin sessions, erreurs génériques | Spring Security | POST /login (JSON handlers), GET /auth/me | features/auth | E2E manuel : login moderator + agent.proprete au serveur réel, redirection /admin, logout ; IT login 200/401 JSON, logout 204, contrat /me | ✅ |
| `/admin` dashboard | stats réelles | AdminFacade (AGENT+) | GET /admin/dashboard | features/admin/dashboard | E2E manuel : tuiles (75/37/16/13, délai moyen, outbox) + dossiers par équipe | ✅ |
| `/admin/reports` + détail | file scopée équipe, affectation, transitions+motif, verrou optimiste | AdminFacade, WorkflowService | GET/POST /admin/reports/* | features/admin/reports + report-detail | E2E manuel : agent.proprete a traité 000110-2026 (IN_PROGRESS -> message public -> DONE) ; IT périmètre équipe (404/403) + 409 version stale | ✅ |
| `/admin/moderation` | publier/masquer, texte public, médias, catégorie, doublon | ModerationService (MODERATOR+) | POST /admin/reports/{id}/… | features/admin/moderation + report-detail | E2E manuel : approbation média + publication de 000110-2026 par moderator ; IT rôle MODERATOR requis | ✅ |
| `/admin/catalog` | activation type, routage | CatalogService (ADMIN) | PATCH /admin/catalog/* | features/admin/catalog | IT rôle ADMIN requis (SpaApiIT) ; écran rendu | ✅ |
| `/admin/users` | création, rôles, équipes, activation | StaffUserService (ADMIN) | /admin/users | features/admin/users | Écran écrit (création avec mot de passe initial, activation) ; protégé ADMIN côté API | ✅ |
| `/admin/content` | édition FR/AR/EN | ContentService (ADMIN) | PUT /admin/content/{slug} | features/admin/content | Écran écrit (onglets FR/AR/EN, saisie AR en RTL) ; protégé ADMIN côté API | ✅ |
| `/admin/contact` | boîte interne, marquer traité | ContentService (AGENT+) | /admin/contact-messages | features/admin/contact-inbox | Écran écrit ; endpoint protégé (chaîne /admin) | ✅ |
| `/admin/settings` | paramètres effectifs + audit + périmètre démo | AppProperties/Boundary/Audit (ADMIN) | GET /admin/settings, /admin/audit | features/admin/settings | Écran écrit (bannière périmètre démo, audit) ; endpoints ADMIN | ✅ |
| Export CSV | file filtrée, anti-injection formule | AdminFacade | GET /admin/reports/export.csv | bouton admin (window.open filtres) | IT export protégé + en-tête `reference;type;` (SpaApiIT) | ✅ |
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

- 2026-09-09 : état de référence exécuté (verify 58 tests OK) avant toute modification.
- 2026-09-09 : API REST /api/v1 écrite, SpaApiIT 13/13 (CSRF cookie/en-tête, login JSON,
  dépôt anonyme idempotent, périmètre équipe, 409 version, non-divulgation, CSV).
- 2026-09-09 : SPA Angular complète (public + admin), `ng build` OK, `ng test` 4/4
  (i18n). Tranche complète vérifiée en navigateur (dev server + proxy, base copiée
  `civiccare_ng`) : dépôt citoyen avec photo → modération (média approuvé, publication)
  → traitement agent (statuts + message public) → fiche publique résolue ; favoris
  `cc_device` créés sous Vaadin toujours visibles ; double opt-in e-mail via Mailpit ;
  bascule FR/AR/EN à chaud avec RTL (carte non inversée).
- 2026-09-09 : Vaadin retiré (vues, starter, plugin, config) ; SpaController liste
  blanche ; profil Maven production (npm ci + ng build + copie dist) ; jar de
  production vérifié en navigateur (deep links HTML, worker MapLibre .mjs en
  text/javascript, API inconnue → 404 JSON, 0 ressource en échec après renommage
  du dossier médias Angular en `ng-media` — collision avec `/media/{key}` photos).
  Suite complète : **67/67** (dont SpaRoutingIT 4/4).
- 2026-09-09 : M12 vérifié avec la stack d'observabilité : trace unique Tempo
  `d76c097da3839cda5a25b67602deb0cb` avec racine `civiccare-tunis-web` (HTTP GET
  /api/v1/reports, nom templatisé) et enfants `civiccare-tunis` (contrôleur, span
  métier report.search, SQL, commit). Proxy `/api/telemetry/traces` → collector,
  exempté de CSRF (POST hors HttpClient), 204 systématique côté navigateur.

## Limitations et vérifications non exécutées (honnêteté)

- **E2E automatisés (Playwright)** : non mis en place ; les parcours ont été vérifiés
  manuellement en navigateur (dev + jar de production) et par 67 tests d'intégration
  backend. À ajouter pour la CI.
- **Captures d'écran 390/768/1440 FR/AR/EN en fichiers** : non produites ; la parité
  visuelle (FR/AR RTL/EN, mobile <900 px avec bascule liste/carte) a été vérifiée
  interactivement, sans export d'images.
- **Image Docker** : Dockerfile mis à jour (étape Node 24 + profil production) mais le
  build d'image compose n'a pas été relancé dans cet environnement ; le jar produit
  par `./mvnw package -Pproduction` a, lui, été démarré et vérifié.
- **`ng test`** couvre le service i18n uniquement (4 tests) ; pas de tests unitaires
  de composants Angular.
- Vignettes 404 possibles sur données de démo : lignes `report_media` orphelines
  (volume médias vide) héritées du seed — sans rapport avec la migration.
- Particularité connue : en zoneless, la validité des FormControls n'est pas un signal
  (corrigé via `statusChanges` → signal) et les composants feuilles traduits uniquement
  par pipe nécessitent le `markForCheck` du pipe `t` (implémenté).
