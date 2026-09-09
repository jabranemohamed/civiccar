package tn.civiccare.open311;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.civiccare.notifications.EmailComposer;
import tn.civiccare.shared.AppProperties;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API Open311 GeoReport v2 — sous-ensemble GET uniquement (services, requests, request).
 * Cette API publie les données de CivicCare Tunis ; la juridiction `tunis` est un
 * identifiant applicatif de démonstration, non attribué officiellement. Elle ne
 * constitue pas une intégration aux systèmes de la municipalité de Tunis.
 * Mapping des statuts : OPEN/IN_PROGRESS -> open ; DONE_OR_ORDERED/OUT_OF_SCOPE -> closed.
 * L'état de publication n'est pas un état Open311 : seuls les dossiers publiés sont exposés.
 */
@RestController
@RequestMapping("/api/georeport/v2")
public class Open311Controller {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    @PersistenceContext
    private EntityManager em;

    private final AppProperties props;
    private final Clock clock;
    private final EmailComposer emails;

    public Open311Controller(AppProperties props, Clock clock, EmailComposer emails) {
        this.props = props;
        this.clock = clock;
        this.emails = emails;
    }

    // ===== services =====

    @GetMapping(value = "/services.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<Object> servicesJson(@RequestParam(required = false) String jurisdiction_id) {
        ResponseEntity<Object> error = validateJurisdiction(jurisdiction_id, false);
        if (error != null) {
            return error;
        }
        return ResponseEntity.ok(serviceList());
    }

    @GetMapping(value = "/services.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<Object> servicesXml(@RequestParam(required = false) String jurisdiction_id) {
        ResponseEntity<Object> error = validateJurisdiction(jurisdiction_id, true);
        if (error != null) {
            return error;
        }
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<services>");
        for (Map<String, Object> s : serviceList()) {
            xml.append("<service>");
            appendXml(xml, s);
            xml.append("</service>");
        }
        xml.append("</services>");
        return ResponseEntity.ok(xml.toString());
    }

