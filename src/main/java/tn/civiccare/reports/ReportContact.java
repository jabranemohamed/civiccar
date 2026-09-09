package tn.civiccare.reports;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** Coordonnées privées du déclarant. Jamais publiées ; purgées 90 jours après clôture. */
@Entity
@Table(name = "report_contact")
public class ReportContact {

    @Id
    @Column(name = "report_id")
    private UUID reportId;

    @Column
    private String email;

    @Column
    private String phone;

    /** Plaque du véhicule hors d'usage : privée, jamais publiée. */
    @Column(name = "vehicle_plate")
    private String vehiclePlate;

    @Column(name = "consent_version", nullable = false)
    private String consentVersion;

    @Column(name = "consent_at", nullable = false)
    private Instant consentAt;

    @Column(nullable = false)
    private boolean purged;

    protected ReportContact() {
    }

    public ReportContact(UUID reportId, String email, String phone, String vehiclePlate,
                         String consentVersion, Instant consentAt) {
        this.reportId = reportId;
        this.email = email;
        this.phone = phone;
        this.vehiclePlate = vehiclePlate;
        this.consentVersion = consentVersion;
        this.consentAt = consentAt;
    }

    public UUID getReportId() {
        return reportId;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getVehiclePlate() {
        return vehiclePlate;
    }

    public String getConsentVersion() {
        return consentVersion;
    }

    public Instant getConsentAt() {
        return consentAt;
    }

    public boolean isPurged() {
        return purged;
    }

    /** Purge des données personnelles : efface les valeurs, conserve la trace du consentement. */
    public void purge() {
        this.email = null;
        this.phone = null;
        this.vehiclePlate = null;
        this.purged = true;
    }
}
