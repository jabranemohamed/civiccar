package tn.civiccare.shared.i18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class TranslationProviderTest {

    private final TranslationProvider provider = new TranslationProvider();
    private final Locale jvmDefault = Locale.getDefault();

    @AfterEach
    void restoreDefault() {
        Locale.setDefault(jvmDefault);
    }

    @Test
    void frenchIsServedFromBaseBundleEvenWhenJvmDefaultIsEnglish() {
        // Régression : sans contrôle no-fallback, fr retombait sur messages_en
        // lorsque la locale par défaut de la JVM était l'anglais.
        Locale.setDefault(Locale.forLanguageTag("en"));
        assertThat(provider.getTranslation("nav.report", Locale.forLanguageTag("fr")))
                .isEqualTo("Signaler un problème");
    }

    @Test
    void threeLanguagesResolveIndependently() {
        assertThat(provider.getTranslation("nav.report", Locale.forLanguageTag("fr")))
                .isEqualTo("Signaler un problème");
        assertThat(provider.getTranslation("nav.report", Locale.forLanguageTag("en")))
                .isEqualTo("Report a problem");
        assertThat(provider.getTranslation("nav.report", Locale.forLanguageTag("ar")))
                .isEqualTo("الإبلاغ عن مشكلة");
        // Locale non supportée -> français par défaut
        assertThat(provider.getTranslation("nav.report", Locale.forLanguageTag("de")))
                .isEqualTo("Signaler un problème");
    }
}
