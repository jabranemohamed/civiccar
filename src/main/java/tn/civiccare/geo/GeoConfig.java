package tn.civiccare.geo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tn.civiccare.shared.AppProperties;

@Configuration
public class GeoConfig {

    /**
     * Le géocodeur de démonstration est le défaut : premier lancement sans service externe.
     * NOMINATIM exige une base URL explicite (instance auto-hébergée ou fournisseur autorisé).
     */
    @Bean
    public Geocoder geocoder(AppProperties props) {
        if ("NOMINATIM".equalsIgnoreCase(props.geo().geocoderMode())) {
            String baseUrl = props.geo().geocoderBaseUrl();
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new IllegalStateException(
                        "GEOCODER_MODE=NOMINATIM exige GEOCODER_BASE_URL (jamais le serveur public par défaut). "
                                + "Voir docs/tunis-configuration.md.");
            }
            // Le filtre pays du géocodeur suit le pays configuré (TN par défaut)
            return new NominatimGeocoder(baseUrl, props.geo().geocoderEmail(), props.countryCode());
        }
        return new DemoGeocoder();
    }
}
