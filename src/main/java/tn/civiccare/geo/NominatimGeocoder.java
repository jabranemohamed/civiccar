package tn.civiccare.geo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Adaptateur Nominatim pour une instance auto-hébergée ou un fournisseur dont les
 * conditions ont été vérifiées. JAMAIS configuré implicitement sur le serveur public
 * nominatim.openstreetmap.org : pas d'autocomplétion, limite globale de 1 req/s appliquée
 * ici par verrou, identification User-Agent requise, cache local des requêtes.
 * countrycodes=tn ne remplace pas la validation du polygone municipal.
 */
public class NominatimGeocoder implements Geocoder {

    private static final Logger log = LoggerFactory.getLogger(NominatimGeocoder.class);

    private final RestClient client;
    private final String countryCodes;
    private final ReentrantLock rateLock = new ReentrantLock();
    private volatile Instant lastRequest = Instant.EPOCH;
    private final Map<String, List<GeocodeResult>> cache = new ConcurrentHashMap<>();

    public NominatimGeocoder(String baseUrl, String contactEmail, String countryCode) {
        this.countryCodes = countryCode == null || countryCode.isBlank()
                ? "tn" : countryCode.toLowerCase(Locale.ROOT);
        var requestFactory = new org.springframework.http.client.JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", "CivicCare-Tunis/1.0 (" +
                        (contactEmail == null || contactEmail.isBlank() ? "demo" : contactEmail) + ")")
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<GeocodeResult> search(String query, Locale locale) {
        String cacheKey = locale.getLanguage() + "|" + query.trim().toLowerCase(Locale.ROOT);
        return cache.computeIfAbsent(cacheKey, k -> {
            try {
                throttle();
                List<Map<String, Object>> rows = client.get()
                        .uri(uri -> uri.path("/search")
                                .queryParam("q", query)
                                .queryParam("format", "jsonv2")
                                .queryParam("countrycodes", countryCodes)
                                .queryParam("accept-language", locale.getLanguage() + ",fr,ar")
                                .queryParam("limit", 6)
                                .build())
                        .retrieve()
                        .body(List.class);
                return rows == null ? List.of() : rows.stream()
                        .map(r -> new GeocodeResult((String) r.get("display_name"),
                                Double.parseDouble((String) r.get("lon")),
                                Double.parseDouble((String) r.get("lat"))))
                        .toList();
            } catch (Exception e) {
                log.warn("Géocodage indisponible ({}). La saisie manuelle reste possible.", e.getMessage());
                return List.of();
            }
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<GeocodeResult> reverse(double longitude, double latitude, Locale locale) {
        try {
            throttle();
            Map<String, Object> row = client.get()
                    .uri(uri -> uri.path("/reverse")
                            .queryParam("lon", longitude)
                            .queryParam("lat", latitude)
                            .queryParam("format", "jsonv2")
                            .queryParam("accept-language", locale.getLanguage() + ",fr,ar")
                            .build())
                    .retrieve()
                    .body(Map.class);
            if (row == null || row.get("display_name") == null) {
                return Optional.empty();
            }
            return Optional.of(new GeocodeResult((String) row.get("display_name"), longitude, latitude));
        } catch (Exception e) {
            log.warn("Géocodage inverse indisponible ({}).", e.getMessage());
            return Optional.empty();
        }
    }

    /** Contrôle de débit global : au plus 1 requête/seconde pour l'application entière. */
    private void throttle() throws InterruptedException {
        rateLock.lock();
        try {
            long since = Duration.between(lastRequest, Instant.now()).toMillis();
            if (since < 1100) {
                Thread.sleep(1100 - since);
            }
            lastRequest = Instant.now();
        } finally {
            rateLock.unlock();
        }
    }

    @Override
    public boolean isDemo() {
        return false;
    }
}
