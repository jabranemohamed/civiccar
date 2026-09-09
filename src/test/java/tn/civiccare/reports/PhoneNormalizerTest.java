package tn.civiccare.reports;

import org.junit.jupiter.api.Test;
import tn.civiccare.observability.Telemetry.ValidationException;
import tn.civiccare.shared.AppProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A18 : téléphone tunisien local et international validés, région par défaut TN. */
class PhoneNormalizerTest {

    private final PhoneNormalizer normalizer = new PhoneNormalizer(
            new AppProperties("Africa/Tunis", "TN", "TN", 50, 30, 90, 3, 10_000_000,
                    null, null, null, null, null));

    @Test
    void tunisianLandlineAndMobileNormalizedToE164() {
        assertThat(normalizer.normalize("71 234 567")).isEqualTo("+21671234567");
        assertThat(normalizer.normalize("20 123 456")).isEqualTo("+21620123456");
        assertThat(normalizer.normalize("+216 71 234 567")).isEqualTo("+21671234567");
    }

    @Test
    void internationalNumbersAccepted() {
        assertThat(normalizer.normalize("+33 6 12 34 56 78")).isEqualTo("+33612345678");
        assertThat(normalizer.normalize("+49 30 123456")).startsWith("+4930");
    }

    @Test
    void optionalAndInvalid() {
        assertThat(normalizer.normalize(null)).isNull();
        assertThat(normalizer.normalize("  ")).isNull();
        assertThatThrownBy(() -> normalizer.normalize("123"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> normalizer.normalize("abc"))
                .isInstanceOf(ValidationException.class);
    }
}
