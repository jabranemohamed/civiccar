package tn.civiccare.reports;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import org.springframework.stereotype.Component;
import tn.civiccare.observability.Telemetry.ValidationException;
import tn.civiccare.shared.AppProperties;

/**
 * Normalisation E.164 avec la Tunisie (TN) comme région de saisie par défaut.
 * Un numéro international valide (+ préfixe) reste accepté ; le téléphone est facultatif.
 */
@Component
public class PhoneNormalizer {

    private final PhoneNumberUtil util = PhoneNumberUtil.getInstance();
    private final String defaultRegion;

    public PhoneNormalizer(AppProperties props) {
        this.defaultRegion = props.phoneDefaultRegion();
    }

    /** @return numéro E.164, ou null si l'entrée est vide. */
    public String normalize(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        try {
            Phonenumber.PhoneNumber number = util.parse(input.trim(), defaultRegion);
            if (!util.isValidNumber(number)) {
                throw new ValidationException("phone.invalid");
            }
            return util.format(number, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException e) {
            throw new ValidationException("phone.invalid");
        }
    }
}