    private List<Map<String, Object>> serviceList() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT st.code, st.label_fr, cg.label_fr, st.help_fr
                FROM service_type st JOIN category_group cg ON cg.id = st.group_id
                WHERE st.active AND cg.active
                ORDER BY cg.sort, st.sort
                """).getResultList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : rows) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("service_code", r[0]);
            s.put("service_name", r[1]);
            s.put("description", r[3] == null ? "" : r[3]);
            s.put("metadata", false);
            s.put("type", "realtime");
            s.put("keywords", "");
            s.put("group", r[2]);
            out.add(s);
        }
        return out;
    }

    // ===== requests =====

    @GetMapping(value = "/requests.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<Object> requestsJson(
            @RequestParam(required = false) String jurisdiction_id,
            @RequestParam(required = false) String service_code,
            @RequestParam(required = false) String start_date,
            @RequestParam(required = false) String end_date,
            @RequestParam(required = false) String status) {
        return requests(jurisdiction_id, service_code, start_date, end_date, status, false);
    }

    @GetMapping(value = "/requests.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<Object> requestsXml(
            @RequestParam(required = false) String jurisdiction_id,
            @RequestParam(required = false) String service_code,
            @RequestParam(required = false) String start_date,
            @RequestParam(required = false) String end_date,
            @RequestParam(required = false) String status) {
        return requests(jurisdiction_id, service_code, start_date, end_date, status, true);
    }

    private ResponseEntity<Object> requests(String jurisdiction, String serviceCode, String startDate,
                                            String endDate, String status, boolean xml) {
        ResponseEntity<Object> error = validateJurisdiction(jurisdiction, xml);
        if (error != null) {
            return error;
        }
        Instant now = clock.instant();
        Instant start;
        Instant end;
        try {
            // Comportement par défaut explicite : les 90 derniers jours.
            end = endDate == null ? now : OffsetDateTime.parse(endDate).toInstant();
            start = startDate == null ? end.minus(Duration.ofDays(props.open311().maxDateRangeDays()))
                    : OffsetDateTime.parse(startDate).toInstant();
        } catch (Exception e) {
            return badRequest("start_date/end_date must be ISO 8601 (e.g. 2026-01-01T00:00:00Z)", xml);
        }
        if (start.isAfter(end)) {
            return badRequest("start_date must be before end_date", xml);
        }
        if (Duration.between(start, end).toDays() > props.open311().maxDateRangeDays()) {
            return badRequest("date range must not exceed " + props.open311().maxDateRangeDays() + " days", xml);
        }
        if (status != null && !status.equals("open") && !status.equals("closed")) {
            return badRequest("status must be open or closed", xml);
        }

        StringBuilder sql = new StringBuilder("""
                SELECT r.reference, r.workflow_status, st.code, st.label_fr, r.description_public,
                       r.created_at, r.updated_at, r.address, ST_Y(r.location), ST_X(r.location),
                       m.storage_key
                FROM report r
                JOIN service_type st ON st.id = r.service_type_id
                LEFT JOIN LATERAL (
                    SELECT storage_key FROM media_asset
                    WHERE report_id = r.id AND moderation_status = 'APPROVED'
                    ORDER BY sort LIMIT 1
                ) m ON true
                WHERE r.publication_status = 'PUBLISHED'
                  AND r.created_at >= :startTs AND r.created_at <= :endTs
                """);
        if (serviceCode != null) {
            sql.append(" AND st.code = :serviceCode");
        }
        if ("open".equals(status)) {
            sql.append(" AND r.workflow_status IN ('OPEN','IN_PROGRESS')");
        } else if ("closed".equals(status)) {
            sql.append(" AND r.workflow_status IN ('DONE_OR_ORDERED','OUT_OF_SCOPE')");
        }
        sql.append(" ORDER BY r.created_at DESC LIMIT :pageSize");
        Query query = em.createNativeQuery(sql.toString());
        query.setParameter("startTs", start);
        query.setParameter("endTs", end);
        if (serviceCode != null) {
            query.setParameter("serviceCode", serviceCode);
        }
        query.setParameter("pageSize", props.open311().pageSize());
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        List<Map<String, Object>> out = rows.stream().map(this::toRequestDto).toList();
        return xml ? ResponseEntity.ok(requestsToXml(out)) : ResponseEntity.ok(out);
    }

    @GetMapping(value = "/requests/{reference}.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<Object> requestJson(@PathVariable String reference,
                                              @RequestParam(required = false) String jurisdiction_id) {
        return singleRequest(reference, jurisdiction_id, false);
    }

    @GetMapping(value = "/requests/{reference}.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<Object> requestXml(@PathVariable String reference,
                                             @RequestParam(required = false) String jurisdiction_id) {
        return singleRequest(reference, jurisdiction_id, true);
    }

    private ResponseEntity<Object> singleRequest(String reference, String jurisdiction, boolean xml) {
        ResponseEntity<Object> error = validateJurisdiction(jurisdiction, xml);
        if (error != null) {
            return error;
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT r.reference, r.workflow_status, st.code, st.label_fr, r.description_public,
                       r.created_at, r.updated_at, r.address, ST_Y(r.location), ST_X(r.location),
                       m.storage_key
                FROM report r
                JOIN service_type st ON st.id = r.service_type_id
                LEFT JOIN LATERAL (
                    SELECT storage_key FROM media_asset
                    WHERE report_id = r.id AND moderation_status = 'APPROVED'
                    ORDER BY sort LIMIT 1
                ) m ON true
                WHERE r.publication_status = 'PUBLISHED' AND r.reference = :ref
                """).setParameter("ref", reference).getResultList();
        if (rows.isEmpty()) {
            return notFound(xml);
        }
        List<Map<String, Object>> out = List.of(toRequestDto(rows.getFirst()));
        return xml ? ResponseEntity.ok(requestsToXml(out)) : ResponseEntity.ok(out);
    }

    private Map<String, Object> toRequestDto(Object[] r) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("service_request_id", r[0]);
        String workflow = (String) r[1];
        dto.put("status", workflow.equals("OPEN") || workflow.equals("IN_PROGRESS") ? "open" : "closed");
        dto.put("service_code", r[2]);
        dto.put("service_name", r[3]);
        dto.put("description", r[4] == null ? "" : r[4]);
        dto.put("requested_datetime", isoUtc(r[5]));
        dto.put("updated_datetime", isoUtc(r[6]));
        dto.put("address", r[7] == null ? "" : r[7]);
        dto.put("lat", r[8]);
        dto.put("long", r[9]);
        dto.put("media_url", r[10] == null ? "" : emails.baseUrl() + "/media/" + r[10]);
        return dto;
    }

    private static String isoUtc(Object timestamp) {
        Instant instant = timestamp instanceof Instant i ? i
                : ((java.time.OffsetDateTime) timestamp).toInstant();
        return ISO.format(instant.atOffset(ZoneOffset.UTC));
    }

    private static String requestsToXml(List<Map<String, Object>> requests) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<service_requests>");
        for (Map<String, Object> r : requests) {
            xml.append("<request>");
            appendXml(xml, r);
            xml.append("</request>");
        }
        xml.append("</service_requests>");
        return xml.toString();
    }

    private static void appendXml(StringBuilder xml, Map<String, Object> fields) {
        fields.forEach((k, v) -> xml.append('<').append(k).append('>')
                .append(escapeXml(v == null ? "" : String.valueOf(v)))
                .append("</").append(k).append('>'));
    }

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private ResponseEntity<Object> validateJurisdiction(String jurisdiction, boolean xml) {
        if (jurisdiction != null && !jurisdiction.equals(props.open311().jurisdictionId())) {
            return badRequest("jurisdiction_id provided was not found", xml);
        }
        return null;
    }

    private static ResponseEntity<Object> badRequest(String description, boolean xml) {
        if (xml) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_XML)
                    .body("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<errors><error><code>400</code><description>"
                            + escapeXml(description) + "</description></error></errors>");
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(List.of(Map.of("code", 400, "description", description)));
    }

    private static ResponseEntity<Object> notFound(boolean xml) {
        if (xml) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_XML)
                    .body("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<errors><error><code>404</code><description>service_request_id not found</description></error></errors>");
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(List.of(Map.of("code", 404, "description", "service_request_id not found")));
    }
}
