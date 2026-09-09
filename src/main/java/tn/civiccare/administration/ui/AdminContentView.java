package tn.civiccare.administration.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.RolesAllowed;
import tn.civiccare.content.ContentPage;
import tn.civiccare.content.ContentService;

/** Édition des pages de contenu FR/AR (ADMIN). */
@Route(value = "admin/content", layout = AdminLayout.class)
@RolesAllowed("ADMIN")
public class AdminContentView extends VerticalLayout {

    public AdminContentView(ContentService content, AuthenticationContext authContext) {
        add(new H2(getTranslation("admin.content.title")));

        Select<ContentPage> pages = new Select<>();
        pages.setItems(content.allPages());
        pages.setItemLabelGenerator(ContentPage::getSlug);
        pages.setWidth("300px");

        TextField titleFr = new TextField("Titre (FR)");
        titleFr.setWidthFull();
        TextField titleAr = new TextField("العنوان (AR)");
        titleAr.setWidthFull();
        titleAr.getElement().setAttribute("dir", "rtl");
        TextField titleEn = new TextField("Title (EN)");
        titleEn.setWidthFull();
        TextArea bodyFr = new TextArea("Contenu (FR)");
        bodyFr.setWidthFull();
        bodyFr.setMinHeight("240px");
        TextArea bodyAr = new TextArea("المحتوى (AR)");
        bodyAr.setWidthFull();
        bodyAr.setMinHeight("240px");
        bodyAr.getElement().setAttribute("dir", "rtl");
        TextArea bodyEn = new TextArea("Content (EN) — repli sur le FR si vide");
        bodyEn.setWidthFull();
        bodyEn.setMinHeight("240px");

        pages.addValueChangeListener(e -> {
            ContentPage page = e.getValue();
            if (page != null) {
                titleFr.setValue(page.getTitleFr());
                titleAr.setValue(page.getTitleAr());
                titleEn.setValue(page.getTitleEn() == null ? "" : page.getTitleEn());
                bodyFr.setValue(page.getBodyFr());
                bodyAr.setValue(page.getBodyAr());
                bodyEn.setValue(page.getBodyEn() == null ? "" : page.getBodyEn());
            }
        });

        Button save = new Button(getTranslation("common.save"), e -> {
            if (pages.getValue() != null) {
                content.updatePage(pages.getValue().getSlug(), titleFr.getValue(), titleAr.getValue(),
                        titleEn.getValue(), bodyFr.getValue(), bodyAr.getValue(), bodyEn.getValue(), null);
                Notification.show(getTranslation("common.save"));
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        add(pages, titleFr, titleAr, titleEn, bodyFr, bodyAr, bodyEn, save);
    }
}
