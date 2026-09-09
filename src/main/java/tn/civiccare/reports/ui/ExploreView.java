package tn.civiccare.reports.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import tn.civiccare.catalog.CatalogService;
import tn.civiccare.catalog.CategoryGroup;
import tn.civiccare.geo.BoundaryService;
import tn.civiccare.geo.ui.MapComponent;
import tn.civiccare.reports.ReportQueryService;
import tn.civiccare.reports.ReportQueryService.PublicSummary;
import tn.civiccare.reports.ReportQueryService.SearchCriteria;
import tn.civiccare.reports.WorkflowStatus;
import tn.civiccare.shared.AppProperties;
import tn.civiccare.shared.ui.PublicLayout;
import tn.civiccare.shared.ui.UiFormats;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Accueil : exploration carte + liste des signalements publiés. Mêmes filtres et même
 * visibilité pour la carte et la liste ; pagination côté serveur ; la liste reste
 * utilisable si la carte (WebGL/tuiles) échoue.
 */
@Route(value = "", layout = PublicLayout.class)
@AnonymousAllowed
public class ExploreView extends HorizontalLayout {

    private static final int PAGE_SIZE = 10;

    private final ReportQueryService queries;
    private final CatalogService catalog;
    private final AppProperties props;

    private final TextField search = new TextField();
    private final Select<CategoryGroup> categoryFilter = new Select<>();
    private final Select<WorkflowStatus> statusFilter = new Select<>();
    private final Select<String> periodFilter = new Select<>();
    private final Checkbox includeArchives = new Checkbox();
    private final Span resultCount = new Span();
    private final VerticalLayout resultsList = new VerticalLayout();
    private final Button prevPage = new Button();
    private final Button nextPage = new Button();
    private final Span pageInfo = new Span();
    private final MapComponent map;
    private final Span mapUnavailable = new Span();

    private int page = 0;
    private long totalCount = 0;
    private double[] bbox;
    private UUID selectedId;

