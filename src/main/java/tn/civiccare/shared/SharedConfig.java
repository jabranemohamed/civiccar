package tn.civiccare.shared;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.time.Clock;

@Configuration
@EnableJpaRepositories(basePackages = "tn.civiccare", considerNestedRepositories = true)
public class SharedConfig {

    /** Horloge injectable : les jobs d'archivage/purge et les tests s'appuient dessus. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
