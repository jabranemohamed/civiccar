package tn.civiccare.notifications;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxMessage, UUID> {

    @Query("select m from OutboxMessage m where m.status = 'PENDING' and m.nextAttemptAt <= :now order by m.nextAttemptAt")
    List<OutboxMessage> findDue(@Param("now") Instant now, org.springframework.data.domain.Limit limit);

    long countByStatus(OutboxMessage.Status status);

    boolean existsByDedupKey(String dedupKey);
}
