package tn.civiccare.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tn.civiccare.AbstractIntegrationTest;
import tn.civiccare.catalog.CatalogService;
import tn.civiccare.reports.PublicationStatus;
import tn.civiccare.reports.Report;
import tn.civiccare.reports.ReportService;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Contrat de l'API SPA : session/CSRF (M06/M07), dépôt anonyme multipart idempotent (M04),
 * permissions par rôle et par équipe côté API (M07), conflit de version (M08),
 * non-divulgation des dossiers privés, erreurs Problem Details (jamais de HTML).
 */
@AutoConfigureMockMvc
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class SpaApiIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    CatalogService catalog;
    @Autowired
    ReportService reports;
    @Autowired
    org.springframework.transaction.PlatformTransactionManager txManager;
    @Autowired
    AuthApi authApi;

    private static final String DATA_JSON = """
            {"serviceTypeId":"%s","longitude":10.1815,"latitude":36.7995,
             "address":"Avenue Habib Bourguiba","description":"Dépôt via API SPA.",
             "fieldValues":{},"email":"spa-%s@example.com","phone":null,
             "consent":true,"idempotencyKey":"%s"}""";

    static byte[] jpeg(int w, int h) {
        try {
            var img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
            var out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "jpg", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private MockMultipartFile dataPart(String typeCode, String idempotencyKey) {
        UUID typeId = catalog.typeByCode(typeCode).orElseThrow().getId();
        String json = DATA_JSON.formatted(typeId, UUID.randomUUID(), idempotencyKey);
        return new MockMultipartFile("data", "data", "application/json", json.getBytes());
    }

    // ===== Config publique, CSRF cookie dès le premier GET =====
    // Ordonné en premier : le post-processeur de test csrf() substitue ensuite le
    // CsrfTokenRepository du filtre (TestCsrfTokenRepository, sans cookie) pour tout le
    // contexte — comportement de spring-security-test, pas de l'application. Le cookie
    // réel est aussi vérifié en E2E contre le serveur démarré.

    @Test
    @org.junit.jupiter.api.Order(1)
    void configIsPublicAndSetsXsrfCookie() throws Exception {
        mvc.perform(get("/api/v1/config"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(jsonPath("$.appName").value("CivicCare Tunis"))
                .andExpect(jsonPath("$.map.boundaryDemo").value(true))
                .andExpect(jsonPath("$.locales", hasSize(3)));
    }

    @Test
    void catalogExposesThreeLanguages() throws Exception {
        mvc.perform(get("/api/v1/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(15)))
                .andExpect(jsonPath("$[*].types[*]", hasSize(40)))
                .andExpect(jsonPath("$[0].labels.fr").isNotEmpty())
                .andExpect(jsonPath("$[0].labels.ar").isNotEmpty())
                .andExpect(jsonPath("$[0].labels.en").isNotEmpty());
    }

    // ===== CSRF sur les mutations (M07) =====

    @Test
    void mutationWithoutCsrfIsRejected() throws Exception {
        mvc.perform(multipart("/api/v1/reports").file(dataPart("TRASH_BIN_FULL", UUID.randomUUID().toString())))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/contact").contentType("application/json")
                        .content("{\"name\":\"X\",\"email\":\"x@example.com\",\"message\":\"m\",\"consent\":true}"))
                .andExpect(status().isForbidden());
        // Double-submit : un en-tête qui ne correspond pas au cookie émis est refusé.
        // (Cookie et en-tête forgés IDENTIQUES passeraient le contrôle : c'est le
        // modèle du double-submit — l'attaquant ne peut pas lire le cookie.)
        jakarta.servlet.http.Cookie issued = (jakarta.servlet.http.Cookie)
                mvc.perform(get("/api/v1/config")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        org.assertj.core.api.Assertions.assertThat(issued).isNotNull();
        mvc.perform(post("/api/v1/contact").contentType("application/json")
                        .cookie(issued)
                        .header("X-XSRF-TOKEN", "jeton-bidon")
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // ===== Dépôt anonyme multipart + idempotence (M04) =====

    @Test
    void anonymousReportCreationWorksAndIsIdempotent() throws Exception {
        String key = UUID.randomUUID().toString();
        MvcResult first = mvc.perform(multipart("/api/v1/reports")
                        .file(dataPart("TRASH_BIN_FULL", key))
                        .file(new MockMultipartFile("photos", "a.jpg", "image/jpeg", jpeg(400, 300)))
                        .with(realCsrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference", matchesPattern("\\d{6}-\\d{4}")))
                .andReturn();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d{6}-\\d{4}")
                .matcher(first.getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(m.find()).isTrue();
        String reference = m.group();

        // Double envoi avec la même clé -> même dossier
        mvc.perform(multipart("/api/v1/reports").file(dataPart("TRASH_BIN_FULL", key)).with(realCsrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value(reference));
    }

    @Test
    void validationErrorsAreProblemDetailsWithFieldCodes() throws Exception {
        UUID typeId = catalog.typeByCode("TRASH_BIN_FULL").orElseThrow().getId();
        // Hors périmètre (Ariana)
        String outside = """
                {"serviceTypeId":"%s","longitude":10.1937,"latitude":36.8665,
                 "description":"x","fieldValues":{},"email":"o@example.com",
                 "consent":true,"idempotencyKey":"%s"}""".formatted(typeId, UUID.randomUUID());
        mvc.perform(multipart("/api/v1/reports")
                        .file(new MockMultipartFile("data", "data", "application/json", outside.getBytes()))
                        .with(realCsrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("position.outside"))
                .andExpect(jsonPath("$.errors.position").value("position.outside"));
        // SVG refusé
        mvc.perform(multipart("/api/v1/reports")
                        .file(dataPart("TRASH_BIN_FULL", UUID.randomUUID().toString()))
                        .file(new MockMultipartFile("photos", "x.svg", "image/svg+xml", "<svg/>".getBytes()))
                        .with(realCsrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("media.format"));
    }

    // ===== Auth SPA : login JSON, me, logout (M06) =====

    @Test
    void loginLogoutFlowWithJsonResponses() throws Exception {
        // Fidèle à la SPA : cookie XSRF-TOKEN réel + en-tête X-XSRF-TOKEN, sans le
        // post-processeur csrf() (qui remplace le CsrfTokenRepository du contexte et
        // peut polluer l'exécution en suite complète — voir xsrfCookieIssuedOnFirstGet).
        // Anonyme : me -> authenticated:false (200, pas de redirection HTML)
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
        jakarta.servlet.http.Cookie xsrf = (jakarta.servlet.http.Cookie)
                mvc.perform(get("/api/v1/config")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        org.assertj.core.api.Assertions.assertThat(xsrf).as("cookie XSRF émis sur GET").isNotNull();
        // Mauvais mot de passe -> 401 problem+json
        mvc.perform(post("/api/v1/auth/login")
                        .cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .param("username", "agent.proprete").param("password", "faux"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        // Bon mot de passe -> 200 JSON
        mvc.perform(post("/api/v1/auth/login")
                        .cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .param("username", "agent.proprete").param("password", "test-agent-pass-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true));
        // Logout POST (CSRF requis) -> 204
        mvc.perform(post("/api/v1/auth/logout")
                        .cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isNoContent());
    }

    /**
     * Contrat du profil /me, testé au niveau bean avec un SecurityContext posé
     * manuellement : en suite complète, spring-security-test (post-processeurs user()
     * et csrf(), @WithMockUser via MockMvc) remplace des dépôts du contexte partagé et
     * l'authentification n'atteint plus le contrôleur — comportement non reproduit en
     * exécution isolée. Le flux HTTP réel (login -> me -> logout avec cookie de session)
     * est vérifié en E2E navigateur contre le serveur réel.
     */
    @Test
    void meReturnsProfileWhenAuthenticated() {
        var previous = org.springframework.security.core.context.SecurityContextHolder.getContext();
        try {
            var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    "agent.proprete", "n/a",
                    java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                            "ROLE_AGENT")));
            var ctx = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
            ctx.setAuthentication(auth);
            org.springframework.security.core.context.SecurityContextHolder.setContext(ctx);
            var body = authApi.me();
            org.assertj.core.api.Assertions.assertThat(body.get("authenticated")).isEqualTo(true);
            org.assertj.core.api.Assertions.assertThat(body.get("username")).isEqualTo("agent.proprete");
            org.assertj.core.api.Assertions.assertThat(String.valueOf(body.get("roles")))
                    .contains("AGENT");
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.setContext(previous);
        }
    }

    // ===== Admin : 401 anonyme, périmètre d'équipe, rôles (M07) =====

    @Test
    void adminApiRequiresAuthenticationWithProblemJson() throws Exception {
        mvc.perform(get("/api/v1/admin/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    private Report createCleanlinessReport() {
        return reports.create(new ReportService.CreateReportCommand(
                catalog.typeByCode("TRASH_BIN_FULL").orElseThrow().getId(), 10.1815, 36.7995,
                null, null, "API admin test.", Map.of(), "adm-" + UUID.randomUUID() + "@example.com",
                null, true, UUID.randomUUID().toString(), Locale.forLanguageTag("fr"), List.of()));
    }

    @Test
    void agentOfOtherDepartmentIsForbiddenAtApiLevel() throws Exception {
        Report report = createCleanlinessReport();
        // agent.voirie ne peut ni voir (404 sans divulgation) ni agir (403)
        mvc.perform(get("/api/v1/admin/reports/" + report.getId())
                        .with(user("agent.voirie").roles("AGENT")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/admin/reports/" + report.getId() + "/status").with(realCsrf())
                        .with(user("agent.voirie").roles("AGENT"))
                        .contentType("application/json")
                        .content("{\"target\":\"IN_PROGRESS\"}"))
                .andExpect(status().isForbidden());
        // agent.proprete (équipe du dossier) peut agir
        mvc.perform(post("/api/v1/admin/reports/" + report.getId() + "/status").with(realCsrf())
                        .with(user("agent.proprete").roles("AGENT"))
                        .contentType("application/json")
                        .content("{\"target\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void moderationEndpointsRequireModeratorRole() throws Exception {
        Report report = createCleanlinessReport();
        mvc.perform(post("/api/v1/admin/reports/" + report.getId() + "/publication").with(realCsrf())
                        .with(user("agent.proprete").roles("AGENT"))
                        .contentType("application/json")
                        .content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/reports/" + report.getId() + "/publication").with(realCsrf())
                        .with(user("moderator").roles("MODERATOR"))
                        .contentType("application/json")
                        .content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isOk());
        // Catalogue réservé ADMIN
        UUID typeId = catalog.typeByCode("BENCH_DIRTY").orElseThrow().getId();
        mvc.perform(patch("/api/v1/admin/catalog/types/" + typeId).with(realCsrf())
                        .with(user("moderator").roles("MODERATOR"))
                        .contentType("application/json").content("{\"active\":true}"))
                .andExpect(status().isForbidden());
    }

    // ===== Conflit de version (M08) =====

    @Test
    void staleVersionIsRejectedWith409WithoutOverwriting() throws Exception {
        Report report = createCleanlinessReport();
        long initialVersion = report.getVersion();
        // Un premier agent modifie (version avance)
        mvc.perform(post("/api/v1/admin/reports/" + report.getId() + "/status").with(realCsrf())
                        .with(user("agent.proprete").roles("AGENT"))
                        .contentType("application/json")
                        .content("{\"target\":\"IN_PROGRESS\",\"expectedVersion\":" + initialVersion + "}"))
                .andExpect(status().isOk());
        // Un second agent, resté sur l'ancienne version -> 409, rien d'écrasé
        mvc.perform(post("/api/v1/admin/reports/" + report.getId() + "/status").with(realCsrf())
                        .with(user("agent.proprete").roles("AGENT"))
                        .contentType("application/json")
                        .content("{\"target\":\"DONE_OR_ORDERED\",\"expectedVersion\":" + initialVersion + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("version.stale"));
    }

    // ===== Non-divulgation côté API publique (M07) =====

    @Test
    void unpublishedReportIsNotFoundViaPublicApi() throws Exception {
        Report hidden = createCleanlinessReport(); // PENDING_REVIEW
        mvc.perform(get("/api/v1/reports/" + hidden.getReference()))
                .andExpect(status().isNotFound());
        // et absent de la recherche
        mvc.perform(get("/api/v1/reports").param("text", hidden.getReference()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    // ===== Favoris : cookie cc_device posé et réutilisé (M09) =====

    @Test
    void bookmarksUseDeviceCookie() throws Exception {
        Report report = createCleanlinessReport();
        new org.springframework.transaction.support.TransactionTemplate(txManager)
                .executeWithoutResult(tx -> reports.byReference(report.getReference())
                        .orElseThrow().setPublicationStatus(PublicationStatus.PUBLISHED));

        MvcResult toggle = mvc.perform(post("/api/v1/bookmarks/" + report.getId() + "/toggle").with(realCsrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookmarked").value(true))
                .andExpect(cookie().exists("cc_device"))
                .andExpect(cookie().httpOnly("cc_device", true))
                .andReturn();
        String device = toggle.getResponse().getCookie("cc_device").getValue();

        mvc.perform(get("/api/v1/bookmarks").cookie(new jakarta.servlet.http.Cookie("cc_device", device)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].reference", hasItem(report.getReference())));
    }

    // ===== Export CSV =====

    @Test
    void csvExportIsProtectedAndFormatted() throws Exception {
        mvc.perform(get("/api/v1/admin/reports/export.csv"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/reports/export.csv")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(startsWith("reference;type;")));
    }

    // NB : « API inconnue jamais servie en HTML » est couvert par SpaRoutingIT
    // (nécessite le service statique SPA, ajouté après le retrait de Vaadin).

    /**
     * CSRF réel côté SPA : récupère le cookie XSRF-TOKEN émis par un GET, puis pose
     * cookie + en-tête X-XSRF-TOKEN sur la requête. On n'utilise PAS le
     * post-processeur csrf() de spring-security-test : il remplace le
     * CsrfTokenRepository du contexte partagé et empêche l'émission du cookie
     * pour tous les tests exécutés ensuite (ordre de suite non maîtrisé).
     */
    private org.springframework.test.web.servlet.request.RequestPostProcessor realCsrf() {
        return request -> {
            try {
                jakarta.servlet.http.Cookie xsrf = (jakarta.servlet.http.Cookie) mvc
                        .perform(get("/api/v1/config"))
                        .andReturn().getResponse().getCookie("XSRF-TOKEN");
                if (xsrf == null) {
                    throw new IllegalStateException("cookie XSRF-TOKEN non émis sur GET");
                }
                request.setCookies(appendCookie(request.getCookies(), xsrf));
                request.addHeader("X-XSRF-TOKEN", xsrf.getValue());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return request;
        };
    }

    private static jakarta.servlet.http.Cookie[] appendCookie(
            jakarta.servlet.http.Cookie[] existing, jakarta.servlet.http.Cookie extra) {
        if (existing == null) {
            return new jakarta.servlet.http.Cookie[] {extra};
        }
        jakarta.servlet.http.Cookie[] all = java.util.Arrays.copyOf(existing, existing.length + 1);
        all[existing.length] = extra;
        return all;
    }
}
