package tn.civiccare.administration.ui;

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import tn.civiccare.catalog.CatalogService;
import tn.civiccare.catalog.RoutingRule;
import tn.civiccare.catalog.ServiceType;
import tn.civiccare.identity.Department;
import tn.civiccare.identity.StaffUserService;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Catalogue : activer/désactiver un type selon la couverture locale et désigner
 * son équipe compétente. Le catalogue n'est pas présenté comme officiel.
 */
@Route(value = "admin/catalog", layout = AdminLayout.class)
@RolesAllowed("ADMIN")
public class AdminCatalogView extends VerticalLayout {

    public AdminCatalogView(CatalogService catalog, StaffUserService users) {
        setSizeFull();
        add(new H2(getTranslation("admin.catalog.title")));
        Span note = new Span(getTranslation("admin.departments.demoNotice"));
        note.addClassName("emergency-banner");
        add(note);

        List<Department> departments = users.allDepartments();
        Map<UUID, RoutingRule> ruleByType = catalog.allRoutingRules().stream()
                .collect(Collectors.toMap(r -> r.getServiceType().getId(), r -> r));

        Grid<ServiceType> grid = new Grid<>();
        grid.addColumn(t -> t.getGroup().getLabelFr())
                .setHeader(getTranslation("admin.catalog.family")).setFlexGrow(1);
        grid.addColumn(ServiceType::getLabelFr)
                .setHeader(getTranslation("admin.catalog.type") + " (FR)").setFlexGrow(2);
        grid.addColumn(ServiceType::getLabelAr)
                .setHeader(getTranslation("admin.catalog.type") + " (AR)").setFlexGrow(2);
        grid.addComponentColumn(type -> {
            Checkbox active = new Checkbox(type.isActive());
            active.addValueChangeListener(e -> catalog.setTypeActive(type.getId(), e.getValue()));
            return active;
        }).setHeader(getTranslation("admin.catalog.active")).setWidth("90px").setFlexGrow(0);
        grid.addComponentColumn(type -> {
            Select<Department> select = new Select<>();
            select.setItems(departments);
            select.setItemLabelGenerator(Department::getNameFr);
            RoutingRule rule = ruleByType.get(type.getId());
            if (rule != null) {
                departments.stream().filter(d -> d.getId().equals(rule.getDepartment().getId()))
                        .findFirst().ifPresent(select::setValue);
                select.addValueChangeListener(e -> {
                    if (e.getValue() != null) {
                        catalog.updateRouting(rule.getId(), e.getValue(), true);
                    }
                });
            }
            select.setWidth("260px");
            return select;
        }).setHeader(getTranslation("admin.catalog.department")).setAutoWidth(true);
        grid.setItems(catalog.allTypes());
        grid.setSizeFull();
        add(grid);
        expand(grid);
    }
}
