# API publique — Open311 GeoReport v2 (sous-ensemble GET)

L'API publie les données de **CivicCare Tunis** (jamais celles d'une autre ville) : seuls
les signalements **publiés** sont exposés, sans aucune donnée privée (contact, notes
internes, jetons, médias non approuvés).

**Périmètre de compatibilité** : uniquement les GET ci-dessous. La création (POST
Open311) et la découverte (`discovery`) ne sont **pas** implémentées : cette API n'annonce
pas une conformité intégrale GeoReport v2. La pagination (`LIMIT 200` côté serveur) est une
limite d'implémentation documentée, pas une extension du standard.

Juridiction applicative : `tunis` — identifiant de **démonstration**, non attribué
officiellement. Toute autre valeur de `jurisdiction_id` est rejetée (HTTP 400).

## Endpoints

### Catalogue des types

```bash
curl -s "http://localhost:8080/api/georeport/v2/services.json" | python3 -m json.tool
curl -s "http://localhost:8080/api/georeport/v2/services.xml"
```

Réponse (extrait) :

```json
[{
  "service_code": "TRASH_BIN_FULL",
  "service_name": "Poubelle pleine",
  "description": "",
  "metadata": false,
  "type": "realtime",
  "keywords": "",
  "group": "Poubelles"
}]
```

### Liste des signalements

```bash
curl -s "http://localhost:8080/api/georeport/v2/requests.json?status=open&jurisdiction_id=tunis"
curl -s "http://localhost:8080/api/georeport/v2/requests.json?service_code=STREET_LIGHT_LAMP_OUT"
curl -s "http://localhost:8080/api/georeport/v2/requests.json?start_date=2026-08-01T00:00:00Z&end_date=2026-09-01T00:00:00Z"
curl -s "http://localhost:8080/api/georeport/v2/requests.xml?status=closed"
```

Paramètres : `service_code`, `start_date`, `end_date` (ISO 8601), `status`
(`open`|`closed`), `jurisdiction_id`. **Plage maximale : 90 jours** ; défaut explicite :
les 90 derniers jours. Erreurs : corps GeoReport `[{"code":400,"description":"…"}]`
(équivalent XML `<errors><error>…`).

### Détail d'un signalement

```bash
curl -s "http://localhost:8080/api/georeport/v2/requests/000001-2026.json"
curl -s "http://localhost:8080/api/georeport/v2/requests/000001-2026.xml"
```

404 si la référence n'existe pas **ou** si le dossier n'est pas publié (aucune
distinction observable).

## Mapping des statuts (documenté)

| Statut interne | Open311 |
|---|---|
| `OPEN`, `IN_PROGRESS` | `open` |
| `DONE_OR_ORDERED`, `OUT_OF_SCOPE` | `closed` |
| archivé | conserve le mapping du résultat final |

L'état de **publication** n'est pas un état Open311 : les dossiers non publiés sont
simplement absents.

## Champs retournés

`service_request_id` (référence humaine), `status`, `service_code`, `service_name`,
`description` (texte public), `requested_datetime` / `updated_datetime` (ISO 8601 UTC),
`address`, `lat`, `long` (WGS84), `media_url` (première photo **approuvée**, sinon vide).

## Limitation de débit

Lecture publique non authentifiée ; le serveur limite la taille des réponses (200 lignes)
et la plage temporelle. Pour un déploiement public, placer un reverse-proxy avec
limitation de débit devant `/api/**` (le limiteur applicatif interne couvre les
formulaires, pas les GET anonymes).

## Contrat testé

`Open311ContractIT` vérifie : contenu JSON et XML, mapping open/closed, validation des
dates/juridiction/statut, filtre `service_code`, absence de données privées et 404 des
dossiers non publiés.
