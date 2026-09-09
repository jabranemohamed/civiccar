package tn.civiccare.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

/**
 * Gestion CSRF pour SPA (pattern documenté Spring Security) :
 * - rendu du token avec protection BREACH (Xor) et matérialisation immédiate du cookie
 *   (csrfToken.get()) pour que XSRF-TOKEN soit disponible dès le premier GET, y compris
 *   pour le dépôt citoyen anonyme ;
 * - résolution du token : valeur brute quand elle vient de l'en-tête X-XSRF-TOKEN
 *   (posée par Angular depuis le cookie), décodage Xor sinon.
 */
public final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       Supplier<CsrfToken> csrfToken) {
        xor.handle(request, response, csrfToken);
        // Force l'écriture du cookie XSRF-TOKEN sur chaque requête (token différé sinon)
        csrfToken.get();
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        String headerValue = request.getHeader(csrfToken.getHeaderName());
        return (StringUtils.hasText(headerValue) ? plain : xor)
                .resolveCsrfTokenValue(request, csrfToken);
    }
}
