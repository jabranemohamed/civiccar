package tn.civiccare.api;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tn.civiccare.catalog.CatalogService;
import tn.civiccare.catalog.CategoryGroup;
import tn.civiccare.catalog.FieldDefinition;
import tn.civiccare.catalog.ServiceType;
import tn.civiccare.content.ContentPage;
import tn.civiccare.content.ContentService;
import tn.civiccare.geo.BoundaryService;
import tn.civiccare.geo.Geocoder;
import tn.civiccare.media.ImageProcessor;
import tn.civiccare.media.MediaService;
import tn.civiccare.notifications.EmailComposer;
import tn.civiccare.notifications.EmailOutboxService;
import tn.civiccare.observability.Telemetry.ValidationException;
import tn.civiccare.reports.PublicReportFacade;
import tn.civiccare.reports.Report;
import tn.civiccare.reports.ReportQueryService;
import tn.civiccare.reports.ReportQueryService.SearchCriteria;
import tn.civiccare.reports.ReportService;
import tn.civiccare.reports.WorkflowStatus;
import tn.civiccare.shared.AppProperties;
import tn.civiccare.shared.RateLimiter;
import tn.civiccare.shared.i18n.TranslationProvider;
import tn.civiccare.subscriptions.SubscriptionService;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * API publique de la SPA Angular. Mêmes services, validations et projections publiques
 * que l'ancienne UI Vaadin : aucune donnée privée (contacts, notes, jetons, médias non
 * approuvés) ne sort d'ici. Les mutations sont protégées par CSRF (cookie XSRF-TOKEN).
 */
@RestController
@RequestMapping("/api/v1")
public class PublicApi {

    public static final String DEVICE_COOKIE = "cc_device";

    private final AppProperties props;
    private final BoundaryService boundary;
    private final CatalogService catalog;
    private final ContentService content;
    private final ReportQueryService queries;
    private final PublicReportFacade facade;
    private final ReportService reports;
    private final MediaService media;
    private final Geocoder geocoder;
    private final SubscriptionService subscriptions;
    private final EmailOutboxService emailOutbox;
    private final EmailComposer emails;
    private final RateLimiter rateLimiter;
    private final String appName;

    public PublicApi(@org.springframework.beans.factory.annotation.Value(
                             "${civiccare.app-name:CivicCare Tunis}") String appName,
                     AppProperties props, BoundaryService boundary, CatalogService catalog,
                     ContentService content, ReportQueryService queries, PublicReportFacade facade,
                     ReportService reports, MediaService media, Geocoder geocoder,
                     SubscriptionService subscriptions, EmailOutboxService emailOutbox,
                     EmailComposer emails, RateLimiter rateLimiter) {
        this.props = props;
        this.boundary = boundary;
        this.catalog = catalog;
        this.content = content;
        this.queries = queries;
        this.facade = facade;
        this.reports = reports;
        this.media = media;
        this.geocoder = geocoder;
        this.subscriptions = subscriptions;
        this.emailOutbox = emailOutbox;
        this.emails = emails;
        this.rateLimiter = rateLimiter;
        this.appName = appName;
    }

    private static Locale locale(String lang) {
        return TranslationProvider.supported(Locale.forLanguageTag(lang == null ? "fr" : lang));
    }

    // ===== Configuration publique =====

    @GetMapping("/config")
    public Map<String, Object> config() {
        BoundaryService.BoundaryInfo info = boundary.activeBoundaryInfo();
        return Map.of(
                "appName", appName,
                "timezone", props.timezone(),
                "map", Map.of(
                        "tileUrl", props.map().tileUrl(),
                        "attribution", props.map().tileAttribution(),
                        "centerLat", props.map().centerLat(),
                        "centerLon", props.map().centerLon(),
                        "initialZoom", props.map().initialZoom(),
                        "boundaryGeoJson", boundary.activeBoundaryGeoJson(),
                        "boundaryDemo", info.demo()),
                "emergency", Map.of(
                        "verified", props.emergency().verified(),
                        "numbers", props.emergency().verified() ? props.emergency().numbers() : List.of()),
                "limits", Map.of(
                        "maxPhotos", props.maxPhotosPerReport(),
                        "maxPhotoBytes", props.maxPhotoBytes(),
                        "descriptionMax", ReportService.DESCRIPTION_MAX,
                        "duplicateRadiusMeters", props.duplicateRadiusMeters()),
                "geocoderDemo", geocoder.isDemo(),
                "locales", List.of("fr", "ar", "en"));
    }

    // ===== Catalogue (les trois langues pour bascule sans rechargement) =====

