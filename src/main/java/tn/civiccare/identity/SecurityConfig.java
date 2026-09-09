package tn.civiccare.identity;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;
import tn.civiccare.api.SpaCsrfTokenRequestHandler;

/**
 * Sécurité en deux chaînes :
 * 1. /api/** : API de la SPA Angular — sessions Spring, CSRF cookie (XSRF-TOKEN lisible,
 *    en-tête X-XSRF-TOKEN), login/logout JSON, réponses 401/403 en Problem Details
 *    (jamais de redirection HTML pour une API).
 * 2. Le reste : ressources statiques de la SPA, médias et actuator — tout en permitAll
 *    (les routes profondes /admin/** servent index.html à l'anonyme ; la protection des
 *    données est assurée par la chaîne API, le guard Angular ne fait que l'ergonomie).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** Chaîne API pour la SPA. Les GET publics restent publics ; l'admin exige un rôle. */
    @Bean
    @org.springframework.core.annotation.Order(1)
    SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/**")
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        // Open311 : lecture publique JSON/XML pour clients non navigateurs
                        // Proxy télémétrie : POST OTLP émis par l'exporteur (fetch natif,
                        // pas HttpClient Angular, donc pas d'en-tête X-XSRF-TOKEN) ;
                        // borné côté serveur (taille, type, destination fixe), sans effet métier.
                        .ignoringRequestMatchers("/api/georeport/**", "/api/telemetry/**"))
                // Matérialise le cookie XSRF-TOKEN sur chaque réponse API (token différé sinon)
                .addFilterAfter(new OncePerRequestFilter() {
                    @Override
                    protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                                                    HttpServletResponse response,
                                                    jakarta.servlet.FilterChain filterChain)
                            throws jakarta.servlet.ServletException, java.io.IOException {
                        CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");
                        if (csrfToken != null) {
                            csrfToken.getToken();
                        }
                        filterChain.doFilter(request, response);
                    }
                }, BasicAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/admin/**")
                        .hasAnyRole("AGENT", "MODERATOR", "ADMIN")
                        .anyRequest().permitAll())
                .formLogin(form -> form
                        .loginProcessingUrl("/api/v1/auth/login")
                        .successHandler((request, response, authentication) -> {
                            response.setStatus(HttpServletResponse.SC_OK);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"authenticated\":true}");
                        })
                        .failureHandler((request, response, exception) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/problem+json");
                            response.getWriter().write(
                                    "{\"status\":401,\"title\":\"Unauthorized\",\"code\":\"credentials.invalid\"}");
                        }))
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT)))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/problem+json");
                            response.getWriter().write(
                                    "{\"status\":401,\"title\":\"Unauthorized\",\"code\":\"authentication.required\"}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/problem+json");
                            response.getWriter().write(
                                    "{\"status\":403,\"title\":\"Forbidden\",\"code\":\"forbidden\"}");
                        }));
        return http.build();
    }

    /** Chaîne statique : SPA, médias, actuator. Aucune session, aucun CSRF (GET seulement). */
    @Bean
    @org.springframework.core.annotation.Order(2)
    SecurityFilterChain staticFilterChain(HttpSecurity http) throws Exception {
        http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
