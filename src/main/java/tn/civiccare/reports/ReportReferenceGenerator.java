package tn.civiccare.reports;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import tn.civiccare.shared.AppProperties;

import java.time.Clock;
import java.time.ZonedDateTime;

/**
 * Référence humaine unique de type 000123-2026, issue d'une séquence PostgreSQL
 * transactionnellement sûre (jamais MAX(id)+1). Les trous de séquence sont acceptés.
 */
@Component
public class ReportReferenceGenerator {

    @PersistenceContext
    private EntityManager em;

    private final Clock clock;
    private final AppProperties props;

    public ReportReferenceGenerator(Clock clock, AppProperties props) {
        this.clock = clock;
        this.props = props;
    }

    public String next() {
        Number seq = (Number) em.createNativeQuery("SELECT nextval('report_reference_seq')").getSingleResult();
        int year = ZonedDateTime.now(clock.withZone(props.zoneId())).getYear();
        return "%06d-%d".formatted(seq.longValue(), year);
    }
}
