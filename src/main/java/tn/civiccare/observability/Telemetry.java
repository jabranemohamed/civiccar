package tn.civiccare.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Façade OpenTelemetry. Le SDK est fourni par l'agent Java (-javaagent) ; sans agent,
 * l'API retourne des no-op : une panne du collector n'empêche jamais le métier.
 * Attributs à cardinalité faible uniquement : jamais d'e-mail, de coordonnées, de
 * référence de dossier ni de texte libre.
 */
@Component
public class Telemetry {

    public static final AttributeKey<String> ATTR_CATEGORY = AttributeKey.stringKey("civiccare.category");
    public static final AttributeKey<String> ATTR_STATUS = AttributeKey.stringKey("civiccare.status");
    public static final AttributeKey<String> ATTR_RESULT = AttributeKey.stringKey("civiccare.result");
    public static final AttributeKey<String> ATTR_DEPARTMENT = AttributeKey.stringKey("civiccare.department");
    public static final AttributeKey<String> ATTR_KIND = AttributeKey.stringKey("civiccare.kind");

    private final Tracer tracer;
    private final LongCounter reportsCreated;
    private final LongCounter transitions;
    private final LongCounter mediaFailures;
    private final LongCounter notifications;
    private final LongHistogram usecaseDuration;
    private final AtomicLong outboxBacklog = new AtomicLong();
    private final AtomicLong activeReports = new AtomicLong();

    public Telemetry() {
        tracer = GlobalOpenTelemetry.getTracer("tn.civiccare", "1.0.0");
        Meter meter = GlobalOpenTelemetry.getMeter("tn.civiccare");
        reportsCreated = meter.counterBuilder("civiccare.reports.created")
                .setDescription("Signalements créés").setUnit("{report}").build();
        transitions = meter.counterBuilder("civiccare.reports.transitions")
                .setDescription("Transitions de statut").setUnit("{transition}").build();
        mediaFailures = meter.counterBuilder("civiccare.media.failures")
                .setDescription("Échecs de traitement média").setUnit("{failure}").build();
        notifications = meter.counterBuilder("civiccare.notifications.deliveries")
                .setDescription("Livraisons de notifications (résultat en attribut)").setUnit("{delivery}").build();
        usecaseDuration = meter.histogramBuilder("civiccare.usecase.duration")
                .setDescription("Durée des cas d'usage métier").setUnit("ms").ofLongs().build();
        meter.gaugeBuilder("civiccare.outbox.backlog").setUnit("{message}")
                .setDescription("Messages outbox en attente")
                .buildWithCallback(m -> m.record(outboxBacklog.get()));
        meter.gaugeBuilder("civiccare.reports.active").setUnit("{report}")
                .setDescription("Dossiers non clos")
                .buildWithCallback(m -> m.record(activeReports.get()));
    }

    /** Exécute un cas d'usage dans un span métier, avec durée et statut d'erreur. */
    public <T> T span(String name, Attributes attributes, Supplier<T> body) {
        Span span = tracer.spanBuilder(name).setSpanKind(SpanKind.INTERNAL)
                .setAllAttributes(attributes).startSpan();
        long start = System.nanoTime();
        try (Scope ignored = span.makeCurrent()) {
            return body.get();
        } catch (RuntimeException e) {
            // Refus de validation métier ≠ panne serveur : distingué par l'attribut result.
            boolean validation = e instanceof IllegalArgumentException || e instanceof ValidationException;
            span.setAttribute(ATTR_RESULT, validation ? "rejected" : "error");
            if (!validation) {
                span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            }
            throw e;
        } finally {
            span.end();
            usecaseDuration.record((System.nanoTime() - start) / 1_000_000,
                    Attributes.of(AttributeKey.stringKey("civiccare.usecase"), name));
        }
    }

    public void runSpan(String name, Attributes attributes, Runnable body) {
        span(name, attributes, () -> {
            body.run();
            return null;
        });
    }

    public Tracer tracer() {
        return tracer;
    }

    public void reportCreated(String categoryCode) {
        reportsCreated.add(1, Attributes.of(ATTR_CATEGORY, categoryCode));
    }

    public void transition(String from, String to) {
        transitions.add(1, Attributes.of(ATTR_STATUS, from + "->" + to));
    }

    public void mediaFailure(String reason) {
        mediaFailures.add(1, Attributes.of(ATTR_RESULT, reason));
    }

    public void notification(String kind, String result) {
        notifications.add(1, Attributes.of(ATTR_KIND, kind, ATTR_RESULT, result));
    }

    /** Jauges rafraîchies périodiquement par un job (pas de requête lourde à chaque scrape). */
    public void updateBacklog(long outbox, long active) {
        outboxBacklog.set(outbox);
        activeReports.set(active);
    }

    /** Exception de validation métier, distinguée d'une panne dans les traces. */
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }
}
