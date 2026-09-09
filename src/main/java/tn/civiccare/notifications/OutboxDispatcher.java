package tn.civiccare.notifications;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tn.civiccare.observability.Telemetry;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Job d'envoi de l'outbox : reprise bornée avec backoff, une transaction par message.
 * Une panne SMTP ne bloque jamais la création des dossiers (exigence A16).
 */
@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
    private static final int MAX_ATTEMPTS = 8;

    private final OutboxRepository outbox;
    private final JavaMailSender mailSender;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Telemetry telemetry;
    private final String fromAddress;

    public OutboxDispatcher(OutboxRepository outbox, JavaMailSender mailSender,
                            org.springframework.transaction.PlatformTransactionManager txManager,
                            Clock clock, Telemetry telemetry,
                            @org.springframework.beans.factory.annotation.Value("${civiccare.mail.from:no-reply@civiccare-tunis.example}") String fromAddress) {
        this.outbox = outbox;
        this.mailSender = mailSender;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
        this.telemetry = telemetry;
        this.fromAddress = fromAddress;
    }

    @Scheduled(fixedDelayString = "${civiccare.outbox.poll-ms:5000}")
    public void dispatchDue() {
        List<UUID> due = tx.execute(status ->
                outbox.findDue(clock.instant(), Limit.of(20)).stream().map(OutboxMessage::getId).toList());
        if (due == null || due.isEmpty()) {
            return;
        }
        for (UUID id : due) {
            deliverOne(id);
        }
    }

    private void deliverOne(UUID id) {
        tx.executeWithoutResult(status -> {
            OutboxMessage msg = outbox.findById(id).orElse(null);
            if (msg == null || msg.getStatus() != OutboxMessage.Status.PENDING) {
                return;
            }
            Span span = telemetry.tracer().spanBuilder("notification.deliver")
                    .setSpanKind(SpanKind.INTERNAL)
                    .setAttribute(Telemetry.ATTR_KIND, msg.getKind())
                    .startSpan();
            // Lien vers la trace d'origine (pas de span maintenu ouvert entre transactions)
            String traceId = msg.getPayload().get("traceId");
            String spanId = msg.getPayload().get("spanId");
            if (traceId != null && spanId != null) {
                span.addLink(SpanContext.createFromRemoteParent(
                        traceId, spanId, TraceFlags.getSampled(), TraceState.getDefault()));
            }
            try (io.opentelemetry.context.Scope ignored = span.makeCurrent()) {
                SimpleMailMessage mail = new SimpleMailMessage();
                mail.setFrom(fromAddress);
                mail.setTo(msg.getPayload().get("to"));
                mail.setSubject(msg.getPayload().get("subject"));
                mail.setText(msg.getPayload().get("body"));
                mailSender.send(mail);
                msg.markSent(clock.instant());
                telemetry.notification(msg.getKind(), "sent");
            } catch (Exception e) {
                // Jamais le contenu du message dans les logs : classe d'erreur uniquement.
                log.warn("Envoi e-mail différé (outbox {}): {}", msg.getId(), e.getClass().getSimpleName());
                msg.markFailure(e.getClass().getSimpleName() + ": " + safeMessage(e), clock.instant(), MAX_ATTEMPTS);
                telemetry.notification(msg.getKind(),
                        msg.getStatus() == OutboxMessage.Status.FAILED ? "failed" : "retry");
                span.setAttribute(Telemetry.ATTR_RESULT, "error");
            } finally {
                span.end();
            }
        });
    }

    private static String safeMessage(Exception e) {
        String m = e.getMessage();
        return m == null ? "" : m.substring(0, Math.min(m.length(), 200));
    }
}
