package tn.civiccare.administration.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import tn.civiccare.content.ContactMessage;
import tn.civiccare.content.ContentService;
import tn.civiccare.shared.AppProperties;
import tn.civiccare.shared.ui.UiFormats;

/** Boîte de contact interne : consultation et traitement. Aucun destinataire réel préconfiguré. */
@Route(value = "admin/contact", layout = AdminLayout.class)
@RolesAllowed({"AGENT", "MODERATOR", "ADMIN"})
public class AdminContactView extends VerticalLayout {

    private final Grid<ContactMessage> grid = new Grid<>();
    private final Paragraph body = new Paragraph();
    private final ContentService content;

    public AdminContactView(ContentService content, AppProperties props) {
        this.content = content;
        setSizeFull();
        add(new H2(getTranslation("admin.contact.title")));

        grid.addColumn(ContactMessage::getName).setHeader(getTranslation("contact.name"));
        grid.addColumn(ContactMessage::getEmail).setHeader(getTranslation("contact.email"));
        grid.addColumn(m -> UiFormats.dateTime(m.getCreatedAt(), props.zoneId(), getLocale()))
                .setHeader(getTranslation("common.date"));
        grid.addColumn(m -> m.getStatus().name()).setHeader(getTranslation("admin.reports.status"));
        grid.addComponentColumn(message -> {
            Button processed = new Button(getTranslation("admin.contact.markProcessed"), e -> {
                content.markProcessed(message.getId(), null);
                refresh();
            });
            processed.addThemeVariants(ButtonVariant.LUMO_SMALL);
            processed.setEnabled(message.getStatus() == ContactMessage.Status.NEW);
            return processed;
        }).setHeader(getTranslation("common.actions"));
        grid.addItemClickListener(e -> body.setText(e.getItem().getBody()));
        grid.setHeight("50%");

        body.getStyle().set("white-space", "pre-wrap");
        body.addClassName("report-card");

        refresh();
        add(grid, body);
        expand(grid);
    }

    private void refresh() {
        grid.setItems(content.inbox());
    }
}
