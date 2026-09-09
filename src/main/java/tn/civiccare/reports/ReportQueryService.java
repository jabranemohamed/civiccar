package tn.civiccare.reports;

import io.opentelemetry.api.common.Attributes;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.civiccare.observability.Telemetry;
import tn.civiccare.shared.AppProperties;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Requêtes publiques : liste paginée, points de carte par emprise, candidats doublons.
 * Mêmes règles de visibilité partout : seuls les dossiers PUBLISHED sont retournés,
 * archives exclues sauf filtre explicite. Jamais de contact, note interne ni média
 * non approuvé dans les projections.
 */
@Service
public class ReportQueryService {

    /** Plafond explicite de points retournés pour une emprise de carte. */
    public static final int MAP_POINT_CAP = 500;

    @PersistenceContext
    private EntityManager em;

    private final Telemetry telemetry;
    private final AppProperties props;

    public ReportQueryService(Telemetry telemetry, AppProperties props) {
        this.telemetry = telemetry;
        this.props = props;
    }

    public record SearchCriteria(String text, UUID groupId, UUID typeId, WorkflowStatus status,
                                 Instant from, Instant to, boolean includeArchived,
                                 boolean sortAscending) {
        public static SearchCriteria empty() {
            return new SearchCriteria(null, null, null, null, null, null, false, false);
        }
    }

    public record PublicSummary(UUID id, String reference, String typeLabelFr, String typeLabelAr,
                                String typeLabelEn, String groupCode, WorkflowStatus status,
                                boolean archived, String address, double longitude, double latitude,
                                Instant createdAt, String thumbKey) {

        /** Libellé du type selon la locale ; l'anglais replie sur le français. */
        public String typeLabel(java.util.Locale locale) {
            return switch (locale.getLanguage()) {
                case "ar" -> typeLabelAr();
                case "en" -> typeLabelEn() != null ? typeLabelEn() : typeLabelFr();
                default -> typeLabelFr();
            };
        }
    }

    public record MapPoint(UUID id, String reference, String groupCode, WorkflowStatus status,
                           double longitude, double latitude) {
    }

    private static final String PUBLIC_VISIBILITY = " r.publication_status = 'PUBLISHED' ";

    /** Recherche paginée côté serveur, tri stable (created_at, id). */
    @Transactional(readOnly = true)
    public List<PublicSummary> search(SearchCriteria criteria, int offset, int limit) {
        return telemetry.span("report.search", Attributes.empty(), () -> {
            StringBuilder sql = new StringBuilder("""
                    SELECT r.id, r.reference, st.label_fr, st.label_ar, st.label_en, cg.code,
                           r.workflow_status, (r.archived_at IS NOT NULL) AS archived,
                           r.address, ST_X(r.location), ST_Y(r.location), r.created_at,
                           m.thumb_key
                    FROM report r
                    JOIN service_type st ON st.id = r.service_type_id
                    JOIN category_group cg ON cg.id = st.group_id
                    LEFT JOIN LATERAL (
                        SELECT thumb_key FROM media_asset
                        WHERE report_id = r.id AND moderation_status = 'APPROVED'
                        ORDER BY sort LIMIT 1
                    ) m ON true
                    WHERE
                    """);
            Map<String, Object> params = new HashMap<>();
            appendFilters(sql, params, criteria);
            sql.append(" ORDER BY r.created_at ").append(criteria.sortAscending() ? "ASC" : "DESC")
                    .append(", r.id LIMIT :limit OFFSET :offset");
            Query query = em.createNativeQuery(sql.toString());
            params.forEach(query::setParameter);
            query.setParameter("limit", Math.min(limit, 100));
            query.setParameter("offset", Math.max(offset, 0));
            @SuppressWarnings("unchecked")
            List<Object[]> rows = query.getResultList();
            return rows.stream().map(ReportQueryService::toSummary).toList();
        });
    }

