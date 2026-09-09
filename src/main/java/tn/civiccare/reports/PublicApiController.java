package tn.civiccare.reports;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * API interne de lecture publique (complément à Open311) : points par emprise pour une
 * carte, détail public. Mêmes projections et visibilité que l'UI — jamais de donnée privée.
 * Les écritures (dépôt, favoris, abonnements) passent par l'UI Vaadin, pas par cette API.
 */
@RestController
@RequestMapping("/api/v1")
public class PublicApiController {

    private final ReportQueryService queries;
    private final PublicReportFacade facade;

    public PublicApiController(ReportQueryService queries, PublicReportFacade facade) {
        this.queries = queries;
        this.facade = facade;
    }

    /** Points publiés dans une emprise (plafond serveur : 500). */
    @GetMapping("/reports/map")
    public ResponseEntity<Object> mapPoints(@RequestParam double west, @RequestParam double south,
                                            @RequestParam double east, @RequestParam double north) {
        if (west >= east || south >= north || Math.abs(west) > 180 || Math.abs(north) > 90) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid bbox"));
        }
        List<ReportQueryService.MapPoint> points =
                queries.mapPoints(west, south, east, north, ReportQueryService.SearchCriteria.empty());
        return ResponseEntity.ok(points.stream().map(p -> Map.of(
                "reference", p.reference(),
                "group", p.groupCode(),
                "status", p.status().name(),
                "lon", p.longitude(),
                "lat", p.latitude())).toList());
    }

    /** Détail public par référence (404 pour inexistant OU non publié, sans distinction). */
    @GetMapping("/reports/{reference}")
    public ResponseEntity<Object> detail(@PathVariable String reference,
                                         @RequestParam(defaultValue = "fr") String lang) {
        Locale locale = tn.civiccare.shared.i18n.TranslationProvider.supported(Locale.forLanguageTag(lang));
        return facade.byReference(reference, locale)
                .<ResponseEntity<Object>>map(d -> ResponseEntity.ok(Map.of(
                        "reference", d.reference(),
                        "type", d.typeLabel(),
                        "group", d.groupLabel(),
                        "status", d.status().name(),
                        "archived", d.archived(),
                        "address", d.address() == null ? "" : d.address(),
                        "description", d.description() == null ? "" : d.description(),
                        "lon", d.longitude(),
                        "lat", d.latitude(),
                        "createdAt", d.createdAt().toString())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