    @GetMapping("/catalog")
    public List<Map<String, Object>> catalog() {
        List<ServiceType> types = catalog.activeTypes();
        return catalog.allGroups().stream()
                .filter(CategoryGroup::isActive)
                .map(group -> Map.of(
                        "id", group.getId(),
                        "code", group.getCode(),
                        "labels", labels(group.getLabelFr(), group.getLabelAr(),
                                group.label(Locale.forLanguageTag("en"))),
                        "types", types.stream()
                                .filter(t -> t.getGroup().getId().equals(group.getId()))
                                .map(this::typeDto).toList()))
                .toList();
    }

    private Map<String, Object> typeDto(ServiceType type) {
        return Map.of(
                "id", type.getId(),
                "code", type.getCode(),
                "labels", labels(type.getLabelFr(), type.getLabelAr(),
                        type.label(Locale.forLanguageTag("en"))),
                "help", Map.of(
                        "fr", nullSafe(type.help(TranslationProvider.FRENCH)),
                        "ar", nullSafe(type.help(TranslationProvider.ARABIC)),
                        "en", nullSafe(type.help(TranslationProvider.ENGLISH))),
                "standardDescription", type.isStandardDescription(),
                "fields", type.getFields().stream().map(this::fieldDto).toList());
    }

    private Map<String, Object> fieldDto(FieldDefinition field) {
        return Map.of(
                "code", field.getCode(),
                "kind", field.getKind().name(),
                "required", field.isRequired(),
                "publicField", field.isPublicField(),
                "maxLen", field.getMaxLen(),
                "labels", labels(field.label(TranslationProvider.FRENCH),
                        field.label(TranslationProvider.ARABIC), field.label(TranslationProvider.ENGLISH)),
                "options", field.getOptions().stream()
                        .filter(o -> o.isActive())
                        .map(o -> Map.of("code", o.getCode(),
                                "labels", labels(o.label(TranslationProvider.FRENCH),
                                        o.label(TranslationProvider.ARABIC),
                                        o.label(TranslationProvider.ENGLISH))))
                        .toList());
    }

