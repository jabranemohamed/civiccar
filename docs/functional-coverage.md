# Matrice de couverture — F01–F33 et A01–A18

Statuts : ✅ réalisé et vérifié · ⚠️ réalisé avec limite documentée · ❌ non réalisé.
Les preuves « navigateur » proviennent des parcours exécutés le 2026-09-08 (captures dans
`docs/screenshots/`) ; les preuves « test » des suites `src/test/java` (voir le rapport
d'exécution en fin de document).

## Inventaire fonctionnel F01–F33

| ID | Statut | Réalisation CivicCare Tunis | Preuve |
|---|---|---|---|
| F01 | ✅ | Accueil avec présentation et bandeau urgences générique (numéros affichés uniquement après vérification officielle, non exécutée → consigne générique) | navigateur |
| F02 | ✅ | Navigation carte/liste, suivis, infos, contact ; panneau latéral 400 px + carte | navigateur |
| F03 | ✅ | Carte MapLibre : déplacement, zoom, regroupement, sélection, synchronisation liste, recherche par emprise | navigateur |
| F04 | ✅ | Liste avec compteur de résultats et état vide | navigateur |
| F05 | ✅ | Recherche par référence exacte ou texte de description (FR/AR, insensible aux diacritiques) | test `VisibilityAndDuplicatesIT` |
| F06 | ✅ | Filtres famille, statut, période (aujourd'hui/semaine/mois en Africa/Tunis), archives, reset | navigateur |
| F07 | ✅ | Fiche : référence, type, photos approuvées, adresse, coordonnées, description | navigateur |
| F08 | ✅ | Frise chronologique : statuts, horodatages, messages publics | navigateur |
| F09 | ✅ | Bouton Suivre + onglet Mes suivis avec compteur (cookie appareil) | navigateur + test `SubscriptionIT` |
| F10 | ✅ | Dépôt sans compte | navigateur + test `ReportCreationIT` |
| F11 | ✅ | 15 familles / 40 types hiérarchisés avec recherche | test `CatalogIT` |
| F12 | ✅ | Jusqu'à 3 photos, compteur 0/3, conseils, facultatif, aperçu/suppression | navigateur + tests |
| F13 | ✅ | Adresse recherchable (géocodeur démo/Nominatim), géolocalisation au clic, placement carte, périmètre municipal serveur | navigateur + test A06 |
| F14 | ✅ | Description requise 1–300 caractères (normalisation trim+NFC documentée) | test |
| F15 | ✅ | E-mail requis privé, téléphone facultatif privé (E.164, région TN) | tests |
| F16 | ✅ | Consentement obligatoire non précoché, version du texte enregistrée | navigateur + test |
| F17 | ✅ | Champs obligatoires indiqués, erreurs près des champs, revalidation serveur | navigateur + tests |
| F18 | ✅ | Affiches politiques : parti requis (catalogue fictif), description standard désactivée | test `CatalogIT`/`ReportCreationIT` |
| F19 | ✅ | Aides contextuelles par type ; véhicules hors d'usage : plaque privée facultative | test |
| F20 | ✅ | Doublons candidats avant création (50 m, même famille, publics actifs) avec consultation/confirmation | navigateur + test |
| F21 | ✅ | E-mail après dépôt (Mailpit vérifié), abonnement activé par lien, notification des changements de statut | navigateur + Mailpit + tests |
| F22 | ✅ | Routage par type vers équipes fictives + file de triage | test `CatalogIT` |
| F23 | ✅ | Modération : publier/masquer, adaptation du texte public, retrait de photo, correction de catégorie auditée | test `WorkflowAndPermissionsIT` |
| F24 | ✅ | Dossier non publié traité normalement (workflow indépendant de la publication) | test |
| F25 | ✅ | Statuts OPEN / IN_PROGRESS / DONE_OR_ORDERED / OUT_OF_SCOPE + archives | tests |
| F26 | ✅ | Archivage 30 jours après clôture (job + horloge injectable) | test `RetentionIT` |
| F27 | ✅ | Purge des données personnelles 90 jours après clôture | test `RetentionIT` |
| F28 | ✅ | Open311 GET JSON/XML sans clé (services, requests, request) ; POST non implémenté (documenté) | test `Open311ContractIT` |
| F29 | ✅ | FAQ, conditions, mentions, confidentialité, accessibilité, doc API (FR/AR, éditables) | navigateur |
| F30 | ✅ | Contact : nom/e-mail/message requis, copie facultative, consentement, honeypot « Website », boîte interne | test `ContactIT` |
| F31 | ✅ | Responsive 390/768/1440 vérifié (captures) | navigateur |
| F32 | ⚠️ | Sémantique (labels, rôles, aria, focus visible, cibles 44 px) ; alternative à la carte ; conformité WCAG globale **non certifiée** (vérification manuelle partielle) | navigateur |
| F33 | ⚠️ | Pas de tracking : emplacement prévu pour un adaptateur d'audience optionnel, **désactivé par défaut et non implémenté** (l'observabilité OTel est technique, séparée) | code |

## Critères d'acceptation A01–A18

| ID | Statut | Preuve / limite |
|---|---|---|
| A01 | ✅ | `./mvnw -Pproduction package` OK (bundle Vaadin prod) ; démarrage sur base vierge (9 migrations) puis redémarrage avec données conservées vérifiés |
| A02 | ✅ | `CatalogIT` : 15 familles, 40 types, recherche FR sans accents + AR, aides |
| A03 | ✅ | `ReportCreationIT` : 0 photo OK, 3 photos OK, 4e refusée, SVG/WebP refusés, 12 Mo refusé, description vide/301 refusées — côté serveur |
| A04 | ✅ | e-mail/consentement requis, téléphone facultatif validé (TN + international) ; refus GPS → saisie manuelle (navigateur) |
| A05 | ✅ | parti requis (option du catalogue uniquement), description masquée pour affiches, plaque stockée dans le contact privé |
| A06 | ✅ | double submit → 1 dossier (clé d'idempotence) ; points testés : Tunis (dedans), Le Bardo/Ariana/Ben Arous (dehors), sommet du polygone (dedans, ST_Covers) ; distinction municipalité/gouvernorat/agglomération documentée ; production bloquée si périmètre démo |
| A07 | ✅ | candidats à ≤50 m même famille, dossier caché jamais révélé, « autre problème » toujours déclarable (navigateur : dialogue avec 22 m/40 m) |
| A08 | ✅ | même visibilité liste/carte (`SearchCriteria` partagé), pagination serveur (10/page, plafond carte 500), liste utilisable sans carte |
| A09 | ✅ | fiche publique exacte ; non publié = introuvable partout (UI, API, carte, médias, recherche) |
| A10 | ✅ | favoris sans compte ; aucun e-mail avant activation ; jetons usage unique/expirés/rejoués (`SubscriptionIT`) |
| A11 | ✅ | agent d'un autre service refusé (AccessDenied) et file scopée ; contrôle au service même en contournant l'UI |
| A12 | ✅ | modérer sans changer le workflow ; retirer une photo sans perdre le dossier ; transition auditée + e-mail via outbox |
| A13 | ✅ | `RetentionIT` avec horloge mutable : archive à J+31 (pas à J+29), purge à J+91, réouverture efface les échéances, abonnement indépendant conservé |
| A14 | ✅ | `Open311ContractIT` : JSON+XML, mapping open/closed, plage >90 j refusée, juridiction inconnue refusée, données privées absentes |
| A15 | ✅ | `ContactIT` + Mailpit : enregistrement, copie facultative respectée, honeypot silencieux, consentement, limite de débit |
| A16 | ✅ | Preuve exécutée sur la stack conteneurisée (rapport ci-dessous) : trace `report.create` dans Tempo, métrique dans Prometheus, log corrélé au trace_id dans Loki, jauge backlog ; panne SMTP réelle (Mailpit arrêté) : création conservée, message PENDING avec tentative, reprise SENT après redémarrage ; panne collector sans blocage du métier (app exploitée toute la construction sans collector) ; aucune donnée privée en télémétrie (revue attributs + scrub collector) |
| A17 | ✅ | captures 390/768/1440 en FR (LTR) et AR (RTL), carte non inversée, textes mixtes corrects, erreurs de formulaire annoncées |
| A18 | ✅ | catalogue/filtres/pages/validations/back-office/e-mails FR+AR ; recherche dans les deux écritures ; bornes journalières Africa/Tunis ; téléphones TN/international ; aucune donnée munichoise ; « intervention commandée » jamais présenté comme réparation physique |

## Rapport d'exécution (2026-09-08)

### Builds et tests

- `./mvnw verify` : **BUILD SUCCESS — 8 tests unitaires + 48 tests d'intégration, 0 échec**
  (Testcontainers `imresamu/postgis:18-3.6` réel, jamais H2). Suites : CatalogIT (7),
  ReportCreationIT (9), VisibilityAndDuplicatesIT (5), WorkflowAndPermissionsIT (8),
  RetentionIT (3), SubscriptionIT (4), Open311ContractIT (7), ContactIT (5),
  ImageProcessorTest (5), PhoneNormalizerTest (3).
- `./mvnw -Pproduction package` : BUILD SUCCESS (bundle Vaadin de production,
  jar 74,5 Mo).
- `docker compose --profile observability up --build -d` : 8 conteneurs sains
  (app avec agent OTel, db PostGIS, Mailpit, collector, Tempo, Loki, Prometheus, Grafana).
- Redémarrage avec données conservées : l'app conteneurisée a démarré sur la base
  existante (« Seed démo ignoré : 65 dossiers déjà présents »), migrations validées.

### Parcours navigateur exécutés (app réelle)

1. Dépôt complet FR (adresse démo → type → description mixte FR/AR → contact) :
   dialogue de doublons affiché (candidats à 22 m et 40 m avec liens), confirmation
   « autre problème », **référence 000100-2026** créée, accusé reçu dans Mailpit.
2. Activation de l'abonnement via le lien de l'e-mail (GET → confirmation → clic).
3. Modération (`moderator`) : publication de 000100-2026 depuis la file.
4. Traitement (`agent.proprete`) : fiche interne (contact privé, textes séparés),
   passage « En traitement » → **e-mail de mise à jour reçu par l'abonné**.
5. Suivi d'appareil : bouton Suivre/Ne plus suivre, « Mes suivis (1) » persistant
   à travers les redémarrages.
6. Bascule العربية : interface RTL complète, carte géographique non inversée.
7. Trois autres dépôts scriptés (Playwright) sur l'app conteneurisée : 000101 à 000103.

### Captures (docs/screenshots/)

18 captures publiques : {accueil, assistant, fiche} × {fr, ar} × {390, 768, 1440 px} —
**0 px de débordement horizontal partout, `dir=rtl` automatique en arabe** — plus
2 captures back-office (tableau de bord, file de travail).

### Preuve d'observabilité (scripts/observability-smoke.sh — exécuté avec succès)

```
OK health
OK trace report.create : 28da04bf9adcbb86c83d3f143aaa846f   (Tempo)
OK métrique : 2 création(s)                                  (Prometheus)
OK 1 log(s) corrélé(s) au trace_id 28da04bf…                 (Loki, métadonnée trace_id)
OK jauge backlog
```

Panne SMTP réelle : Mailpit arrêté → dépôt 000103-2026 réussi, outbox `PENDING/1 tentative` ;
Mailpit relancé → `SENT`, e-mail livré (backoff 60 s). Panne collector : le mode
développement a tourné toute la construction sans collector (API OTel no-op), dépôts
fonctionnels.

### Vérifications non exécutées (déclarées)

- Fournisseur de géocodage externe réel (Nominatim auto-hébergé) : non configuré dans cet
  environnement — recherches FR/AR et géocodage inverse réels à consigner lors de la
  configuration (docs/tunis-configuration.md).
- Vérification des numéros d'urgence sur une source tunisienne officielle : non exécutée →
  consigne générique sans numéro (comportement par défaut voulu).
- Audit d'accessibilité complet WCAG 2.2 AA : vérifications manuelles partielles
  (clavier, labels, focus, contrastes du thème) ; aucune certification revendiquée.
