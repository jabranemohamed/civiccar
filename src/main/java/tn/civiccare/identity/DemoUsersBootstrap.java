package tn.civiccare.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Bootstrap des comptes internes. Mots de passe par variables d'environnement ;
 * en dehors du profil production, un mot de passe local de développement est appliqué
 * par défaut (documenté dans le README, jamais utilisé en production : en production,
 * un compte sans variable d'environnement n'est pas créé).
 */
@Configuration
public class DemoUsersBootstrap {

    private static final Logger log = LoggerFactory.getLogger(DemoUsersBootstrap.class);
    private static final String DEV_FALLBACK = "demo1234!";

    private record Account(String username, String displayName, Role role, String departmentCode, String envVar) {
    }

    private static final List<Account> ACCOUNTS = List.of(
            new Account("admin", "Administratrice démo", Role.ADMIN, null, "ADMIN_BOOTSTRAP_PASSWORD"),
            new Account("moderator", "Modérateur démo", Role.MODERATOR, null, "MODERATOR_BOOTSTRAP_PASSWORD"),
            new Account("agent.proprete", "Agent démo — Propreté", Role.AGENT, "DEMO_CLEANLINESS", "AGENT_BOOTSTRAP_PASSWORD"),
            new Account("agent.voirie", "Agent démo — Voirie", Role.AGENT, "DEMO_ROADS", "AGENT_BOOTSTRAP_PASSWORD"),
            new Account("agent.eclairage", "Agent démo — Éclairage", Role.AGENT, "DEMO_LIGHTING", "AGENT_BOOTSTRAP_PASSWORD"),
            new Account("agent.espaces", "Agent démo — Espaces publics", Role.AGENT, "DEMO_PUBLIC_SPACES", "AGENT_BOOTSTRAP_PASSWORD"));

    @Bean
    ApplicationRunner bootstrapUsers(StaffUserRepository users, DepartmentRepository departments,
                                     PasswordEncoder encoder, Environment env, Clock clock,
                                     org.springframework.transaction.PlatformTransactionManager txManager,
                                     @Value("${spring.profiles.active:}") String activeProfiles) {
        return args -> new TransactionTemplate(txManager).executeWithoutResult(tx -> {
            boolean production = List.of(env.getActiveProfiles()).contains("production");
            for (Account account : ACCOUNTS) {
                if (users.findByUsername(account.username()).isPresent()) {
                    continue;
                }
                String password = env.getProperty(account.envVar());
                if (password == null || password.isBlank()) {
                    if (production) {
                        log.warn("Compte {} non créé : variable {} absente en production.",
                                account.username(), account.envVar());
                        continue;
                    }
                    password = DEV_FALLBACK;
                }
                StaffUser user = new StaffUser(UUID.randomUUID(), account.username(),
                        encoder.encode(password), account.displayName(), clock.instant());
                user.getRoles().add(account.role());
                if (account.departmentCode() != null) {
                    departments.findByCode(account.departmentCode())
                            .ifPresent(d -> user.getDepartments().add(d));
                }
                users.save(user);
                log.info("Compte interne initialisé : {}", account.username());
            }
        });
    }
}
