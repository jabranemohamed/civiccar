package tn.civiccare.identity;

import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import tn.civiccare.identity.ui.LoginView;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Chaîne dédiée aux API REST publiques (Open311 + API interne) : stateless,
     * lecture publique, CSRF désactivé uniquement sur ce périmètre.
     */
    @Bean
    @org.springframework.core.annotation.Order(1)
    SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(
                        org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /** Chaîne principale Vaadin : contrôle d'accès par vue, CSRF Vaadin conservé. */
    @Bean
    @org.springframework.core.annotation.Order(2)
    SecurityFilterChain vaadinFilterChain(HttpSecurity http) throws Exception {
        // Chemins publics explicites (défense en profondeur : les vues restent contrôlées
        // par le contrôle d'accès de navigation Vaadin et les services par @PreAuthorize).
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**", "/actuator/prometheus", "/media/**")
                .permitAll()
                .requestMatchers("/", "/report", "/requests/**", "/following", "/info", "/info/**",
                        "/contact", "/s/**", "/login")
                .permitAll());
        http.with(VaadinSecurityConfigurer.vaadin(), configurer -> configurer.loginView(LoginView.class));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
