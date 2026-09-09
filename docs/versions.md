# Versions et décisions de compatibilité

Date de résolution : **2026-09-08**. Toutes les versions ont été vérifiées ce jour-là sur les
sources officielles indiquées, puis validées par compilation et exécution réelles.

## Stack applicative

| Composant | Version retenue | Source vérifiée | Décision |
|---|---|---|---|
| Java | **25** (JDK local Oracle 25.0.4 LTS ; images `eclipse-temurin:25-jdk/jre`) | `java -version`, hub Docker | Exigence du cahier des charges ; `maven.compiler.release=25`, aucune fonctionnalité preview |
| Spring Boot | **4.1.1** | `api.spring.io/projects/spring-boot/releases` (current, GENERAL_AVAILABILITY) | Dernière GA ; supporte Java jusqu'à 26 |
| Angular / Material / CDK | **21.2.x** (core 21.2.22, CLI 21.2.23, Material+CDK 21.2.14) | registre npm | Dernier correctif 21.x ; Angular 22 exclu volontairement (exigence). Node `^20.19 ∥ ^22.12 ∥ ^24`, TypeScript 5.9, RxJS 7.8 |
| PostgreSQL + PostGIS | **18.1 / 3.6** via image **`imresamu/postgis:18-3.6`** | hub Docker (manifest) | `postgis/postgis:18-3.6` ne publie **pas** d'arm64 : l'image communautaire multi-arch `imresamu/postgis` (build équivalent) est épinglée pour fonctionner sur amd64 **et** arm64. Testcontainers utilise la même image |
| Flyway | géré par le BOM Spring Boot (starter `spring-boot-starter-flyway` + `flyway-database-postgresql`) | BOM 4.1.1 | Spring Boot 4 a modularisé Flyway dans un starter dédié (découvert à l'exécution : sans le starter, aucune migration ne s'exécute) |
| Hibernate ORM / Spatial | géré par le BOM (ORM 7.x, `hibernate-spatial`) | BOM 4.1.1 | Points JTS `geometry(Point,4326)` |
| Testcontainers | **2.0.5** via BOM Spring Boot | BOM 4.1.1 | Modules renommés en 2.x : `testcontainers-postgresql`, `testcontainers-junit-jupiter` |
| libphonenumber | **9.0.38** | maven-metadata | Normalisation TN/international |
| OpenTelemetry API | **1.65.0** (BOM) | maven-metadata | Alignée sur l'agent 2.31.1 (SDK fourni par l'agent, jamais un second SDK applicatif) |
| Agent Java OpenTelemetry | **2.31.1** | GitHub releases `opentelemetry-java-instrumentation` | Chargé par `-javaagent` dans l'image Docker |
| MapLibre GL JS | **6.7.0** | registre npm | 6.7.0 épinglée (version validée avec le wrapper Angular ; worker servi en asset statique). v6 n'a plus d'export par défaut → imports nommés |
| JUnit | Jupiter via BOM Spring Boot 4.1 | BOM | `spring-boot-starter-test` + `spring-security-test` |

## Images Docker épinglées (aucun tag `latest`)

| Image | Tag |
|---|---|
| `imresamu/postgis` | `18-3.6` |
| `axllent/mailpit` | `v1.31.1` |
| `otel/opentelemetry-collector-contrib` | `0.160.0` |
| `grafana/tempo` | `3.0.3` |
| `grafana/loki` | `3.7.7` |
| `prom/prometheus` | `v3.14.0` |
| `grafana/grafana` | `13.2.1` |
| `eclipse-temurin` | `25-jdk` (build) / `25-jre` (run) |

## Incompatibilités rencontrées et résolues

- **Spring Security 7** : `AntPathRequestMatcher` supprimé → motifs de chemin directs.
- **Spring Data** : repositories imbriqués nécessitent `considerNestedRepositories = true`.
- **Node local** : Node 26 installé sur la machine > maximum supporté par Angular 21 (24) → utiliser
  et utilise automatiquement son propre Node 24.20.0 (`~/.vaadin`).
