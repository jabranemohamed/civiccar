package tn.civiccare.reports;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ReportContactRepository extends JpaRepository<ReportContact, UUID> {
}

interface StatusEventRepository extends JpaRepository<StatusEvent, UUID> {
    List<StatusEvent> findByReportIdOrderByCreatedAt(UUID reportId);
}

interface PublicUpdateRepository extends JpaRepository<PublicUpdate, UUID> {
    List<PublicUpdate> findByReportIdOrderByCreatedAt(UUID reportId);
}

interface InternalNoteRepository extends JpaRepository<InternalNote, UUID> {
    List<InternalNote> findByReportIdOrderByCreatedAt(UUID reportId);
}

interface DuplicateLinkRepository extends JpaRepository<DuplicateLink, UUID> {
    Optional<DuplicateLink> findByReportId(UUID reportId);
}