    public ExploreView(ReportQueryService queries, CatalogService catalog,
                       BoundaryService boundary, AppProperties props) {
        this.queries = queries;
        this.catalog = catalog;
        this.props = props;
        setSizeFull();
        setSpacing(false);
        setPadding(false);

        // ===== Panneau latéral =====
        VerticalLayout panel = new VerticalLayout();
        panel.addClassName("explore-panel");
        panel.setPadding(true);
        panel.setSpacing(false);
        panel.setHeightFull();

        Span emergency = new Span(getTranslation("emergency.generic"));
        emergency.addClassName("emergency-banner");
        Span demo = new Span(getTranslation("app.demo.disclaimer"));
        demo.addClassNames("demo-banner");
        demo.getStyle().set("font-size", "var(--lumo-font-size-xs)")
                .set("color", "var(--lumo-secondary-text-color)");

        H2 title = new H2(getTranslation("home.title"));
        title.getStyle().set("margin", "var(--lumo-space-s) 0");

        search.setPlaceholder(getTranslation("home.search.placeholder"));
        search.setWidthFull();
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.setValueChangeTimeout(400);
        search.addValueChangeListener(e -> resetAndRefresh());
        search.setId("explore-search");
        search.getElement().setAttribute("aria-label", getTranslation("common.search"));

        categoryFilter.setLabel(getTranslation("filter.category"));
        List<CategoryGroup> groups = catalog.allGroups();
        categoryFilter.setItems(groups);
        categoryFilter.setItemLabelGenerator(g -> g == null
                ? getTranslation("filter.all") : g.label(locale()));
        categoryFilter.setEmptySelectionAllowed(true);
        categoryFilter.setEmptySelectionCaption(getTranslation("filter.all"));
        categoryFilter.addValueChangeListener(e -> resetAndRefresh());

        statusFilter.setLabel(getTranslation("filter.status"));
        statusFilter.setItems(WorkflowStatus.values());
        statusFilter.setItemLabelGenerator(s -> s == null
                ? getTranslation("filter.all") : getTranslation("status." + s.name()));
        statusFilter.setEmptySelectionAllowed(true);
        statusFilter.setEmptySelectionCaption(getTranslation("filter.all"));
        statusFilter.addValueChangeListener(e -> resetAndRefresh());

        periodFilter.setLabel(getTranslation("filter.period"));
        periodFilter.setItems("any", "today", "week", "month");
        periodFilter.setValue("any");
        periodFilter.setItemLabelGenerator(p -> getTranslation(switch (p) {
            case "today" -> "filter.period.today";
            case "week" -> "filter.period.week";
            case "month" -> "filter.period.month";
            default -> "filter.period.any";
        }));
        periodFilter.addValueChangeListener(e -> resetAndRefresh());

        includeArchives.setLabel(getTranslation("filter.archives"));
        includeArchives.addValueChangeListener(e -> resetAndRefresh());

        Button reset = new Button(getTranslation("filter.reset"), e -> {
            search.clear();
            categoryFilter.clear();
            statusFilter.clear();
            periodFilter.setValue("any");
            includeArchives.setValue(false);
        });
        reset.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        HorizontalLayout filters = new HorizontalLayout(categoryFilter, statusFilter, periodFilter);
        filters.setWidthFull();
        filters.setWrap(true);

        resultCount.setId("result-count");
        resultCount.getStyle().set("font-weight", "600");
        resultCount.getElement().setAttribute("role", "status");

        resultsList.setPadding(false);
        resultsList.setSpacing(true);
        resultsList.setId("results-list");

        prevPage.setText("‹");
        nextPage.setText("›");
        prevPage.getElement().setAttribute("aria-label", "Page précédente");
        nextPage.getElement().setAttribute("aria-label", "Page suivante");
        prevPage.addClickListener(e -> {
            if (page > 0) {
                page--;
                refreshList();
            }
        });
        nextPage.addClickListener(e -> {
            if ((long) (page + 1) * PAGE_SIZE < totalCount) {
                page++;
                refreshList();
            }
        });
        HorizontalLayout pagination = new HorizontalLayout(prevPage, pageInfo, nextPage);
        pagination.setAlignItems(FlexComponent.Alignment.CENTER);

        panel.add(emergency, demo, title, search, filters, includeArchives, reset,
                resultCount, resultsList, pagination);

        // ===== Carte =====
        map = new MapComponent(props);
        map.setSizeFull();
        map.showBoundary(boundary.activeBoundaryGeoJson());
        mapUnavailable.setText(getTranslation("home.map.unavailable"));
        mapUnavailable.addClassName("emergency-banner");
        mapUnavailable.setVisible(false);
        map.addErrorListener(e -> mapUnavailable.setVisible(true));
        map.addMoveListener(e -> {
            bbox = new double[]{e.getWest(), e.getSouth(), e.getEast(), e.getNorth()};
            refreshMap();
        });
        map.addMarkerClickListener(e -> UI.getCurrent().getPage().executeJs(
                "const el=document.querySelector('[data-report-id=\"'+$0+'\"]'); if(el){el.scrollIntoView({behavior:'smooth',block:'center'}); el.focus();}",
                e.getMarkerId().toString()));

        Div mapWrap = new Div(mapUnavailable, map);
        mapWrap.setSizeFull();
        mapWrap.getStyle().set("display", "flex").set("flex-direction", "column");
        map.getStyle().set("flex", "1");

        add(panel, mapWrap);
        setFlexGrow(0, panel);
        setFlexGrow(1, mapWrap);
        addClassName("explore-view");

        refreshAll();
    }

    private Locale locale() {
        UI ui = UI.getCurrent();
        return ui != null ? ui.getLocale() : Locale.forLanguageTag("fr");
    }

