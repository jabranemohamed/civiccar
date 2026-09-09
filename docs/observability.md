# Observabilité

## Architecture

```
Application Java (agent OTel 2.31.1, -javaagent)
        │ OTLP http/protobuf
        ▼
OpenTelemetry Collector 0.160.0 (memory_limiter, scrub d'attributs, batch)
   ├── traces  → Tempo 3.0.3 (OTLP)
   ├── métriques → endpoint Prometheus :9464 ← scrape Prometheus 3.14.0
   └── logs    → Loki 3.7.7 (endpoint OTLP natif)
                        ▼
                 Grafana 13.2.1 (sources provisionnées + liens logs ↔ traces)
```

Lancement : `docker compose --profile observability up --build -d` puis `OTEL_SDK_DISABLED=false`
pour le service `app` (variable du compose). Grafana : http://localhost:3000 (anonyme, local).

## Choix d'instrumentation

- **Agent Java OpenTelemetry 2.31.1** chargé par `-javaagent` dans l'image Docker. L'agent
  fournit le SDK et l'export OTLP ; l'application n'initialise **aucun second SDK** et
  n'embarque aucun starter OTel concurrent — elle n'utilise que l'API (`opentelemetry-api`
  1.65.0, alignée sur l'agent) via `GlobalOpenTelemetry`.
- Sans agent ou avec `OTEL_SDK_DISABLED=true`, l'API est no-op : **une panne du collector
  n'empêche jamais le dépôt** (vérifié : le mode développement tourne sans collector).
- Bibliothèques instrumentées automatiquement par l'agent (vérifiées dans les traces) :
  Tomcat/servlet (HTTP serveur), JDBC/HikariCP, logback (logs OTLP corrélés), JVM runtime
  metrics, java.net.http (appels géocodeur externes lorsqu'activés).
- Les requêtes HTTP seules ne suffisent pas à identifier une opération métier : des **spans
  métier explicites** sont créés par la façade `Telemetry`.

## Spans métier

`report.create`, `report.search`, `report.find_duplicates`, `report.assign`,
`report.change_status`, `report.moderate`, `media.process`, `subscription.confirm`,
`notification.deliver`, `report.archive`, `privacy.purge`.

- Propagation W3C par l'agent. Les envois outbox asynchrones portent un **lien de span**
  vers la trace d'origine (traceId/spanId stockés dans le payload) — aucun span maintenu
  ouvert entre transactions.
- Les refus de validation métier sont distingués des pannes serveur : attribut
  `civiccare.result=rejected` sans statut ERROR ; les vraies erreurs portent
  `StatusCode.ERROR`.

## Métriques

| Nom OTel | Type | Conversion Prometheus |
|---|---|---|
| `civiccare.reports.created` | compteur (attr `civiccare.category`) | `civiccare_reports_created_total{civiccare_category=…}` |
| `civiccare.reports.transitions` | compteur (attr `civiccare.status`) | `civiccare_reports_transitions_total` |
| `civiccare.media.failures` | compteur (attr `civiccare.result`) | `civiccare_media_failures_total` |
| `civiccare.notifications.deliveries` | compteur (attrs kind/result) | `civiccare_notifications_deliveries_total` |
| `civiccare.usecase.duration` | histogramme ms (attr `civiccare.usecase`) | `civiccare_usecase_duration_*` |
| `civiccare.outbox.backlog` | jauge | `civiccare_outbox_backlog` |
| `civiccare.reports.active` | jauge | `civiccare_reports_active` |
| + JVM/HTTP de l'agent | — | `jvm_*`, `http_server_request_duration_*` |

- Les jauges sont rafraîchies par un job périodique (60 s) — pas de requête lourde à chaque
  scrape.
- Cardinalité faible uniquement : catégorie prédéfinie, statut, résultat. **Jamais**
  d'identifiant de dossier, e-mail, coordonnées, jeton ni texte libre en label.
- Une seule source par métrique : Prometheus scrape **le collector** ; l'endpoint Actuator
  sert uniquement aux sondes de santé.

## Logs

- Logs applicatifs structurés capturés par l'appender logback de l'agent → OTLP → Loki
  (config OTLP native, labels indexés à faible cardinalité : `service.name`,
  `deployment.environment.name`). stdout conservé pour le diagnostic local ; aucun second
  collecteur n'ingère les mêmes logs.
- Interdits en journalisation (appliqué au code + scrub défensif au collector) : corps de
  formulaires, e-mail, téléphone, plaque, coordonnées exactes, cookies, jetons, URLs à
  paramètres secrets, paramètres SQL. Hibernate ne journalise pas les paramètres SQL.
- Rétention démonstration : Tempo 48 h, Loki 48 h.

## Dashboards et alertes

Provisionnés dans Grafana (dossier « CivicCare Tunis ») : santé technique,
erreurs/latence, activité citoyenne, e-mails/backlog. Règles d'alerte Prometheus locales
(hausse d'erreurs, latence p95, backlog outbox durable, échecs de notification) — **aucun
envoi vers un destinataire réel** (pas d'Alertmanager).

## Scénario de preuve (smoke test)

Script : `scripts/observability-smoke.sh` (documenté dans le README).
1. Démarrer `docker compose --profile observability up --build -d` (app avec
   `OTEL_SDK_DISABLED=false`).
2. Créer un dossier fictif via l'UI ou l'API interne, le traiter (connexion agent), ce qui
   déclenche un e-mail Mailpit.
3. Vérifier :
   - une trace `report.create` dans Tempo (Grafana → Explore → Tempo, requête
     `{name="report.create"}`) ;
   - la métrique `civiccare_reports_created_total` dans Prometheus ;
   - un log corrélé (trace_id identique) dans Loki.
4. Panne SMTP simulée : arrêter Mailpit, changer un statut → la création/transition
   fonctionne, l'outbox passe en retry (backlog visible), redémarrer Mailpit → reprise.
5. Panne collector : arrêter otel-collector → le dépôt de signalement continue de
   fonctionner (vérifié aussi par la suite de tests qui tourne sans collector).

Le résultat de l'exécution réelle de ce scénario est consigné dans
`docs/functional-coverage.md` (A16).
