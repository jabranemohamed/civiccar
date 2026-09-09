package tn.civiccare.media;

import io.opentelemetry.api.common.Attributes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tn.civiccare.observability.Telemetry;
import tn.civiccare.observability.Telemetry.ValidationException;
import tn.civiccare.shared.AppProperties;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MediaService {

    public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {
        List<MediaAsset> findByReportIdOrderBySort(UUID reportId);

        List<MediaAsset> findByReportIdAndModerationStatusOrderBySort(UUID reportId, MediaAsset.ModerationStatus status);

        Optional<MediaAsset> findByStorageKey(String storageKey);

        Optional<MediaAsset> findByThumbKey(String thumbKey);

        long countByReportId(UUID reportId);
    }

    private final MediaAssetRepository assets;
    private final MediaStorage storage;
    private final AppProperties props;
    private final Telemetry telemetry;
    private final Clock clock;

    public MediaService(MediaAssetRepository assets, MediaStorage storage, AppProperties props,
                        Telemetry telemetry, Clock clock) {
        this.assets = assets;
        this.storage = storage;
        this.props = props;
        this.telemetry = telemetry;
        this.clock = clock;
    }

    /**
     * Valide et transforme une image (signature, dimensions, réencodage sans métadonnées).
     * Aucune écriture disque tant que le dossier n'est pas soumis : pas d'upload orphelin.
     */
    public ImageProcessor.Processed validateAndProcess(byte[] input) {
        return telemetry.span("media.process", Attributes.empty(), () -> {
            try {
                if (input.length > props.maxPhotoBytes()) {
                    throw new ValidationException("media.tooLarge");
                }
                return ImageProcessor.process(input);
            } catch (ValidationException e) {
                telemetry.mediaFailure(e.getMessage());
                throw e;
            }
        });
    }

    /** Persiste les dérivés d'une image déjà validée, dans la transaction du dossier. */
    @Transactional(propagation = Propagation.MANDATORY)
    public MediaAsset attach(UUID reportId, ImageProcessor.Processed processed, int sort) {
        if (assets.countByReportId(reportId) >= props.maxPhotosPerReport()) {
            throw new ValidationException("media.tooMany");
        }
        UUID id = UUID.randomUUID();
        String key = id + ".jpg";
        String thumbKey = id + "_thumb.jpg";
        try {
            storage.put(key, processed.full());
            storage.put(thumbKey, processed.thumbnail());
        } catch (IOException e) {
            telemetry.mediaFailure("storage");
            throw new IllegalStateException("Stockage média indisponible", e);
        }
        MediaAsset asset = new MediaAsset(id, reportId, key, thumbKey, "image/jpeg",
                processed.full().length, processed.width(), processed.height(), sort, clock.instant());
        return assets.save(asset);
    }

    @Transactional(readOnly = true)
    public List<MediaAsset> approvedFor(UUID reportId) {
        return assets.findByReportIdAndModerationStatusOrderBySort(reportId, MediaAsset.ModerationStatus.APPROVED);
    }

    @Transactional(readOnly = true)
    public List<MediaAsset> allFor(UUID reportId) {
        return assets.findByReportIdOrderBySort(reportId);
    }

    @Transactional
    public void setModeration(UUID assetId, MediaAsset.ModerationStatus status) {
        assets.findById(assetId).ifPresent(a -> a.setModerationStatus(status));
    }

    /**
     * Résout un flux pour une clé (image ou miniature). Public uniquement si le média
     * est APPROVED ; le personnel authentifié voit aussi PENDING/REMOVED.
     */
    @Transactional(readOnly = true)
    public Optional<StreamableMedia> resolve(String key, boolean staff) {
        Optional<MediaAsset> asset = assets.findByStorageKey(key)
                .or(() -> assets.findByThumbKey(key));
        if (asset.isEmpty()) {
            return Optional.empty();
        }
        boolean visible = staff || asset.get().getModerationStatus() == MediaAsset.ModerationStatus.APPROVED;
        if (!visible) {
            return Optional.empty();
        }
        try {
            InputStream in = storage.get(key);
            return Optional.of(new StreamableMedia(in, asset.get().getContentType()));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public record StreamableMedia(InputStream stream, String contentType) {
    }
}
