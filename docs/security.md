# Sécurité

## Authentification et autorisations

- Spring Security 7 + `VaadinSecurityConfigurer` : CSRF Vaadin conservé (jamais désactivé
  globalement) ; seule la chaîne REST stateless `/api/**` (lecture publique) est sans CSRF.
- Aucune inscription publique : les citoyens n'ont pas de compte. Rôles internes `AGENT`,
  `MODERATOR`, `ADMIN`.
- Contrôles appliqués aux trois niveaux : routes Vaadin (`@AnonymousAllowed` /
  `@RolesAllowed`), services (`@PreAuthorize`) et **accès objet** (un AGENT n'agit que sur
  les dossiers de ses équipes ; vérifié dans `WorkflowService`/`AdminFacade`, testé avec
  deux services distincts).
- Mots de passe : `DelegatingPasswordEncoder` (bcrypt par défaut, migrable). Bootstrap des
  comptes par variables d'environnement ; en production un compte sans variable n'est pas
  créé ; message d'erreur de connexion générique.
- Limitation de débit en mémoire sur dépôt, contact et abonnement (fenêtre fixe).
  Anti-robot discret : champ honeypot invisible sur le formulaire de contact (choix
  CivicCare) — aucun CAPTCHA payant.

## Données personnelles et non-divulgation

- Contact du déclarant (e-mail, téléphone), plaque et texte source : **privés**, jamais
  dans le HTML public, l'API, la carte, les erreurs ni les tâches asynchrones. Tests de
  non-divulgation dans `VisibilityAndDuplicatesIT` et `Open311ContractIT`.
- Un dossier non publié répond comme un dossier inexistant (aucune distinction observable).
- Jetons (abonnements, appareil) : 256 bits d'entropie, **hachés SHA-256 en base**, une
  finalité, usage unique, expiration. Aucun e-mail dans les URLs. Les liens e-mail (GET)
  affichent une confirmation ; l'action est déclenchée par un clic (requête serveur),
  jamais par un scanner de messagerie.
- L'état d'abonnement n'est pas public ; la demande d'abonnement répond de façon identique
  qu'un abonnement existe ou non.

## Médias

- Privés par défaut (`PENDING`) : seuls les dérivés **approuvés** sont diffusés
  publiquement ; le personnel authentifié voit les médias en attente.
- Validation serveur : taille (≤ 10 Mo), **signature réelle** (JPEG/PNG uniquement — WebP
  refusé car le décodage n'est pas pris en charge par ImageIO standard, limite documentée),
  dimensions lues avant décodage complet (≤ 12000 px / 40 MP, budget mémoire).
  SVG et contenus actifs rejetés.
- Normalisation de l'orientation EXIF puis **réencodage JPEG** : toutes les métadonnées
  (EXIF, GPS) sont supprimées ; miniatures produites.
- Aucune écriture disque avant la soumission du dossier (pas d'upload orphelin) ; clés de
  stockage générées (UUID) et validées par motif — pas de traversée de chemin ; aucune URL
  d'origine arbitraire téléchargée côté serveur.
- Stockage local persistant derrière le port `MediaStorage` (volume Docker) ; un adaptateur
  S3 peut être branché en production ; métadonnées en PostgreSQL, pas de chemins système
  exposés.

## Rendu et injections

- Tout texte utilisateur est rendu **comme texte** (composants Vaadin) ; aucun HTML riche
  autorisé, donc pas de sanitizer nécessaire.
- Export CSV protégé contre l'injection de formules (préfixe `'` sur `=`, `+`, `-`, `@`),
  généré par pages de 100 lignes.
- Requêtes SQL natives exclusivement paramétrées.

## Configuration et exploitation

- Secrets par variables d'environnement ; `.env.example` sans secret ; aucun secret commité.
- Docker : exécution **non-root**, images épinglées.
- Actuator : seuls `health` (sondes) et `prometheus` sont exposés ; en compose, Prometheus
  scrape le collector, pas l'application (une seule source par métrique).
- Sessions : cookies HttpOnly, `Secure` activé par le profil production.

## Limites connues

- Limiteur de débit et cache géocodage en mémoire : à externaliser pour du multi-instance.
- Exactement-une-fois impossible en SMTP : les doublons applicatifs sont empêchés par clés
  de déduplication (outbox + notification_delivery), mais un acquittement SMTP perdu après
  envoi effectif peut produire un doublon (documenté, voir docs/data-retention.md).
