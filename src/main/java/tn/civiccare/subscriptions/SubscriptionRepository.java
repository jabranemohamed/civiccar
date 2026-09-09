package tn.civiccare.subscriptions;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    Optional<Subscription> findByReportIdAndEmail(UUID reportId, String email);

    List<Subscription> findByReportIdAndStatus(UUID reportId, Subscription.Status status);

    List<Subscription> findByReportId(UUID reportId);
}
