package tn.civiccare.api;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tn.civiccare.AbstractIntegrationTest;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Routage de livraison SPA : les routes UI connues servent index.html (liens profonds,
 * liens e-mail, refresh), et RIEN d'autre ne tombe dans le repli — une API inconnue
 * répond en JSON (jamais la page HTML de la SPA), /media et /actuator gardent leur
 * sémantique. Le test vérifie le routage, pas le bundle Angular : un index.html
 * sentinelle est posé sur le classpath de test si le build frontend n'a pas tourné.
 */
@AutoConfigureMockMvc
class SpaRoutingIT extends AbstractIntegrationTest {

    private static final String SENTINEL = "<!doctype html><html><body>civiccare-spa</body></html>";

    @Autowired
    MockMvc mvc;

    @BeforeAll
    static void ensureIndexHtmlOnTestClasspath() throws Exception {
        Path staticDir = Path.of(SpaRoutingIT.class.getResource("/").toURI()).resolve("static");
        Files.createDirectories(staticDir);
        Path index = staticDir.resolve("index.html");
        if (Files.notExists(index)) {
            Files.writeString(index, SENTINEL);
        }
    }

    @Test
    void knownUiRoutesServeIndexHtml() throws Exception {
        for (String route : new String[] {
                "/", "/report", "/requests/000001-2026", "/following", "/info", "/info/about",
                "/contact", "/s/confirm/jeton-test", "/s/unsubscribe/jeton-test", "/login",
                "/admin", "/admin/reports", "/admin/report/8b2d30d0-9951-44c5-b778-7d4d38586e71"}) {
            mvc.perform(get(route))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("text/html"));
        }
    }

    @Test
    void unknownApiPathIsJsonNotHtml() throws Exception {
        mvc.perform(get("/api/v1/nexiste-pas"))
                .andExpect(status().isNotFound())
                .andExpect(result -> {
                    String type = String.valueOf(result.getResponse().getContentType());
                    org.assertj.core.api.Assertions.assertThat(type).doesNotContain("text/html");
                });
        mvc.perform(get("/api/nexiste/pas/du/tout"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownUiPathIsNotServedAsHtmlFallback() throws Exception {
        // Pas de fourre-tout : un chemin hors liste blanche ne sert pas la SPA
        mvc.perform(get("/chemin/inconnu"))
                .andExpect(status().isNotFound());
    }

    @Test
    void mediaAndActuatorKeepTheirSemantics() throws Exception {
        // Média inconnu : 404 du contrôleur média, pas la page SPA
        mvc.perform(get("/media/inexistant_thumb.jpg"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(result -> org.assertj.core.api.Assertions
                        .assertThat(String.valueOf(result.getResponse().getContentType()))
                        .contains("json").doesNotContain("html"));
    }
}
