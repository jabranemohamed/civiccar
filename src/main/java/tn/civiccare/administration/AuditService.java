package tn.civiccare.administration;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

    public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    }

    private final AuditEventRepository repository;
    private final Clock clock;

    public AuditService(AuditEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String actor, String action, String targetType, String targetId, Map<String, String> detail) {
        repository.save(new AuditEvent(UUID.randomUUID(), actor, action, targetType, targetId,
                detail, clock.instant()));
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> latest(int limit) {
        return repository.findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();
    }
}
