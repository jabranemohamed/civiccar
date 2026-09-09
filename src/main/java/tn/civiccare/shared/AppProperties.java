package tn.civiccare.shared;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;
import java.util.List;

/**
 * Paramètres applicatifs de CivicCare Tunis. Tous les défauts ciblent la ville de Tunis (TN).
 */
@ConfigurationProperties(prefix = "civiccare")
public record AppProperties(
        /** Fuseau d'affichage. Stockage toujours en UTC. */
        String timezone,
        /** Code pays ISO pour géocodage et téléphonie. */
        String countryCode,
        /** Région par défaut pour la normalisation des numéros de téléphone. */
        String phoneDefaultRegion,
        /** Rayon (mètres) de recherche des doublons candidats. */
        int duplicateRadiusMeters,
        /** Jours après clôture avant archivage. Choix produit configurable, pas une règle légale. */
        int archiveAfterDays,
        /** Jours après clôture avant purge des données personnelles. */
        int purgeAfterDays,
        /** Nombre maximal de photos par signalement. */
        int maxPhotosPerReport,
        /** Taille maximale d'une image en octets. */
        long maxPhotoBytes,
        Geo geo,
        Map map,
        Emergency emergency,
        Demo demo,
        Open311 open311
) {
    public ZoneId zoneId() {
        return ZoneId.of(timezone);
    }

    /**
     * Périmètre géographique et géocodage.
     * boundarySource: DEMO (polygone de démonstration) ou CONFIGURED (contour vérifié chargé en base).
     * boundaryValidated doit être true pour démarrer en profil production.
     */
    public record Geo(String boundarySource, boolean boundaryValidated,
                      String geocoderMode, String geocoderBaseUrl, String geocoderEmail) {
    }

    /** Fournisseur de tuiles configuré séparément du moteur MapLibre. */
    public record Map(String tileUrl, String tileAttribution,
                      double centerLat, double centerLon, double initialZoom) {
    }

    /**
     * Bandeau urgences. Si verified=false, l'interface affiche une consigne générique sans numéro.
     * Les numéros ne sont affichés que si une source officielle tunisienne et une date sont renseignées.
     */
    public record Emergency(boolean verified, String source, String verifiedOn, List<Number> numbers) {
        public record Number(String labelKey, String value) {
        }
    }

    /** Données de démonstration. Jamais activées en production. */
    public record Demo(boolean seedEnabled) {
    }

    /** Juridiction Open311 applicative (identifiant de démonstration, non officiel). */
    public record Open311(String jurisdictionId, int maxDateRangeDays, int pageSize) {
    }
}
