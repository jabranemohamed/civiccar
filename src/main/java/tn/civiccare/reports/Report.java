package tn.civiccare.reports;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;
import tn.civiccare.catalog.ServiceType;
import tn.civiccare.identity.Department;
import tn.civiccare.identity.StaffUser;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "report")
public class Report {

    @Id
    private UUID id;

    /** Référence humaine unique (ex. 000123-2026), issue d'une séquence transactionnelle. */
    @Column(nullable = false, unique = true)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_type_id")
    private ServiceType serviceType;

    /** WGS84, ordre PostGIS : longitude puis latitude (Point(x=lon, y=lat)). */
    @Column(nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point location;

    /** Adresse publique du problème — jamais celle du déclarant. */
    @Column
    private String address;

    @Column(name = "address_details")
    private String addressDetails;

    @Column(name = "description_private")
    private String descriptionPrivate;

    @Column(name = "description_public")
    private String descriptionPublic;

    /** Valeurs des champs conditionnels validées côté serveur (code champ -> valeur). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_values", nullable = false)
    private Map<String, String> fieldValues = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_status", nullable = false)
    private WorkflowStatus workflowStatus = WorkflowStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "publication_status", nullable = false)
    private PublicationStatus publicationStatus = PublicationStatus.PENDING_REVIEW;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private StaffUser assignee;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "personal_data_purged_at")
    private Instant personalDataPurgedAt;

    @Version
    private long version;

    protected Report() {
    }

    public Report(UUID id, String reference, ServiceType serviceType, Point location, Instant now) {
        this.id = id;
        this.reference = reference;
        this.serviceType = serviceType;
        this.location = location;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

    public void setServiceType(ServiceType serviceType) {
        this.serviceType = serviceType;
    }

    public Point getLocation() {
        return location;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getAddressDetails() {
        return addressDetails;
    }

    public void setAddressDetails(String addressDetails) {
        this.addressDetails = addressDetails;
    }

    public String getDescriptionPrivate() {
        return descriptionPrivate;
    }

    public void setDescriptionPrivate(String descriptionPrivate) {
        this.descriptionPrivate = descriptionPrivate;
    }

    public String getDescriptionPublic() {
        return descriptionPublic;
    }

    public void setDescriptionPublic(String descriptionPublic) {
        this.descriptionPublic = descriptionPublic;
    }

    public Map<String, String> getFieldValues() {
        return fieldValues;
    }

    public void setFieldValues(Map<String, String> fieldValues) {
        this.fieldValues = fieldValues;
    }

    public WorkflowStatus getWorkflowStatus() {
        return workflowStatus;
    }

    public void setWorkflowStatus(WorkflowStatus workflowStatus) {
        this.workflowStatus = workflowStatus;
    }

    public PublicationStatus getPublicationStatus() {
        return publicationStatus;
    }

    public void setPublicationStatus(PublicationStatus publicationStatus) {
        this.publicationStatus = publicationStatus;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public StaffUser getAssignee() {
        return assignee;
    }

    public void setAssignee(StaffUser assignee) {
        this.assignee = assignee;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
    }

    public Instant getPersonalDataPurgedAt() {
        return personalDataPurgedAt;
    }

    public void setPersonalDataPurgedAt(Instant personalDataPurgedAt) {
        this.personalDataPurgedAt = personalDataPurgedAt;
    }

    public long getVersion() {
        return version;
    }

    public boolean isPubliclyVisible() {
        return publicationStatus == PublicationStatus.PUBLISHED;
    }
}
