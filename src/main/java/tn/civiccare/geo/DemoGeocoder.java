package tn.civiccare.geo;

import tn.civiccare.catalog.CatalogService;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Géocodeur local de démonstration : jeu déterministe de lieux publics de la ville de Tunis,
 * noms français et arabes, aucun appel réseau. Les résultats sont identifiés comme données
 * de démonstration et ne prouvent pas la qualité d'un fournisseur externe.
 * Coordonnées choisies dans le polygone OSM de la ville de Tunis (vérifiées par test).
 */
public class DemoGeocoder implements Geocoder {

    private record Place(String fr, String ar, double lon, double lat) {
    }

    private static final List<Place> PLACES = List.of(
            new Place("Avenue Habib Bourguiba, Tunis", "شارع الحبيب بورقيبة، تونس", 10.1815, 36.7995),
            new Place("Médina de Tunis", "المدينة العتيقة بتونس", 10.1712, 36.7981),
            new Place("Bab El Bhar, Tunis", "باب البحر، تونس", 10.1770, 36.7986),
            new Place("Place de la Kasbah, Tunis", "ساحة القصبة، تونس", 10.1660, 36.7969),
            new Place("Place Barcelone, Tunis", "ساحة برشلونة، تونس", 10.1826, 36.7952),
            new Place("Parc du Belvédère, Tunis", "منتزه البلفيدير، تونس", 10.1723, 36.8228),
            new Place("Bab Souika, Tunis", "باب سويقة، تونس", 10.1667, 36.8055),
            new Place("Avenue Mohamed V, Tunis", "شارع محمد الخامس، تونس", 10.1866, 36.8058),
            new Place("El Menzah, Tunis", "المنزه، تونس", 10.1719, 36.8398),
            new Place("El Omrane, Tunis", "العمران، تونس", 10.1580, 36.8194),
            new Place("Montfleury, Tunis", "مونفلوري، تونس", 10.1786, 36.7830),
            new Place("El Kabaria, Tunis", "الكبارية، تونس", 10.1743, 36.7568),
            new Place("Les Berges du Lac, Tunis", "ضفاف البحيرة، تونس", 10.2331, 36.8320),
            new Place("Cité El Khadra, Tunis", "حي الخضراء، تونس", 10.1976, 36.8266));

    @Override
    public List<GeocodeResult> search(String query, Locale locale) {
        String q = CatalogService.normalize(query);
        if (q.isBlank()) {
            return List.of();
        }
        boolean ar = "ar".equals(locale.getLanguage());
        return PLACES.stream()
                .filter(p -> CatalogService.normalize(p.fr()).contains(q)
                        || CatalogService.normalize(p.ar()).contains(q))
                .map(p -> new GeocodeResult(ar ? p.ar() : p.fr(), p.lon(), p.lat()))
                .toList();
    }

    @Override
    public Optional<GeocodeResult> reverse(double longitude, double latitude, Locale locale) {
        boolean ar = "ar".equals(locale.getLanguage());
        Place nearest = null;
        double best = Double.MAX_VALUE;
        for (Place p : PLACES) {
            double d = Math.hypot(p.lon() - longitude, p.lat() - latitude);
            if (d < best) {
                best = d;
                nearest = p;
            }
        }
        // ~0.02 degré ≈ 2 km : au-delà, pas d'adresse inventée — la position reste utilisable.
        if (nearest == null || best > 0.02) {
            return Optional.empty();
        }
        String prefix = ar ? "قرب " : "Près de ";
        return Optional.of(new GeocodeResult(prefix + (ar ? nearest.ar() : nearest.fr()), longitude, latitude));
    }

    @Override
    public boolean isDemo() {
        return true;
    }
}
