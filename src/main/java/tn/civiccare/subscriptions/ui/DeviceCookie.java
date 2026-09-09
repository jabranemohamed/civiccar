package tn.civiccare.subscriptions.ui;

import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinResponse;
import com.vaadin.flow.server.VaadinService;
import jakarta.servlet.http.Cookie;
import tn.civiccare.subscriptions.SubscriptionService;

import java.util.Optional;

/**
 * Cookie d'appareil pour les favoris : jeton aléatoire, HttpOnly, SameSite=Lax,
 * Secure derrière HTTPS. Le serveur ne stocke que le hachage du jeton.
 */
public final class DeviceCookie {

    public static final String NAME = "cc_device";

    private DeviceCookie() {
    }

    public static Optional<String> readToken() {
        VaadinRequest request = VaadinRequest.getCurrent();
        if (request == null || request.getCookies() == null) {
            return Optional.empty();
        }
        for (Cookie cookie : request.getCookies()) {
            if (NAME.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    /** Résout l'identité navigateur et pose le cookie si nécessaire. */
    public static SubscriptionService.BrowserBinding bind(SubscriptionService subscriptions) {
        SubscriptionService.BrowserBinding binding =
                subscriptions.resolveBrowser(readToken().orElse(null));
        if (binding.created()) {
            VaadinResponse response = VaadinResponse.getCurrent();
            if (response != null) {
                Cookie cookie = new Cookie(NAME, binding.rawToken());
                cookie.setPath("/");
                cookie.setHttpOnly(true);
                cookie.setMaxAge(60 * 60 * 24 * 365);
                cookie.setAttribute("SameSite", "Lax");
                if (VaadinService.getCurrentRequest() != null
                        && VaadinService.getCurrentRequest().isSecure()) {
                    cookie.setSecure(true);
                }
                response.addCookie(cookie);
            }
        }
        return binding;
    }
}
