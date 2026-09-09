package tn.civiccare.administration.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.spring.security.AuthenticationContext;

/** Layout du back-office : navigation latérale compacte, identité de l'agent, déconnexion. */
@jakarta.annotation.security.RolesAllowed({"AGENT", "MODERATOR", "ADMIN"})
public class AdminLayout extends AppLayout {

    public AdminLayout(AuthenticationContext authContext) {
        setPrimarySection(Section.DRAWER);

        H1 title = new H1("CivicCare — " + getTranslation("admin.title"));
        title.addClassName("app-title");
        title.getStyle().set("font-size", "var(--lumo-font-size-l)").set("margin", "0");

        String username = authContext.getPrincipalName().orElse("");
        Span user = new Span(username);
        user.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Button logout = new Button(getTranslation("nav.logout"), VaadinIcon.SIGN_OUT.create(),
                e -> authContext.logout());
        logout.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        Button publicSite = new Button(getTranslation("nav.explore"),
                e -> UI.getCurrent().navigate(""));
        publicSite.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        HorizontalLayout header = new HorizontalLayout(new DrawerToggle(), title, publicSite, user, logout);
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.expand(title);
        header.addClassName("app-header");
        addToNavbar(header);

        SideNav nav = new SideNav();
        nav.addItem(new SideNavItem(getTranslation("admin.nav.dashboard"), AdminDashboardView.class,
                VaadinIcon.DASHBOARD.create()));
        nav.addItem(new SideNavItem(getTranslation("admin.nav.reports"), AdminReportsView.class,
                VaadinIcon.RECORDS.create()));
        nav.addItem(new SideNavItem(getTranslation("admin.nav.moderation"), AdminModerationView.class,
                VaadinIcon.EYE.create()));
        nav.addItem(new SideNavItem(getTranslation("admin.nav.catalog"), AdminCatalogView.class,
                VaadinIcon.LIST.create()));
        nav.addItem(new SideNavItem(getTranslation("admin.nav.departments"), AdminDepartmentsView.class,
                VaadinIcon.GROUP.create()));
        nav.addItem(new SideNavItem(getTranslation("admin.nav.users"), AdminUsersView.class,
                VaadinIcon.USERS.create()));
        nav.addItem(new SideNavItem(getTranslation("admin.nav.content"), AdminContentView.class,
                VaadinIcon.FILE_TEXT.create()));
        nav.addItem(new SideNavItem(getTranslation("admin.nav.contact"), AdminContactView.class,
                VaadinIcon.ENVELOPES.create()));
        nav.addItem(new SideNavItem(getTranslation("admin.nav.settings"), AdminSettingsView.class,
                VaadinIcon.COG.create()));
        addToDrawer(nav);
    }
}