    @Transactional(readOnly = true)
    public long count(SearchCriteria criteria) {
        StringBuilder sql = new StringBuilder("SELECT count(*) FROM report r WHERE ");
        Map<String, Object> params = new HashMap<>();
        appendFilters(sql, params, criteria);
        Query query = em.createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        return ((Number) query.getSingleResult()).longValue();
    }

    private void appendFilters(StringBuilder sql, Map<String, Object> params, SearchCriteria c) {
        sql.append(PUBLIC_VISIBILITY);
        if (!c.includeArchived()) {
            sql.append(" AND r.archived_at IS NULL");
        }
        if (c.text() != null && !c.text().isBlank()) {
            String text = c.text().trim();
            // Référence exacte ou recherche trgm insensible aux diacritiques
            sql.append(" AND (r.reference = :ref OR f_unaccent(coalesce(r.description_public,'')) ILIKE f_unaccent(:text))");
            params.put("ref", text);
            params.put("text", "%" + text + "%");
        }
        if (c.typeId() != null) {
            sql.append(" AND r.service_type_id = :typeId");
            params.put("typeId", c.typeId());
        } else if (c.groupId() != null) {
            sql.append(" AND r.service_type_id IN (SELECT id FROM service_type WHERE group_id = :groupId)");
            params.put("groupId", c.groupId());
        }
        if (c.status() != null) {
            sql.append(" AND r.workflow_status = :status");
            params.put("status", c.status().name());
        }
        if (c.from() != null) {
            sql.append(" AND r.created_at >= :fromTs");
            params.put("fromTs", c.from());
        }
        if (c.to() != null) {
            sql.append(" AND r.created_at < :toTs");
            params.put("toTs", c.to());
        }
    }

    /**
     * Points pour une emprise de carte : requête PostGIS indexée, plafond explicite.
     * Mêmes filtres métier et de visibilité que la liste.
     */
    @Transactional(readOnly = true)
    public List<MapPoint> mapPoints(double west, double south, double east, double north,
                                    SearchCriteria criteria) {
        StringBuilder sql = new StringBuilder("""
                SELECT r.id, r.reference, cg.code, r.workflow_status, ST_X(r.location), ST_Y(r.location)
                FROM report r
                JOIN service_type st ON st.id = r.service_type_id
                JOIN category_group cg ON cg.id = st.group_id
                WHERE r.location && ST_MakeEnvelope(:west, :south, :east, :north, 4326) AND
                """);
        Map<String, Object> params = new HashMap<>();
        appendFilters(sql, params, criteria);
        sql.append(" ORDER BY r.created_at DESC LIMIT :cap");
        Query query = em.createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        query.setParameter("west", west);
        query.setParameter("south", south);
        query.setParameter("east", east);
        query.setParameter("north", north);
        query.setParameter("cap", MAP_POINT_CAP);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream().map(r -> new MapPoint((UUID) r[0], (String) r[1], (String) r[2],
                WorkflowStatus.valueOf((String) r[3]),
                ((Number) r[4]).doubleValue(), ((Number) r[5]).doubleValue())).toList();
    }

    public record DuplicateCandidate(PublicSummary summary, double distanceMeters) {
    }

