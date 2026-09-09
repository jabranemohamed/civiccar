package tn.civiccare.media;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Diffusion des médias dérivés. Le public ne voit que les photos approuvées ;
 * les URLs sont des clés opaques, pas des chemins système.
 */
@RestController
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @GetMapping("/media/{key}")
    public ResponseEntity<InputStreamResource> serve(@PathVariable String key) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean staff = auth != null && auth.isAuthenticated()
                && auth.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_AGENT") || a.getAuthority().equals("ROLE_MODERATOR")
                        || a.getAuthority().equals("ROLE_ADMIN"));
        return mediaService.resolve(key, staff)
                .map(media -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(media.contentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
                        .body(new InputStreamResource(media.stream())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