    private SearchCriteria criteria() {
        Instant from = null;
        Instant to = null;
        ZoneId zone = props.zoneId();
        LocalDate today = LocalDate.now(java.time.Clock.system(zone));
        switch (periodFilter.getValue() == null ? "any" : periodFilter.getValue()) {
            case "today" -> from = today.atStartOfDay(zone).toInstant();
            case "week" -> from = today.with(WeekFields.ISO.dayOfWeek(), 1).atStartOfDay(zone).toInstant();
            case "month" -> from = today.withDayOfMonth(1).atStartOfDay(zone).toInstant();
            default -> {
            }
        }
        return new SearchCriteria(search.getValue(),
                categoryFilter.getValue() == null ? null : categoryFilter.getValue().getId(),
                null,
                statusFilter.getValue(),
                from, to,
                Boolean.TRUE.equals(includeArchives.getValue()),
                false);
    }

    private void resetAndRefresh() {
        page = 0;
        refreshAll();
    }

    private void refreshAll() {
        refreshList();
        refreshMap();
    }

    private void refreshList() {
        SearchCriteria criteria = criteria();
        totalCount = queries.count(criteria);
        List<PublicSummary> results = queries.search(criteria, page * PAGE_SIZE, PAGE_SIZE);
        resultCount.setText(getTranslation("home.results.count", totalCount));
        resultsList.removeAll();
        if (results.isEmpty()) {
            Span empty = new Span(getTranslation("home.results.empty"));
            empty.getStyle().set("color", "var(--lumo-secondary-text-color)");
            resultsList.add(empty);
        } else {
            results.forEach(summary -> resultsList.add(card(summary)));
        }
        int totalPages = (int) Math.max(1, (totalCount + PAGE_SIZE - 1) / PAGE_SIZE);
        pageInfo.setText((page + 1) + " / " + totalPages);
        prevPage.setEnabled(page > 0);
        nextPage.setEnabled((long) (page + 1) * PAGE_SIZE < totalCount);
    }

    private void refreshMap() {
        double[] box = bbox != null ? bbox : new double[]{9.9, 36.6, 10.45, 37.0};
        List<ReportQueryService.MapPoint> points =
                queries.mapPoints(box[0], box[1], box[2], box[3], criteria());
        map.setMarkers(points.stream()
                .map(p -> new MapComponent.Marker(p.id(), p.longitude(), p.latitude(),
                        p.status().name(), p.reference()))
                .toList());
    }

    private Div card(PublicSummary summary) {
        Div card = new Div();
        card.addClassName("report-card");
        card.getElement().setAttribute("data-report-id", summary.id().toString());
        card.getElement().setAttribute("tabindex", "0");
        card.getElement().setAttribute("role", "link");
        if (summary.id().equals(selectedId)) {
            card.getElement().setAttribute("data-selected", "true");
        }

        Span type = new Span(summary.typeLabel(locale()));
        type.getStyle().set("font-weight", "600");

        Span address = new Span(summary.address() == null ? "" : summary.address());
        address.getStyle().set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");

        Span date = new Span(UiFormats.date(summary.createdAt(), props.zoneId(), locale())
                + " · " + summary.reference());
        date.getStyle().set("color", "var(--lumo-tertiary-text-color)")
                .set("font-size", "var(--lumo-font-size-xs)");

        VerticalLayout text = new VerticalLayout(type, address,
                (com.vaadin.flow.component.Component) UiFormats.statusBadge(summary.status(), summary.archived(), this), date);
        text.setPadding(false);
        text.setSpacing(false);

        HorizontalLayout content = new HorizontalLayout();
        content.setWidthFull();
        if (summary.thumbKey() != null) {
            Image thumb = new Image("/media/" + summary.thumbKey(), "");
            thumb.setWidth("72px");
            thumb.setHeight("72px");
            thumb.getStyle().set("object-fit", "cover")
                    .set("border-radius", "var(--lumo-border-radius-s)");
            content.add(thumb);
        }
        content.add(text);
        card.add(content);

        card.addClickListener(e -> {
            selectedId = summary.id();
            map.highlight(summary.id());
            map.flyTo(summary.longitude(), summary.latitude(), 16);
            UI.getCurrent().navigate("requests/" + summary.reference());
        });
        return card;
    }
}