    /**
     * Candidats doublons : dossiers publics ACTIFS (non clos) de la même famille,
     * distance métrique via geography (jamais des degrés interprétés comme des mètres).
     * Aucun dossier privé n'est révélé par cette recherche.
     */
    @Transactional(readOnly = true)
    public List<DuplicateCandidate> findDuplicateCandidates(UUID serviceTypeId, double longitude, double latitude) {
        return telemetry.span("report.find_duplicates", Attributes.empty(), () -> {
            Query query = em.createNativeQuery("""
                    SELECT r.id, r.reference, st.label_fr, st.label_ar, st.label_en, cg.code,
                           r.workflow_status,
                           r.address, ST_X(r.location), ST_Y(r.location), r.created_at,
                           ST_Distance(r.location::geography, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography) AS dist
                    FROM report r
                    JOIN service_type st ON st.id = r.service_type_id
                    JOIN category_group cg ON cg.id = st.group_id
                    WHERE r.publication_status = 'PUBLISHED'
                      AND r.archived_at IS NULL
                      AND r.workflow_status IN ('OPEN', 'IN_PROGRESS')
                      AND st.group_id = (SELECT group_id FROM service_type WHERE id = :typeId)
                      AND ST_DWithin(r.location::geography,
                                     ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, :radius)
                    ORDER BY dist
                    LIMIT 5
                    """);
            query.setParameter("lon", longitude);
            query.setParameter("lat", latitude);
            query.setParameter("typeId", serviceTypeId);
            query.setParameter("radius", (double) props.duplicateRadiusMeters());
            @SuppressWarnings("unchecked")
            List<Object[]> rows = query.getResultList();
            List<DuplicateCandidate> out = new ArrayList<>();
            for (Object[] r : rows) {
                out.add(new DuplicateCandidate(new PublicSummary((UUID) r[0], (String) r[1], (String) r[2],
                        (String) r[3], (String) r[4], (String) r[5],
                        WorkflowStatus.valueOf((String) r[6]), false,
                        (String) r[7], ((Number) r[8]).doubleValue(), ((Number) r[9]).doubleValue(),
                        toInstant(r[10]), null), ((Number) r[11]).doubleValue()));
            }
            return out;
        });
    }

    /** Résumés publics par identifiants (favoris). Les dossiers non publiés sont exclus. */
    @Transactional(readOnly = true)
    public List<PublicSummary> publicSummariesByIds(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Query query = em.createNativeQuery("""
                SELECT r.id, r.reference, st.label_fr, st.label_ar, st.label_en, cg.code,
                       r.workflow_status, (r.archived_at IS NOT NULL), r.address,
                       ST_X(r.location), ST_Y(r.location), r.created_at, m.thumb_key
                FROM report r
                JOIN service_type st ON st.id = r.service_type_id
                JOIN category_group cg ON cg.id = st.group_id
                LEFT JOIN LATERAL (
                    SELECT thumb_key FROM media_asset
                    WHERE report_id = r.id AND moderation_status = 'APPROVED'
                    ORDER BY sort LIMIT 1
                ) m ON true
                WHERE r.publication_status = 'PUBLISHED' AND r.id IN (:ids)
                ORDER BY r.created_at DESC
                """);
        query.setParameter("ids", ids);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream().map(ReportQueryService::toSummary).toList();
    }

    /** Fiche publique par référence : uniquement si publiée. */
    @Transactional(readOnly = true)
    public Optional<PublicSummary> publicByReference(String reference) {
        Query query = em.createNativeQuery("""
                SELECT r.id, r.reference, st.label_fr, st.label_ar, st.label_en, cg.code,
                       r.workflow_status, (r.archived_at IS NOT NULL), r.address,
                       ST_X(r.location), ST_Y(r.location), r.created_at, NULL
                FROM report r
                JOIN service_type st ON st.id = r.service_type_id
                JOIN category_group cg ON cg.id = st.group_id
                WHERE r.publication_status = 'PUBLISHED' AND r.reference = :ref
                """);
        query.setParameter("ref", reference);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.of(toSummary(rows.getFirst()));
    }

    private static PublicSummary toSummary(Object[] r) {
        return new PublicSummary((UUID) r[0], (String) r[1], (String) r[2], (String) r[3], (String) r[4],
                (String) r[5], WorkflowStatus.valueOf((String) r[6]), Boolean.TRUE.equals(r[7]),
                (String) r[8], ((Number) r[9]).doubleValue(), ((Number) r[10]).doubleValue(),
                toInstant(r[11]), (String) r[12]);
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Instant i) {
            return i;
        }
        if (value instanceof java.time.OffsetDateTime odt) {
            return odt.toInstant();
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant();
        }
        throw new IllegalArgumentException("Type temporel inattendu : " + value.getClass());
    }
}
