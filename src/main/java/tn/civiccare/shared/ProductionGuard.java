package tn.civiccare.shared;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Bloque le démarrage en profil production tant que le périmètre géographique
 * reste un périmètre de démonstration non validé (exigence A06 / section 6.2).
 */
@Configuration
@Profile("production")
public class ProductionGuard {

    public ProductionGuard(AppProperties props) {
        if (!props.geo().boundaryValidated()) {
            throw new IllegalStateException("""
                    Démarrage en production refusé : le périmètre municipal de Tunis n'a pas été validé.
                    Configurez GEO_BOUNDARY_SOURCE=CONFIGURED avec un contour vérifié (source et licence documentées)
                    puis GEO_BOUNDARY_VALIDATED=true. Voir docs/tunis-configuration.md.""");
        }
        if (props.demo().seedEnabled()) {
            throw new IllegalStateException(
                    "Démarrage en production refusé : le seed de démonstration est activé (DEMO_SEED doit être false).");
        }
    }
}