    private static Map<String, String> labels(String fr, String ar, String en) {
        return Map.of("fr", nullSafe(fr), "ar", nullSafe(ar), "en", nullSafe(en));
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    // ===== Contenus =====

    @GetMapping("/content/{slug}")
    public ResponseEntity<Map<String, Object>> contentPage(@PathVariable String slug) {
        return content.page(slug)
                .<ResponseEntity<Map<String, Object>>>map(page -> ResponseEntity.ok(Map.of(
                        "slug", page.getSlug(),
                        "title", labels(page.getTitleFr(), page.getTitleAr(),
                                page.title(TranslationProvider.ENGLISH)),
                        "body", labels(page.getBodyFr(), page.getBodyAr(),
                                page.body(TranslationProvider.ENGLISH)))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ===== Recherche publique =====

    public record SummaryDto(UUID id, String reference, Map<String, String> typeLabels,
                             String groupCode, String status, boolean archived, String address,
                             double longitude, double latitude, Instant createdAt, String thumbKey) {
        static SummaryDto of(ReportQueryService.PublicSummary s) {
            return new SummaryDto(s.id(), s.reference(),
                    labels(s.typeLabelFr(), s.typeLabelAr(),
                            s.typeLabelEn() != null ? s.typeLabelEn() : s.typeLabelFr()),
                    s.groupCode(), s.status().name(), s.archived(), s.address(),
                    s.longitude(), s.latitude(), s.createdAt(), s.thumbKey());
        }
    }

    @GetMapping("/reports")
    public Map<String, Object> searchReports(
            @RequestParam(required = false) String text,
            @RequestParam(required = false) UUID groupId,
            @RequestParam(required = false) UUID typeId,
            @RequestParam(required = false) WorkflowStatus status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "false") boolean sortAscending,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        SearchCriteria criteria = new SearchCriteria(text, groupId, typeId, status, from, to,
                includeArchived, sortAscending);
        long total = queries.count(criteria);
        List<SummaryDto> items = queries.search(criteria, Math.max(0, page) * Math.min(size, 50),
                        Math.min(size, 50)).stream()
                .map(SummaryDto::of).toList();
        return Map.of("total", total, "items", items);
    }

    @GetMapping("/reports/map")
    public List<Map<String, Object>> mapPoints(
            @RequestParam double west, @RequestParam double south,
            @RequestParam double east, @RequestParam double north,
            @RequestParam(required = false) String text,
            @RequestParam(required = false) UUID groupId,
            @RequestParam(required = false) WorkflowStatus status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        if (west >= east || south >= north || Math.abs(west) > 180 || Math.abs(north) > 90) {
            throw new IllegalArgumentException("bbox invalide");
        }
        SearchCriteria criteria = new SearchCriteria(text, groupId, null, status, from, to,
                includeArchived, false);
        return queries.mapPoints(west, south, east, north, criteria).stream()
                .<Map<String, Object>>map(p -> Map.of(
                        "id", p.id(), "reference", p.reference(), "group", p.groupCode(),
                        "status", p.status().name(), "lon", p.longitude(), "lat", p.latitude()))
                .toList();
    }

    // ===== Fiche publique =====

    @GetMapping("/reports/{reference}")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable String reference,
                                                      @RequestParam(defaultValue = "fr") String lang) {
        Locale locale = locale(lang);
        Optional<PublicReportFacade.PublicDetail> found = facade.byReference(reference, locale);
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        PublicReportFacade.PublicDetail d = found.get();
        Map<String, Object> dto = new java.util.LinkedHashMap<>();
        dto.put("id", d.id());
        dto.put("reference", d.reference());
        dto.put("typeLabel", d.typeLabel());
        dto.put("groupLabel", d.groupLabel());
        dto.put("status", d.status().name());
        dto.put("archived", d.archived());
        dto.put("address", nullSafe(d.address()));
        dto.put("addressDetails", nullSafe(d.addressDetails()));
        dto.put("description", nullSafe(d.description()));
        dto.put("position", Map.of("lon", d.longitude(), "lat", d.latitude()));
        dto.put("createdAt", d.createdAt());
        dto.put("publicFields", d.publicFieldLabels());
        dto.put("media", d.mediaKeys().stream()
                .map(k -> Map.of("full", "/media/" + k,
                        "thumb", "/media/" + k.replace(".jpg", "_thumb.jpg")))
                .toList());
        dto.put("timeline", d.timeline().stream()
                .<Map<String, Object>>map(t -> {
                    var entry = new java.util.LinkedHashMap<String, Object>();
                    entry.put("at", t.at());
                    entry.put("status", t.toStatus() == null ? null : t.toStatus().name());
                    entry.put("message", t.publicMessage());
                    return entry;
                })
                .toList());
        return ResponseEntity.ok(dto);
    }

    // ===== Doublons candidats =====

    @GetMapping("/reports/duplicates")
    public List<Map<String, Object>> duplicates(@RequestParam UUID typeId,
                                                @RequestParam double lon, @RequestParam double lat) {
        return queries.findDuplicateCandidates(typeId, lon, lat).stream()
                .<Map<String, Object>>map(c -> Map.of(
                        "summary", SummaryDto.of(c.summary()),
                        "distanceMeters", Math.round(c.distanceMeters())))
                .toList();
    }

    // ===== Dépôt (multipart : part "data" JSON + parts "photos") =====

    public record CreateReportRequest(UUID serviceTypeId, double longitude, double latitude,
                                      String address, String addressDetails, String description,
                                      Map<String, String> fieldValues, String email, String phone,
                                      boolean consent, String idempotencyKey) {
    }

    @PostMapping(value = "/reports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> createReport(
            @RequestPart("data") CreateReportRequest request,
            @RequestPart(value = "photos", required = false) List<MultipartFile> photos,
            @RequestHeader(value = "Accept-Language", defaultValue = "fr") String acceptLanguage,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        // Limitation de débit par appareil (cookie) + repli IP
        String clientKey = deviceBinding(httpRequest, httpResponse).browserId().toString();
        rateLimiter.check("report:" + clientKey, 5, 600);

        List<ImageProcessor.Processed> processed = List.of();
        if (photos != null && !photos.isEmpty()) {
            if (photos.size() > props.maxPhotosPerReport()) {
                throw new ValidationException("media.tooMany");
            }
            processed = photos.stream().map(f -> {
                try {
                    return media.validateAndProcess(f.getBytes());
                } catch (java.io.IOException e) {
                    throw new ValidationException("media.unreadable");
                }
            }).toList();
        }

        Report created = reports.create(new ReportService.CreateReportCommand(
                request.serviceTypeId(), request.longitude(), request.latitude(),
                request.address(), request.addressDetails(), request.description(),
                request.fieldValues(), request.email(), request.phone(), request.consent(),
                request.idempotencyKey(), locale(acceptLanguage), processed));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("reference", created.getReference()));
    }

    // ===== Géocodage (fournisseur configuré, jamais le serveur public par défaut) =====

    @GetMapping("/geocode")
    public List<Map<String, Object>> geocode(@RequestParam String q,
                                             @RequestParam(defaultValue = "fr") String lang) {
        if (q.isBlank() || q.length() > 200) {
            return List.of();
        }
        return geocoder.search(q, locale(lang)).stream()
                .<Map<String, Object>>map(r -> Map.of("label", r.label(), "lon", r.longitude(),
                        "lat", r.latitude()))
                .toList();
    }

    @GetMapping("/geocode/reverse")
    public ResponseEntity<Map<String, Object>> reverse(@RequestParam double lon, @RequestParam double lat,
                                                       @RequestParam(defaultValue = "fr") String lang) {
        return geocoder.reverse(lon, lat, locale(lang))
                .<ResponseEntity<Map<String, Object>>>map(r -> ResponseEntity.ok(
                        Map.of("label", r.label(), "lon", r.longitude(), "lat", r.latitude())))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    // ===== Périmètre (validation précoce côté client ; l'autorité reste le serveur au dépôt) =====

    @GetMapping("/boundary/check")
    public Map<String, Object> boundaryCheck(@RequestParam double lon, @RequestParam double lat) {
        return Map.of("inside", boundary.isInsideBoundary(lon, lat));
    }

    // ===== Favoris de cet appareil (cookie cc_device conservé, hachage en base) =====

    private SubscriptionService.BrowserBinding deviceBinding(HttpServletRequest request,
                                                             HttpServletResponse response) {
        String raw = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (DEVICE_COOKIE.equals(cookie.getName())) {
                    raw = cookie.getValue();
                }
            }
        }
        SubscriptionService.BrowserBinding binding = subscriptions.resolveBrowser(raw);
        if (binding.created()) {
            Cookie cookie = new Cookie(DEVICE_COOKIE, binding.rawToken());
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            cookie.setMaxAge(60 * 60 * 24 * 365);
            cookie.setAttribute("SameSite", "Lax");
            cookie.setSecure(request.isSecure());
            response.addCookie(cookie);
        }
        return binding;
    }

