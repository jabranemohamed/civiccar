# Conservation et suppression des données

Les délais ci-dessous sont des **choix de produit de démonstration**, repris des textes
publics du site de référence et configurables (`APP_ARCHIVE_DAYS`, `APP_PURGE_DAYS`). Ils
ne décrivent ni une règle municipale tunisienne ni une obligation légale universelle.

## Cycle de vie d'un dossier

1. **Dépôt** : dossier `OPEN`, publication `PENDING_REVIEW`. Contact privé enregistré avec
   version du consentement et horodatage.
2. **Clôture** : `DONE_OR_ORDERED` ou `OUT_OF_SCOPE` → `closed_at` est posé.
   « Traité ou intervention commandée » ne signifie pas réparation physique effective.
3. **Archivage (30 jours après clôture toujours effective)** : `archived_at` posé par le
   job nocturne (cron en Africa/Tunis, horloge injectable pour les tests). Les archives
   sont exclues de l'exploration par défaut, disponibles via le filtre explicite si publiées.
4. **Purge des données personnelles (90 jours après clôture toujours effective)** :
   - contact : e-mail, téléphone, plaque effacés (la trace du consentement est conservée) ;
   - texte source privé (`description_private`) effacé ;
   - abonnements **du dossier** : supprimés, ainsi que leurs jetons ;
   - `personal_data_purged_at` posé.
   Un abonnement **actif indépendant** (même adresse sur un autre dossier) n'est pas touché
   (testé dans `RetentionIT`).
5. **Réouverture** : autorisée depuis un état terminal avec motif obligatoire ; elle efface
   `closed_at` et `archived_at` (les échéances repartent de la prochaine clôture) sans
   effacer l'historique des événements.

## Espaces de données et périmètre de purge

| Espace | Contenu personnel | Politique |
|---|---|---|
| `report_contact` | e-mail, téléphone, plaque | purgé à J+90 |
| `report.description_private` | texte source pouvant contenir des éléments personnels | purgé à J+90 |
| `report.description_public` | texte publié | conservé, mais la modération est chargée d'en expurger les données personnelles avant publication |
| `subscription` / `action_token` | e-mail, jetons | purgés avec le dossier |
| `outbox_message` | e-mails en clair dans le payload | messages SENT/FAILED conservés 30 jours max recommandés ; en cas de purge d'un dossier, les messages non envoyés du dossier deviennent inopérants (adresse absente) — limite documentée : le payload historique n'est pas réécrit rétroactivement, prévoir une purge périodique de la table (`DELETE FROM outbox_message WHERE created_at < now() - interval '30 days'`) |
| `audit_event` | pas de duplication de données personnelles (acteur = identifiant interne, cible = référence) | conservation longue |
| Logs applicatifs / télémétrie | jamais d'e-mail, téléphone, plaque, coordonnées exactes, cookies, jetons, paramètres SQL | rétention Tempo/Loki 48 h en démonstration |
| Médias | dérivés réencodés sans EXIF | supprimables avec le dossier (cascade DB ; fichiers orphelins à nettoyer par tâche d'exploitation) |

## Sauvegardes et restauration

Les sauvegardes PostgreSQL contiennent l'état au moment de la sauvegarde, y compris des
données personnelles purgées depuis. **Procédure documentée** : après toute restauration,
réexécuter les jobs de rétention (`RetentionJobs.archiveDue()` / `purgeDue()` sont
idempotents et s'appuient sur `closed_at`), ce qui réapplique les suppressions échues.
Limiter la rétention des sauvegardes à la fenêtre de purge (90 jours) pour borner la
persistance résiduelle.

## Notifications : limites de l'exactement-une-fois

Les doublons applicatifs connus sont empêchés par des clés uniques (`outbox.dedup_key`,
`notification_delivery.dedup_key`). En revanche, si l'acquittement SMTP est perdu **après**
un envoi effectif, le message est réémis à la tentative suivante : un doublon est possible.
Cette limite est intrinsèque à SMTP et n'est pas présentée autrement.
