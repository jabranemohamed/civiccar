package tn.civiccare.administration.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import tn.civiccare.identity.Department;
import tn.civiccare.identity.Role;
import tn.civiccare.identity.StaffUser;
import tn.civiccare.identity.StaffUserService;
import tn.civiccare.observability.Telemetry.ValidationException;

import java.util.stream.Collectors;

/** Gestion des utilisateurs internes : rôles, équipes, activation (ADMIN uniquement). */
@Route(value = "admin/users", layout = AdminLayout.class)
@RolesAllowed("ADMIN")
public class AdminUsersView extends VerticalLayout {

    private final Grid<StaffUser> grid = new Grid<>();
    private final StaffUserService users;

    public AdminUsersView(StaffUserService users) {
        this.users = users;
        setSizeFull();
        add(new H2(getTranslation("admin.users.title")));

        Button create = new Button("+ " + getTranslation("admin.users.title"), e -> openCreateDialog());
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        add(create);

        grid.addColumn(StaffUser::getUsername).setHeader(getTranslation("admin.users.username"));
        grid.addColumn(StaffUser::getDisplayName).setHeader(getTranslation("admin.users.displayName"));
        grid.addColumn(u -> u.getRoles().stream().map(Enum::name).collect(Collectors.joining(", ")))
                .setHeader(getTranslation("admin.users.roles"));
        grid.addColumn(u -> u.getDepartments().stream().map(Department::getNameFr)
                        .collect(Collectors.joining(", ")))
                .setHeader(getTranslation("admin.users.departments")).setFlexGrow(2);
        grid.addComponentColumn(user -> {
            Checkbox enabled = new Checkbox(user.isEnabled());
            enabled.addValueChangeListener(e -> users.setEnabled(user.getId(), e.getValue()));
            return enabled;
        }).setHeader(getTranslation("admin.users.enabled")).setWidth("90px").setFlexGrow(0);
        grid.setSizeFull();
        refresh();
        add(grid);
        expand(grid);
    }

    private void refresh() {
        grid.setItems(users.allUsers());
    }

    private void openCreateDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("admin.users.title"));
        TextField username = new TextField(getTranslation("admin.users.username"));
        TextField displayName = new TextField(getTranslation("admin.users.displayName"));
        PasswordField password = new PasswordField(getTranslation("login.password"));
        password.setHelperText("≥ 10 caractères");
        CheckboxGroup<Role> roles = new CheckboxGroup<>(getTranslation("admin.users.roles"));
        roles.setItems(Role.values());
        CheckboxGroup<Department> departments = new CheckboxGroup<>(getTranslation("admin.users.departments"));
        departments.setItems(users.allDepartments());
        departments.setItemLabelGenerator(Department::getNameFr);
        Button save = new Button(getTranslation("common.save"), e -> {
            try {
                users.create(username.getValue(), password.getValue(), displayName.getValue(),
                        roles.getSelectedItems(),
                        departments.getSelectedItems().stream().map(Department::getId)
                                .collect(Collectors.toSet()));
                dialog.close();
                refresh();
            } catch (ValidationException ex) {
                Notification.show(getTranslation("common.error"), 4000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button(getTranslation("common.cancel"), e -> dialog.close());
        dialog.add(new VerticalLayout(username, displayName, password, roles, departments));
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }
}
