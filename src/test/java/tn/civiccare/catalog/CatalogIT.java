package tn.civiccare.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tn.civiccare.AbstractIntegrationTest;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/** A02 : catalogue complet 15 familles / 40 types, recherche FR/AR, aides contextuelles. */
class CatalogIT extends AbstractIntegrationTest {

    @Autowired
    CatalogService catalog;

    @Test
    void catalogHas15FamiliesAnd40Types() {
        assertThat(catalog.allGroups()).hasSize(15);
        assertThat(catalog.activeTypes()).hasSize(40);
    }

    @Test
    void allTypesHaveFrenchAndArabicLabels() {
        for (ServiceType type : catalog.allTypes()) {
            assertThat(type.getLabelFr()).isNotBlank();
            assertThat(type.getLabelAr()).isNotBlank();
            assertThat(type.getGroup().getLabelFr()).isNotBlank();
            assertThat(type.getGroup().getLabelAr()).isNotBlank();
        }
    }

    @Test
    void englishLabelsAvailableWithFrenchFallback() {
        java.util.Locale en = java.util.Locale.forLanguageTag("en");
        ServiceType bin = catalog.typeByCode("TRASH_BIN_FULL").orElseThrow();
        assertThat(bin.label(en)).isEqualTo("Full litter bin");
        assertThat(bin.getGroup().label(en)).isEqualTo("Litter bins");
        // Aide contextuelle traduite pour le véhicule hors d'usage
        ServiceType vehicle = catalog.typeByCode("ROAD_ABANDONED_VEHICLE").orElseThrow();
        assertThat(vehicle.help(en)).contains("licence plate");
        // Recherche en anglais
        assertThat(catalog.searchTypes("litter", en))
                .anyMatch(t -> t.getCode().equals("TRASH_BIN_FULL"));
    }

    @Test
    void searchWorksInFrenchWithoutDiacritics() {
        // « eclairage » sans accent doit trouver l'éclairage public
        List<ServiceType> results = catalog.searchTypes("eclairage", Locale.forLanguageTag("fr"));
        assertThat(results).anyMatch(t -> t.getCode().startsWith("STREET_LIGHT"));
    }

    @Test
    void searchWorksInArabic() {
        List<ServiceType> results = catalog.searchTypes("مصباح", Locale.forLanguageTag("ar"));
        assertThat(results).anyMatch(t -> t.getCode().startsWith("STREET_LIGHT"));
    }

    @Test
    void abandonedVehicleHasContextualHelpAndPrivatePlateField() {
        ServiceType vehicle = catalog.typeByCode("ROAD_ABANDONED_VEHICLE").orElseThrow();
        assertThat(vehicle.help(Locale.forLanguageTag("fr"))).contains("plaque");
        assertThat(vehicle.help(Locale.forLanguageTag("ar"))).isNotBlank();
        FieldDefinition plate = vehicle.getFields().stream()
                .filter(f -> f.getCode().equals("VEHICLE_PLATE")).findFirst().orElseThrow();
        assertThat(plate.isPublicField()).as("la plaque ne doit jamais être publique").isFalse();
        assertThat(plate.isRequired()).isFalse();
    }

    @Test
    void posterTypesRequirePartyAndDisableStandardDescription() {
        for (String code : List.of("POSTER_FORBIDDEN_AREA", "POSTER_DAMAGED", "POSTER_OBSTRUCTING",
                "POSTER_POORLY_FIXED", "POSTER_NOT_REMOVED")) {
            ServiceType poster = catalog.typeByCode(code).orElseThrow();
            assertThat(poster.isStandardDescription())
                    .as("description standard absente pour " + code).isFalse();
            FieldDefinition party = poster.getFields().stream()
                    .filter(f -> f.getCode().equals("POLITICAL_PARTY")).findFirst().orElseThrow();
            assertThat(party.isRequired()).isTrue();
            assertThat(party.getOptions()).isNotEmpty();
            // Partis fictifs uniquement
            assertThat(party.getOptions()).allMatch(o ->
                    o.getLabelFr().contains("fictif") || o.getCode().equals("PARTY_OTHER"));
        }
    }

    @Test
    void everyTypeHasARoutingRuleToADemoDepartment() {
        for (ServiceType type : catalog.activeTypes()) {
            assertThat(catalog.routeFor(type.getId()))
                    .as("routage manquant pour " + type.getCode())
                    .isPresent();
        }
    }
}
