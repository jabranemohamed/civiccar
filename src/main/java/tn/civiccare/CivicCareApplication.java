package tn.civiccare;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.server.PWA;
import com.vaadin.flow.theme.Theme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * CivicCare Tunis — plateforme de signalement citoyen (application de démonstration).
 * Monolithe modulaire : modules par packages (reports, catalog, geo, media, subscriptions,
 * notifications, moderation, identity, administration, content, open311, observability, shared).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@Theme("civiccare")
@PWA(name = "CivicCare Tunis", shortName = "CivicCare")
public class CivicCareApplication implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(CivicCareApplication.class, args);
    }
}
