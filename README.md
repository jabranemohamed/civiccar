# CivicCare Tunis

Plateforme de signalement citoyen pour la ville de **Tunis** (Tunisie) — **application de
démonstration**, sans affiliation avec la municipalité de Tunis. Les habitants localisent
un problème urbain, choisissent son type (15 familles / 40 types, français + arabe RTL
+ anglais),
joignent des photos et suivent le traitement, sans compte. Les agents reçoivent, modèrent,
affectent et traitent les dossiers dans un back-office.

Stack : **Java 25 · Spring Boot 4.1.1 · Angular 21 + Angular Material 21 (SPA) ·
PostgreSQL 18 + PostGIS 3.6 · MapLibre GL JS · OpenTelemetry** (voir
[docs/versions.md](docs/versions.md) et [docs/migration-angular21.md](docs/migration-angular21.md)).

## Démarrage rapide

Prérequis : Docker (24+) avec Compose. Aucun compte SaaS ni clé payante : le géocodeur de
démonstration fonctionne hors ligne ; seul le fond de carte télécharge des tuiles (OSM).

```bash
cp .env.example .env          # adaptez DB_PASSWORD au minimum
docker compose up --build -d  # app + PostgreSQL/PostGIS + Mailpit
```

- Application : http://localhost:8080 (dans le compose ; en développement local : 8081)
- Mailpit (e-mails de dev) : http://localhost:8025
- Base : port hôte 55432 (pour éviter les conflits avec un PostgreSQL local)

Avec observabilité (collector OTel, Tempo, Prometheus, Loki, Grafana) :

```bash
OTEL_SDK_DISABLED=false docker compose --profile observability up --build -d
# Grafana : http://localhost:3000 (dashboards provisionnés, dossier « CivicCare Tunis »)
```

## Développement local (sans conteneur applicatif)

Prérequis : JDK 25 (`JAVA_HOME` pointant dessus), Docker pour la base.

```bash
docker compose up -d db mailpit
DB_URL=jdbc:postgresql://localhost:55432/civiccare SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run
```

Le backend seul ne sert pas l'UI en développement : lancez aussi le serveur Angular
(proxy `/api` et `/media` vers :8080) :

```bash
cd frontend && npm ci && npm start -- --port 4300   # Node 24 requis (matrice Angular 21)
```

UI de développement : http://localhost:4300. Si le port 8080 est occupé, exportez
`SERVER_PORT` et ajustez `frontend/proxy.conf.json`.

## Build et tests

```bash
./mvnw verify                 # tests unitaires + intégration (Testcontainers PostGIS réel)
./mvnw -Pproduction package   # jar de production : npm ci + ng build + SPA dans le jar
cd frontend && npm test       # tests unitaires Angular (vitest)
```

Le profil `production` exige Node 24 et npm sur le PATH.

Les tests d'intégration démarrent un conteneur `imresamu/postgis:18-3.6` : Docker doit
tourner.

## Comptes de démonstration

Créés au démarrage (bootstrap). **Hors production uniquement**, si la variable
d'environnement correspondante est vide, le mot de passe local de développement
**`demo1234!`** est appliqué. En production, un compte sans variable n'est pas créé.

| Identifiant | Rôle | Équipe | Variable |
|---|---|---|---|
| `admin` | ADMIN | — | `ADMIN_BOOTSTRAP_PASSWORD` |
| `moderator` | MODERATOR | — | `MODERATOR_BOOTSTRAP_PASSWORD` |
| `agent.proprete` | AGENT | Propreté urbaine (démo) | `AGENT_BOOTSTRAP_PASSWORD` |
| `agent.voirie` | AGENT | Voirie (démo) | `AGENT_BOOTSTRAP_PASSWORD` |
| `agent.eclairage` | AGENT | Éclairage public (démo) | `AGENT_BOOTSTRAP_PASSWORD` |
| `agent.espaces` | AGENT | Espaces publics (démo) | `AGENT_BOOTSTRAP_PASSWORD` |

## Parcours de test suggérés

1. **Citoyen** : `/` → explorer carte/liste (64 dossiers de démonstration), filtres,
   recherche FR/AR → « Signaler un problème » → assistant en 4 étapes (adresse démo :
   tapez « Bourguiba » ou « المدينة ») → référence retournée → e-mail d'accusé dans Mailpit
   → activer l'abonnement via le lien.
2. **Modération** : connexion `moderator` → `/admin/moderation` → publier un dossier →
   il apparaît sur la carte publique.
3. **Traitement** : connexion `agent.proprete` → `/admin/reports` (file limitée à son
   équipe) → ouvrir un dossier → affecter, passer « En traitement » puis « Traité » →
   l'abonné actif reçoit un e-mail (Mailpit).
4. **Langue** : sélecteur Français / العربية / English → arabe en RTL complet (carte non
   inversée), anglais avec catalogue traduit (repli français si une traduction manque).
   Ville/pays configurables par variables d'environnement (`APP_NAME`, `APP_COUNTRY`,
   `MAP_CENTER_*`) — Tunis/Tunisie par défaut, voir docs/tunis-configuration.md.
5. **API** : `curl http://localhost:8080/api/georeport/v2/requests.json?status=open`
   (voir [docs/api.md](docs/api.md)).

## URLs principales

| Chemin | Rôle |
|---|---|
| `/` | carte + liste publiques |
| `/report` | assistant de dépôt |
| `/requests/{référence}` | fiche publique |
| `/following` | suivis de l'appareil |
| `/info/faq` … `/info/api`, `/contact` | pages d'information et contact |
| `/login`, `/admin/**` | espace agents |
| `/api/georeport/v2/*` | API Open311 (GET) |
| `/actuator/health` | sondes |

## Documentation

- [docs/architecture.md](docs/architecture.md) — monolithe modulaire, choix structurants
- [docs/versions.md](docs/versions.md) — versions épinglées et décisions de compatibilité
- [docs/tunis-configuration.md](docs/tunis-configuration.md) — périmètre, géocodage,
  langues, urgences, données opérateur à compléter
- [docs/security.md](docs/security.md) — authentification, médias, non-divulgation
- [docs/data-retention.md](docs/data-retention.md) — archivage 30 j, purge 90 j, sauvegardes
- [docs/observability.md](docs/observability.md) — OTel, dashboards, preuve de bout en bout
- [docs/api.md](docs/api.md) — Open311 + exemples exécutables
- [docs/functional-coverage.md](docs/functional-coverage.md) — matrice F01–F33 / A01–A18

## Dépannage

- **Port 5432/8080 occupé** : la base est mappée sur 55432 ; exportez `SERVER_PORT` pour
  l'app locale.
- **Node incompatible** : Angular 21 exige Node `^20.19 || ^22.12 || ^24` ; utilisez
  Node 24 (les versions plus récentes ne sont pas supportées par le CLI).
- **Pas de carte** : vérifiez l'accès réseau aux tuiles (`MAP_TILE_URL`) ; la liste et la
  saisie manuelle de coordonnées restent fonctionnelles hors ligne.
- **Mac Apple Silicon** : l'image PostGIS utilisée est multi-arch (`imresamu/postgis`).
- **Production bloquée au démarrage** : comportement voulu tant que
  `GEO_BOUNDARY_VALIDATED=true` n'est pas positionné après validation d'un contour
  municipal vérifié (voir docs/tunis-configuration.md).
