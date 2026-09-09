package tn.civiccare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * CivicCare Tunis — plateforme de signalement citoyen (application de démonstration).
 * Monolithe modulaire : modules par packages (reports, catalog, geo, media, subscriptions,
 * notifications, moderation, identity, administration, content, open311, observability, shared).
 * Le frontend est une SPA Angular servie en ressources statiques (voir SpaController).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class CivicCareApplication {

    public static void main(String[] args) {
        SpringApplication.run(CivicCareApplication.class, args);
    }
}
