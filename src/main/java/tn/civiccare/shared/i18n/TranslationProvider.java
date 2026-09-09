package tn.civiccare.shared.i18n;

import org.springframework.stereotype.Component;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Fournisseur i18n : français (défaut), arabe (RTL) et anglais.
 * Toutes les chaînes sont externalisées dans i18n/messages*.properties.
 */
@Component
public class TranslationProvider {

    public static final Locale FRENCH = Locale.forLanguageTag("fr");
    public static final Locale ARABIC = Locale.forLanguageTag("ar");
    public static final Locale ENGLISH = Locale.forLanguageTag("en");
    private static final List<Locale> LOCALES = List.of(FRENCH, ARABIC, ENGLISH);
    private static final String BUNDLE = "i18n.messages";

    public List<Locale> getProvidedLocales() {
        return LOCALES;
    }

    /** Réduit une locale quelconque à l'une des trois locales supportées (défaut : français). */
    public static Locale supported(Locale locale) {
        if (locale == null) {
            return FRENCH;
        }
        return switch (locale.getLanguage()) {
            case "ar" -> ARABIC;
            case "en" -> ENGLISH;
            default -> FRENCH;
        };
    }

    /**
     * Chargement SANS repli sur la locale par défaut de la JVM : sinon, la locale fr
     * (sans fichier messages_fr, le français étant le bundle de base) retomberait sur
     * messages_en lorsque la JVM tourne en anglais.
     */
    public static ResourceBundle bundle(Locale effective) {
        return ResourceBundle.getBundle(BUNDLE, effective,
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
    }

    public String getTranslation(String key, Locale locale, Object... params) {
        Locale effective = supported(locale);
        ResourceBundle bundle = bundle(effective);
        if (!bundle.containsKey(key)) {
            return "!" + key;
        }
        // Toujours passer par MessageFormat : les apostrophes doublées ('') des bundles
        // sont ainsi rendues correctement même sans paramètre.
        return new MessageFormat(bundle.getString(key), effective).format(params);
    }

    public static boolean isRtl(Locale locale) {
        return ARABIC.getLanguage().equals(locale.getLanguage());
    }
}
