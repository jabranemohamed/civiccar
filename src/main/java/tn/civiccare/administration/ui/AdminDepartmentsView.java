package tn.civiccare.administration.ui;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import tn.civiccare.identity.Department;
import tn.civiccare.identity.StaffUserService;

/** Équipes de traitement : toutes fictives (démonstration), signalées comme telles. */
@Route(value = "admin/departments", layout = AdminLayout.class)
@RolesAllowed("ADMIN")
public class AdminDepartmentsView extends VerticalLayout {

    public AdminDepartmentsView(StaffUserService users) {
        add(new H2(getTranslation("admin.departments.title")));
        Span notice = new Span(getTranslation("admin.departments.demoNotice"));
        notice.addClassName("emergency-banner");
        add(notice);

        Grid<Department> grid = new Grid<>();
        grid.addColumn(Department::getCode).setHeader("Code");
        grid.addColumn(Department::getNameFr).setHeader("Nom (FR)");
        grid.addColumn(Department::getNameAr).setHeader("Nom (AR)");
        grid.addColumn(d -> d.isDemo() ? getTranslation("common.yes") : getTranslation("common.no"))
                .setHeader("Démo");
        grid.addColumn(d -> d.isActive() ? getTranslation("common.yes") : getTranslation("common.no"))
                .setHeader(getTranslation("admin.users.enabled"));
        grid.setItems(users.allDepartments());
        grid.setAllRowsVisible(true);
        add(grid);
    }
}
