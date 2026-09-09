package tn.civiccare.catalog;

import jakarta.persistence.*;
import tn.civiccare.identity.Department;

import java.util.UUID;

@Entity
@Table(name = "routing_rule")
public class RoutingRule {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_type_id")
    private ServiceType serviceType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(nullable = false)
    private boolean active = true;

    protected RoutingRule() {
    }

    public RoutingRule(UUID id, ServiceType serviceType, Department department) {
        this.id = id;
        this.serviceType = serviceType;
        this.department = department;
    }

    public UUID getId() {
        return id;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
