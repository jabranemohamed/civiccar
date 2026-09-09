package tn.civiccare.catalog;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Définition bornée d'un champ conditionnel : TEXT ou SELECT, jamais de code exécutable. */
@Entity
@Table(name = "field_definition")
public class FieldDefinition {

    public enum Kind {TEXT, SELECT}

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "label_fr", nullable = false)
    private String labelFr;

    @Column(name = "label_ar", nullable = false)
    private String labelAr;

    @Column(name = "label_en")
    private String labelEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    @Column(nullable = false)
    private boolean required;

    /** false = valeur privée, jamais publiée (ex. plaque). */
    @Column(name = "is_public", nullable = false)
    private boolean publicField;

    @Column(name = "max_len", nullable = false)
    private int maxLen;

    @Column(nullable = false)
    private int sort;

    @OneToMany(mappedBy = "field", fetch = FetchType.LAZY)
    @OrderBy("sort")
    private List<FieldOption> options = new ArrayList<>();

    protected FieldDefinition() {
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String label(Locale locale) {
        return switch (locale.getLanguage()) {
            case "ar" -> labelAr;
            case "en" -> labelEn != null ? labelEn : labelFr;
            default -> labelFr;
        };
    }

    public Kind getKind() {
        return kind;
    }

    public boolean isRequired() {
        return required;
    }

    public boolean isPublicField() {
        return publicField;
    }

    public int getMaxLen() {
        return maxLen;
    }

    public int getSort() {
        return sort;
    }

    public List<FieldOption> getOptions() {
        return options;
    }
}
