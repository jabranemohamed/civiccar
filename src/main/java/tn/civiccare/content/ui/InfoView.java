package tn.civiccare.content.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.OptionalParameter;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import tn.civiccare.content.ContentPage;
import tn.civiccare.content.ContentService;
import tn.civiccare.shared.ui.PublicLayout;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Pages d'information : FAQ, fonctionnement, conditions, confidentialité, mentions
 * légales, accessibilité, documentation API. Contenu éditorial FR/AR en base, rendu
 * comme texte (jamais HTML brut).
 */
@Route(value = "info", layout = PublicLayout.class)
@AnonymousAllowed
public class InfoView extends VerticalLayout implements HasUrlParameter<String> {

    private static final Map<String, String> SLUGS = new LinkedHashMap<>();

    static {
        SLUGS.put("faq", "info.faq");
        SLUGS.put("how", "info.how");
        SLUGS.put("terms", "info.terms");
        SLUGS.put("privacy", "info.privacy");
        SLUGS.put("legal", "info.legal");
        SLUGS.put("accessibility", "info.accessibility");
        SLUGS.put("api", "info.api");
    }

    private final ContentService content;
    private final VerticalLayout body = new VerticalLayout();
    private final Tabs tabs = new Tabs();
    private final Map<Tab, String> tabSlugs = new LinkedHashMap<>();
    private boolean navigating;

    public InfoView(ContentService content) {
        this.content = content;
        setMaxWidth("860px");
        getStyle().set("margin", "0 auto");

        SLUGS.forEach((slug, key) -> {
            Tab tab = new Tab(getTranslation(key));
            tabs.add(tab);
            tabSlugs.put(tab, slug);
        });
        tabs.addSelectedChangeListener(e -> {
            if (!navigating && e.getSelectedTab() != null) {
                UI.getCurrent().navigate("info/" + tabSlugs.get(e.getSelectedTab()));
            }
        });
        body.setPadding(false);
        add(tabs, body);
    }

    @Override
    public void setParameter(BeforeEvent event, @OptionalParameter String slug) {
        String effective = slug == null || !SLUGS.containsKey(slug) ? "faq" : slug;
        navigating = true;
        tabSlugs.forEach((tab, s) -> {
            if (s.equals(effective)) {
                tabs.setSelectedTab(tab);
            }
        });
        navigating = false;
        render(effective);
    }

    private void render(String slug) {
        body.removeAll();
        Locale locale = UI.getCurrent().getLocale();
        content.page(slug).ifPresentOrElse(page -> {
            body.add(new H2(page.title(locale)));
            Paragraph text = new Paragraph(page.body(locale));
            text.getStyle().set("white-space", "pre-wrap");
            body.add(text);
        }, () -> body.add(new Paragraph(getTranslation("common.error"))));
    }
}
