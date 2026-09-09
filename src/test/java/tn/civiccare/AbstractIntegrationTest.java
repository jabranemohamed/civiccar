package tn.civiccare;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Base des tests d'intégration : PostgreSQL/PostGIS réels via Testcontainers
 * (jamais H2 pour le spatial), horloge mutable injectable (A13), capteur d'e-mails.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(AbstractIntegrationTest.TestBeans.class)
public abstract class AbstractIntegrationTest {

    // Conteneur singleton : démarré une fois pour toute la JVM de test et jamais arrêté
    // entre les classes (l'extension @Testcontainers arrêterait le conteneur statique après
    // chaque classe alors que le contexte Spring, mis en cache, y reste connecté).
    // Image multi-arch PostGIS 18/3.6 (postgis/postgis:18-3.6 n'a pas d'arm64).
    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("imresamu/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    /** Horloge mutable pour tester archivage/purge sans attendre. */
    public static class MutableClock extends Clock {
        private volatile Instant instant = Instant.now();

        public void set(Instant instant) {
            this.instant = instant;
        }

        public void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        public void reset() {
            this.instant = Instant.now();
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    /** Capteur d'e-mails : aucun SMTP requis dans les tests. */
    public static class RecordingMailSender implements JavaMailSender {
        public final List<SimpleMailMessage> sent = new CopyOnWriteArrayList<>();
        public volatile boolean failNext = false;

        @Override
        public jakarta.mail.internet.MimeMessage createMimeMessage() {
            throw new UnsupportedOperationException();
        }

        @Override
        public jakarta.mail.internet.MimeMessage createMimeMessage(java.io.InputStream contentStream) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(jakarta.mail.internet.MimeMessage mimeMessage) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(jakarta.mail.internet.MimeMessage... mimeMessages) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(MimeMessagePreparator mimeMessagePreparator) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(SimpleMailMessage message) {
            if (failNext) {
                throw new org.springframework.mail.MailSendException("SMTP indisponible (simulation)");
            }
            sent.add(message);
        }

        @Override
        public void send(SimpleMailMessage... messages) {
            for (SimpleMailMessage m : messages) {
                send(m);
            }
        }
    }

    @TestConfiguration
    public static class TestBeans {
        @Bean
        @Primary
        public MutableClock mutableClock() {
            return new MutableClock();
        }

        @Bean
        @Primary
        public RecordingMailSender recordingMailSender() {
            return new RecordingMailSender();
        }
    }
}
