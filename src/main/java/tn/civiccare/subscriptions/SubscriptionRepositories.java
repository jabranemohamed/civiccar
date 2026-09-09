package tn.civiccare.subscriptions;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ActionTokenRepository extends JpaRepository<ActionToken, UUID> {
    Optional<ActionToken> findByTokenHash(String tokenHash);

    void deleteBySubscriptionId(UUID subscriptionId);
}

interface BrowserIdentityRepository extends JpaRepository<BrowserIdentity, UUID> {
    Optional<BrowserIdentity> findByTokenHash(String tokenHash);
}

interface BookmarkRepository extends JpaRepository<Bookmark, UUID> {
    Optional<Bookmark> findByBrowserIdentityIdAndReportId(UUID browserId, UUID reportId);

    List<Bookmark> findByBrowserIdentityIdOrderByCreatedAtDesc(UUID browserId);
}
