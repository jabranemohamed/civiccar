package tn.civiccare.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Point d'ingestion OTLP/HTTP même origine pour les traces navigateur, en proxy vers le
 * collector. Sécurité : signal traces uniquement, taille bornée, destination FIXE par
 * configuration (jamais d'URL fournie par le client), désactivé par défaut. Les données
 * navigateur sont non fiables : elles sont isolées par service.name côté SDK web et
 * peuvent être filtrées au collector. Une panne ici n'affecte jamais le métier (204).
 */
@RestController
@RequestMapping("/api/telemetry")
public class TelemetryProxyApi {

    private static final Logger log = LoggerFactory.getLogger(TelemetryProxyApi.class);
    private static final int MAX_BYTES = 512 * 1024;

    private final String collectorTracesEndpoint;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)).build();

    public TelemetryProxyApi(
            @Value("${civiccare.telemetry.browser-collector-endpoint:}") String collectorTracesEndpoint) {
        this.collectorTracesEndpoint = collectorTracesEndpoint;
    }

    @PostMapping("/traces")
    public ResponseEntity<Void> traces(@RequestBody byte[] body,
                                       @RequestHeader(value = "Content-Type",
                                               defaultValue = "application/json") String contentType) {
        if (collectorTracesEndpoint.isBlank() || body.length == 0 || body.length > MAX_BYTES) {
            return ResponseEntity.noContent().build();
        }
        if (!contentType.startsWith("application/json") && !contentType.startsWith("application/x-protobuf")) {
            return ResponseEntity.noContent().build();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(collectorTracesEndpoint))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", contentType)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(e -> {
                        log.debug("Export télémétrie navigateur indisponible : {}",
                                e.getClass().getSimpleName());
                        return null;
                    });
        } catch (Exception e) {
            log.debug("Proxy télémétrie : {}", e.getClass().getSimpleName());
        }
        return ResponseEntity.noContent().build();
    }
}
