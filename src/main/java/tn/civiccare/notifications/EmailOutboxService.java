package tn.civiccare.notifications;

import io.opentelemetry.api.trace.Span;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Enregistrement d'e-mails dans l'outbox, dans la transaction métier appelante.
 * Le traceparent W3C courant est propagé dans le payload pour relier l'envoi
 * asynchrone à la trace d'origine (lien de span, pas de span longue durée).
 */
@Service
public class EmailOutboxService {

    private final OutboxRepository outbox;
    private final Clock clock;

    public EmailOutboxService(OutboxRepository outbox, Clock clock) {
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueEmail(String kind, String to, String subject, String body, String dedupKey) {
        if (dedupKey != null && outbox.existsByDedupKey(dedupKey)) {
            return;
        }
        Map<String, String> payload = new HashMap<>();
        payload.put("to", to);
        payload.put("subject", subject);
        payload.put("body", body);
        Span current = Span.current();
        if (current.getSpanContext().isValid()) {
            payload.put("traceId", current.getSpanContext().getTraceId());
            payload.put("spanId", current.getSpanContext().getSpanId());
        }
        outbox.save(new OutboxMessage(UUID.randomUUID(), kind, payload, dedupKey, clock.instant()));
    }
}
