package tn.civiccare.shared.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Repli SPA : sert index.html UNIQUEMENT pour les routes UI connues (liste blanche
 * explicite, jamais de fourre-tout). /api/**, /media/**, /actuator/** et les fichiers
 * statiques ne passent jamais par ici : une API inconnue répond 404, pas du HTML.
 * Les chemins publics historiques (liens e-mail /s/…, références /requests/…) restent
 * ainsi valides sans redirection.
 */
@RestController
public class SpaController {

    private static final ClassPathResource INDEX = new ClassPathResource("static/index.html");

    @GetMapping(value = {
            "/",
            "/report",
            "/requests/{reference}",
            "/following",
            "/info",
            "/info/{slug}",
            "/contact",
            "/s/confirm/{token}",
            "/s/unsubscribe/{token}",
            "/login",
            "/admin",
            "/admin/{page}",
            "/admin/report/{id}",
    }, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<ClassPathResource> index() {
        // index.html jamais mis en cache : les bundles fingerprintés, eux, le sont
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(MediaType.TEXT_HTML)
                .body(INDEX);
    }
}
