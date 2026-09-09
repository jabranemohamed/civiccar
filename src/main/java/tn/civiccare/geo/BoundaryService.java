package tn.civiccare.geo;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.civiccare.shared.AppProperties;

import java.util.List;

/**
 * Vérification serveur du périmètre de la ville de Tunis.
 * ST_Covers : un point exactement sur la limite est considéré comme À L'INTÉRIEUR
 * (choix documenté et testé, exigence A06).
 */
@Service
public class BoundaryService {

    @PersistenceContext
    private EntityManager em;

    private final AppProperties props;

    public BoundaryService(AppProperties props) {
        this.props = props;
    }

    /**
     * Code du périmètre actif. DEMO (défaut) : polygone de démonstration approximant la
     * commune de Tunis, non officiel. OSM_AGGLO : agglomération OSM (relation 8896976),
     * qui n'est PAS la municipalité (inclut Carthage, La Marsa, Le Bardo). Toute autre
     * valeur est traitée comme un code de contour configuré et vérifié par l'exploitant.
     */
    public String activeBoundaryCode() {
        return switch (props.geo().boundarySource()) {
            case "DEMO" -> "DEMO_TUNIS";
            case "OSM_AGGLO" -> "OSM_TUNIS_AGGLO";
            default -> props.geo().boundarySource();
        };
    }

    @Transactional(readOnly = true)
    public boolean isInsideBoundary(double longitude, double latitude) {
        Object result = em.createNativeQuery("""
                        SELECT ST_Covers(b.geom, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326))
                        FROM boundary b WHERE b.code = :code
                        """)
                .setParameter("lon", longitude)
                .setParameter("lat", latitude)
                .setParameter("code", activeBoundaryCode())
                .getSingleResult();
        return Boolean.TRUE.equals(result);
    }

    @Transactional(readOnly = true)
    public BoundaryInfo activeBoundaryInfo() {
        Object[] row = (Object[]) em.createNativeQuery("""
                        SELECT b.code, b.name_fr, b.name_ar, b.source, b.license, b.is_demo
                        FROM boundary b WHERE b.code = :code
                        """)
                .setParameter("code", activeBoundaryCode())
                .getSingleResult();
        return new BoundaryInfo((String) row[0], (String) row[1], (String) row[2],
                (String) row[3], (String) row[4], (Boolean) row[5]);
    }

    /** GeoJSON du périmètre actif, pour affichage cartographique. */
    @Transactional(readOnly = true)
    public String activeBoundaryGeoJson() {
        List<?> rows = em.createNativeQuery(
                        "SELECT ST_AsGeoJSON(b.geom, 6) FROM boundary b WHERE b.code = :code")
                .setParameter("code", activeBoundaryCode())
                .getResultList();
        return rows.isEmpty() ? null : (String) rows.getFirst();
    }

    public record BoundaryInfo(String code, String nameFr, String nameAr,
                               String source, String license, boolean demo) {
    }
}
