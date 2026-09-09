package tn.civiccare.reports;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    Optional<Report> findByReference(String reference);

    Optional<Report> findByIdempotencyKey(String idempotencyKey);

    @Query("select r from Report r where r.closedAt is not null and r.archivedAt is null and r.closedAt <= :threshold")
    List<Report> findArchivable(@Param("threshold") Instant threshold);

    @Query("select r from Report r where r.closedAt is not null and r.personalDataPurgedAt is null and r.closedAt <= :threshold")
    List<Report> findPurgeable(@Param("threshold") Instant threshold);

    long countByWorkflowStatusIn(List<WorkflowStatus> statuses);

    long countByPublicationStatus(PublicationStatus status);
}
