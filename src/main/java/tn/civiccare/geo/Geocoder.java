package tn.civiccare.geo;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Port de géocodage. Le moteur cartographique (MapLibre) n'est ni un fournisseur
 * d'adresses ni un fournisseur de tuiles : ce port est configuré séparément.
 */
public interface Geocoder {

    /** Recherche explicite d'adresse (pas d'autocomplétion sauf fournisseur l'autorisant). */
    List<GeocodeResult> search(String query, Locale locale);

    /** Géocodage inverse : peut ne pas retourner de rue/numéro ; la position reste utilisable. */
    Optional<GeocodeResult> reverse(double longitude, double latitude, Locale locale);

    /** true si les résultats proviennent d'un jeu local de démonstration. */
    boolean isDemo();

    record GeocodeResult(String label, double longitude, double latitude) {
    }
}
