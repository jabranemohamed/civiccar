# Déploiement gratuit : Render + Neon + Brevo

Déploiement de démonstration sans carte bancaire. Limites assumées : le service
s'endort après ~15 min d'inactivité (réveil ≈ 1 min), le disque est éphémère
(**les photos uploadées disparaissent à chaque redéploiement/redémarrage**),
base limitée à 0,5 Go.

## 1. Base de données — Neon (PostGIS)

1. Créer un compte sur <https://neon.tech> (gratuit, sans CB) et un projet
   PostgreSQL (région Europe conseillée).
2. Aucune action pour PostGIS : la migration `V1` exécute
   `CREATE EXTENSION IF NOT EXISTS postgis / unaccent / pg_trgm`
   (extensions disponibles sur Neon).
3. Récupérer les informations de connexion et les convertir au format JDBC :
   Neon affiche `postgresql://UTILISATEUR:MOTDEPASSE@HOTE/BASE` ; on en tire

   ```
   DB_URL      = jdbc:postgresql://HOTE/BASE?sslmode=require
   DB_USER     = UTILISATEUR
   DB_PASSWORD = MOTDEPASSE
   ```

   (le mot de passe ne va **pas** dans DB_URL).

## 2. E-mails — Brevo (300 e-mails/jour)

1. Créer un compte sur <https://www.brevo.com>, puis SMTP & API → *SMTP*.
2. Relever : identifiant SMTP (`SMTP_USER`), clé SMTP (`SMTP_PASSWORD`).
3. `MAIL_FROM` doit être un expéditeur validé dans Brevo (par défaut,
   l'adresse e-mail du compte).

Étape optionnelle pour une démo : sans SMTP configuré, l'application
fonctionne — seuls les e-mails d'abonnement partent en erreur dans l'outbox
(rejoués, jamais bloquants pour le reste).

## 3. Application — Render

1. Créer un compte sur <https://render.com> (gratuit, sans CB) et connecter le
   compte GitHub.
2. *New → Blueprint*, choisir le dépôt `jabranemohamed/civiccar`, branche
   `migration/angular21` (le fichier [render.yaml](../render.yaml) est détecté).
3. Renseigner les variables demandées (`sync: false`) :
   `DB_URL`, `DB_USER`, `DB_PASSWORD` (étape 1), `SMTP_USER`, `SMTP_PASSWORD`,
   `MAIL_FROM` (étape 2), et `APP_BASE_URL` = l'URL du service
   (ex. `https://civiccare-tunis.onrender.com` — visible dès la création).
4. Lancer le déploiement. Le build Docker enchaîne `npm ci` + `ng build` +
   `mvnw -Pproduction package` (compter 10–15 min au premier build), puis
   Flyway crée le schéma et le profil `demo` seed le jeu de données.
5. Santé : `https://…onrender.com/actuator/health` doit répondre `UP`.

## 4. Comptes de démonstration

Les mots de passe des comptes `admin`, `moderator`, `agent.proprete`,
`agent.voirie` sont **générés par Render** (`generateValue: true`) : les lire
dans *Environment* du service. Ne jamais réutiliser les mots de passe de dev.

## 5. Vérifications après déploiement

- Accueil : carte + liste des signalements seedés (FR/AR/EN, RTL).
- Dépôt d'un signalement avec photo → visible après modération.
  ⚠️ la photo disparaîtra au prochain redémarrage (disque éphémère).
- Abonnement e-mail : réception du lien `/s/confirm/...` (si Brevo configuré,
  et `APP_BASE_URL` correct, sinon le lien pointe vers localhost).
- `…/api/georeport/v2/services.json` : Open311 répond.

## Mémoire (512 Mo)

La JVM est bornée explicitement (`-Xmx224m`, métaspace 112 Mo, 20 threads
Tomcat) : mesuré **~490 Mo RSS** sous charge en local (macOS ; le décompte
cgroup Linux de Render est généralement plus favorable). Si le service est
tué pour dépassement mémoire : abaisser `-Xmx` à `192m` dans
`JAVA_TOOL_OPTIONS`, ou envisager Koyeb (service gratuit comparable).

## Hors périmètre de l'offre gratuite

- Persistance des médias (nécessite un disque payant ou un stockage objet).
- Observabilité (l'agent OTel est neutralisé par `OTEL_SDK_DISABLED=true`).
- Ce déploiement reste une **démonstration** : le profil `production` est
  volontairement bloqué tant qu'un contour municipal officiel n'est pas
  validé (`GEO_BOUNDARY_VALIDATED`).