    @GetMapping("/bookmarks")
    public Map<String, Object> bookmarks(HttpServletRequest request, HttpServletResponse response) {
        var binding = deviceBinding(request, response);
        List<UUID> ids = subscriptions.bookmarkedReportIds(binding.browserId());
        return Map.of("items", queries.publicSummariesByIds(ids).stream().map(SummaryDto::of).toList());
    }

    @PostMapping("/bookmarks/{reportId}/toggle")
    public Map<String, Object> toggleBookmark(@PathVariable UUID reportId,
                                              HttpServletRequest request, HttpServletResponse response) {
        var binding = deviceBinding(request, response);
        // Le jeton d'appareil ne donne jamais accès à un dossier privé : le favori est
        // accepté mais seuls les dossiers publiés ressortent dans /bookmarks.
        return Map.of("bookmarked", subscriptions.toggleBookmark(binding.browserId(), reportId));
    }

    @GetMapping("/bookmarks/{reportId}")
    public Map<String, Object> isBookmarked(@PathVariable UUID reportId,
                                            HttpServletRequest request, HttpServletResponse response) {
        var binding = deviceBinding(request, response);
        return Map.of("bookmarked", subscriptions.isBookmarked(binding.browserId(), reportId));
    }

    // ===== Abonnements e-mail =====

    public record SubscribeRequest(UUID reportId, String reference, String email, boolean consent) {
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<Void> subscribe(@RequestBody SubscribeRequest request,
                                          @RequestHeader(value = "Accept-Language", defaultValue = "fr") String lang,
                                          HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        if (!request.consent()) {
            throw new ValidationException("consent.required");
        }
        if (request.email() == null || !request.email().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")) {
            throw new ValidationException("email.invalid");
        }
        rateLimiter.check("subscribe:" + deviceBinding(httpRequest, httpResponse).browserId(), 5, 3600);
        subscriptions.requestSubscriptionWithEmail(request.reportId(), request.reference(),
                request.email().trim(), locale(lang), emailOutbox, emails);
        // 202 dans tous les cas : l'état d'abonnement n'est pas divulgué
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/subscriptions/confirm/{token}")
    public Map<String, Object> confirm(@PathVariable String token) {
        return Map.of("ok", subscriptions.confirm(token));
    }

    @PostMapping("/subscriptions/unsubscribe/{token}")
    public Map<String, Object> unsubscribe(@PathVariable String token) {
        return Map.of("ok", subscriptions.unsubscribe(token));
    }

    // ===== Contact =====

    public record ContactRequest(String name, String email, String message, boolean copyRequested,
                                 boolean consent, String website) {
    }

    @PostMapping("/contact")
    public ResponseEntity<Void> contact(@RequestBody ContactRequest request,
                                        @RequestHeader(value = "Accept-Language", defaultValue = "fr") String lang,
                                        HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String clientKey = deviceBinding(httpRequest, httpResponse).browserId().toString();
        content.submitContact(request.name(), request.email(), request.message(),
                request.copyRequested(), request.consent(), request.website(), clientKey, locale(lang));
        return ResponseEntity.accepted().build();
    }
}
