#!/usr/bin/env bash
# Preuve d'observabilité de bout en bout (A16).
# Prérequis : APP_HOST_PORT=<port> OTEL_SDK_DISABLED=false docker compose --profile observability up --build -d
# puis au moins un signalement créé dans l'application (voir README, parcours de test).
# Les backends ne publient pas leurs ports sur l'hôte : les requêtes passent par les
# proxys de sources de données Grafana (accès anonyme local).
set -euo pipefail

BASE_APP="${BASE_APP:-http://localhost:8080}"
GRAFANA="${GRAFANA:-http://localhost:3000}"
TEMPO="$GRAFANA/api/datasources/proxy/uid/tempo"
PROM="$GRAFANA/api/datasources/proxy/uid/prometheus"
LOKI="$GRAFANA/api/datasources/proxy/uid/loki"

echo "== 1. Application en vie =="
curl -sf "$BASE_APP/actuator/health" >/dev/null && echo "OK health"

echo "== 2. Trace métier report.create dans Tempo =="
TRACE=$(curl -sf "$TEMPO/api/search?tags=name%3Dreport.create&limit=1" | python3 -c "
import json,sys
traces=json.load(sys.stdin).get('traces',[])
print(traces[0]['traceID'] if traces else '')")
if [ -n "$TRACE" ]; then
  echo "OK trace report.create : $TRACE"
else
  echo "ÉCHEC : aucune trace report.create (créez un signalement d'abord)" && exit 1
fi

echo "== 3. Métrique civiccare_reports_created_total dans Prometheus =="
COUNT=$(curl -sf -G "$PROM/api/v1/query" --data-urlencode "query=sum(civiccare_reports_created_total)" \
  | python3 -c "import json,sys; r=json.load(sys.stdin)['data']['result']; print(r[0]['value'][1] if r else 0)")
if [ "${COUNT%.*}" -ge 1 ] 2>/dev/null; then
  echo "OK métrique : $COUNT création(s)"
else
  echo "ÉCHEC : métrique absente" && exit 1
fi

echo "== 4. Log corrélé à la trace dans Loki (trace_id en métadonnée structurée OTLP) =="
NOW=$(date +%s); START=$(( (NOW - 7200) * 1000000000 )); END=$(( (NOW + 60) * 1000000000 ))
LOGS=$(curl -sf -G "$LOKI/loki/api/v1/query_range" \
  --data-urlencode "query={service_name=\"civiccare-tunis\"} | trace_id = \"$TRACE\"" \
  --data-urlencode "start=$START" --data-urlencode "end=$END" \
  | python3 -c "import json,sys; print(sum(len(s['values']) for s in json.load(sys.stdin)['data']['result']))")
if [ "$LOGS" -ge 1 ] 2>/dev/null; then
  echo "OK $LOGS log(s) corrélé(s) au trace_id $TRACE"
else
  echo "ÉCHEC : aucun log portant ce trace_id" && exit 1
fi

echo "== 5. Jauge de backlog outbox observable =="
curl -sf -G "$PROM/api/v1/query" --data-urlencode "query=civiccare_outbox_backlog" >/dev/null && echo "OK jauge backlog"

echo "Smoke test observabilité : SUCCÈS."
