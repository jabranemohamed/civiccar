package tn.civiccare.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Équipe de traitement. Les équipes livrées sont fictives (démonstration) :
 * aucune correspondance vérifiée avec une administration tunisienne réelle.
 */
@Entity
@Table(name = "department")
public class Department {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "name_fr", nullable = false)
    private String nameFr;

    @Column(name = "name_ar", nullable = false)
    private String nameAr;

    @Column(name = "name_en")
    private String nameEn;

    @Column(nullable = false)
    private boolean demo = true;

    @Column(nullable = false)
    private boolean active = true;

    protected Department() {
    }

    public Department(UUID id, String code, String nameFr, String nameAr) {
        this.id = id;
        this.code = code;
        this.nameFr = nameFr;
        this.nameAr = nameAr;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getNameFr() {
        return nameFr;
    }

    public String getNameAr() {
        return nameAr;
    }

    public String name(java.util.Locale locale) {
        return switch (locale.getLanguage()) {
            case "ar" -> nameAr;
            case "en" -> nameEn != null ? nameEn : nameFr;
            default -> nameFr;
        };
    }

    public boolean isDemo() {
        return demo;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Department d && id.equals(d.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
