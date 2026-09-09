package tn.civiccare.reports.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.server.streams.UploadHandler;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import tn.civiccare.catalog.CatalogService;
import tn.civiccare.catalog.FieldDefinition;
import tn.civiccare.catalog.FieldOption;
import tn.civiccare.catalog.ServiceType;
import tn.civiccare.geo.BoundaryService;
import tn.civiccare.geo.Geocoder;
import tn.civiccare.geo.ui.MapComponent;
import tn.civiccare.media.ImageProcessor;
import tn.civiccare.media.MediaService;
import tn.civiccare.observability.Telemetry.ValidationException;
import tn.civiccare.reports.Report;
import tn.civiccare.reports.ReportQueryService;
import tn.civiccare.reports.ReportService;
import tn.civiccare.shared.AppProperties;
import tn.civiccare.shared.RateLimiter;
import tn.civiccare.shared.ui.PublicLayout;
import tn.civiccare.shared.ui.UiFormats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Assistant de dépôt en 4 étapes : position, type, détails, contact.
 * Retour sans perte de données ; validations près des champs ; toutes les règles
 * sont re-validées côté serveur au submit. Clé d'idempotence par assistant :
 * un double envoi ne crée qu'un seul dossier.
 */
@Route(value = "report", layout = PublicLayout.class)
@AnonymousAllowed
public class ReportWizardView extends VerticalLayout {

    private final ReportService reportService;
    private final ReportQueryService queries;
    private final CatalogService catalog;
    private final Geocoder geocoder;
    private final BoundaryService boundary;
    private final MediaService mediaService;
    private final AppProperties props;
    private final RateLimiter rateLimiter;

    // État de l'assistant
    private final String idempotencyKey = UUID.randomUUID().toString();
    private Double pickedLon;
    private Double pickedLat;
    private ServiceType selectedType;
    private final Map<String, String> fieldValues = new HashMap<>();
    private final List<NamedPhoto> photos = new ArrayList<>();
    private boolean duplicatesConfirmed;

    private record NamedPhoto(String name, ImageProcessor.Processed processed) {
    }

    private int step = 0;
    private final Div stepContainer = new Div();
    private final HorizontalLayout progress = new HorizontalLayout();
    private final Button backButton = new Button();
    private final Button nextButton = new Button();

    // Étape 1
    private MapComponent pickerMap;
    private final TextField addressField = new TextField();
    private final NumberField latField = new NumberField();
    private final NumberField lonField = new NumberField();
    private final Span positionError = new Span();
    private final VerticalLayout geocodeResults = new VerticalLayout();

    // Étape 2
    private final TextField typeSearch = new TextField();
    private final VerticalLayout typeList = new VerticalLayout();
    private final Span typeError = new Span();

    // Étape 3
    private final TextArea description = new TextArea();
    private final VerticalLayout conditionalFields = new VerticalLayout();
    private final VerticalLayout photosPreview = new VerticalLayout();
    private final Span photoCount = new Span();

    // Étape 4
    private final EmailField email = new EmailField();
    private final TextField phone = new TextField();
    private final Checkbox consent = new Checkbox();
    private final VerticalLayout summary = new VerticalLayout();

    public ReportWizardView(ReportService reportService, ReportQueryService queries,
                            CatalogService catalog, Geocoder geocoder, BoundaryService boundary,
                            MediaService mediaService, AppProperties props, RateLimiter rateLimiter) {
        this.reportService = reportService;
        this.queries = queries;
        this.catalog = catalog;
        this.geocoder = geocoder;
        this.boundary = boundary;
        this.mediaService = mediaService;
        this.props = props;
        this.rateLimiter = rateLimiter;

        setMaxWidth("900px");
        getStyle().set("margin", "0 auto");

        add(new H2(getTranslation("wizard.title")));
        progress.setWidthFull();
        progress.addClassName("wizard-progress");
        add(progress);

        stepContainer.setWidthFull();
        add(stepContainer);

        backButton.setText(getTranslation("wizard.back"));
        backButton.addClickListener(e -> goToStep(step - 1));
        nextButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        nextButton.setId("wizard-next");
        nextButton.addClickListener(e -> onNext());
        HorizontalLayout nav = new HorizontalLayout(backButton, nextButton);
        nav.setWidthFull();
        nav.setJustifyContentMode(JustifyContentMode.BETWEEN);
        add(nav);

        buildStep1();
        buildStep2();
        buildStep3();
        buildStep4();
        goToStep(0);
    }

    private Locale locale() {
        return UI.getCurrent().getLocale();
    }

    // ===== Navigation =====

    private final List<Component> steps = new ArrayList<>();

    private void goToStep(int target) {
        step = Math.max(0, Math.min(3, target));
        stepContainer.removeAll();
        stepContainer.add(steps.get(step));
        backButton.setVisible(step > 0);
        nextButton.setText(step == 3 ? getTranslation("wizard.submit") : getTranslation("wizard.next"));
        progress.removeAll();
        String[] keys = {"wizard.step.position", "wizard.step.type", "wizard.step.details", "wizard.step.contact"};
        for (int i = 0; i < 4; i++) {
            Span chip = new Span((i + 1) + ". " + getTranslation(keys[i]));
            chip.addClassName("status-badge");
            chip.getStyle().set("background", i == step ? "var(--lumo-primary-color)" : "var(--lumo-contrast-5pct)")
                    .set("color", i == step ? "white" : "var(--lumo-secondary-text-color)");
            if (i < step) {
                chip.getStyle().set("background", "var(--lumo-primary-color-10pct)")
                        .set("color", "var(--lumo-primary-text-color)");
            }
            progress.add(chip);
        }
        if (step == 3) {
            refreshSummary();
        }
    }

    private void onNext() {
        switch (step) {
            case 0 -> {
                if (pickedLon == null || pickedLat == null) {
                    positionError.setText(getTranslation("wizard.position.required"));
                    positionError.setVisible(true);
                    return;
                }
                if (!boundary.isInsideBoundary(pickedLon, pickedLat)) {
                    positionError.setText(getTranslation("wizard.position.outside"));
                    positionError.setVisible(true);
                    return;
                }
                positionError.setVisible(false);
                goToStep(1);
            }
            case 1 -> {
                if (selectedType == null) {
                    typeError.setText(getTranslation("wizard.type.required"));
                    typeError.setVisible(true);
                    return;
                }
                typeError.setVisible(false);
                rebuildDetailsStep();
                goToStep(2);
            }
            case 2 -> {
                if (selectedType.isStandardDescription()) {
                    String value = description.getValue() == null ? "" : description.getValue().trim();
                    if (value.isEmpty() || value.length() > ReportService.DESCRIPTION_MAX) {
                        description.setInvalid(true);
                        description.setErrorMessage(getTranslation(
                                "wizard.details.description.required", ReportService.DESCRIPTION_MAX));
                        return;
                    }
                }
                if (!validateConditionalFields()) {
                    return;
                }
                goToStep(3);
            }
            case 3 -> submit();
        }
    }

    // ===== Étape 1 : position =====

    private void buildStep1() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.add(new Paragraph(getTranslation("wizard.position.intro")));

        addressField.setLabel(getTranslation("wizard.position.search"));
        addressField.setWidthFull();
        addressField.setClearButtonVisible(true);
        addressField.setValueChangeMode(ValueChangeMode.ON_CHANGE);
        Button searchButton = new Button(getTranslation("common.search"), VaadinIcon.SEARCH.create());
        searchButton.addClickListener(e -> doGeocode());
        addressField.addKeyPressListener(com.vaadin.flow.component.Key.ENTER, e -> doGeocode());
        HorizontalLayout searchRow = new HorizontalLayout(addressField, searchButton);
        searchRow.setWidthFull();
        searchRow.setAlignItems(Alignment.END);
        searchRow.expand(addressField);

        Button locate = new Button(getTranslation("wizard.position.locate"), VaadinIcon.CROSSHAIRS.create());
        locate.setId("locate-button");
        // Géolocalisation demandée uniquement au clic de l'utilisateur.
        locate.addClickListener(e -> UI.getCurrent().getPage().executeJs("""
                const el = $0;
                if (!navigator.geolocation) { el.dispatchEvent(new CustomEvent('geo-denied')); return; }
                navigator.geolocation.getCurrentPosition(
                  p => el.dispatchEvent(new CustomEvent('geo-found', {detail:{lon:p.coords.longitude, lat:p.coords.latitude}})),
                  () => el.dispatchEvent(new CustomEvent('geo-denied')));
                """, getElement()));
        getElement().addEventListener("geo-found", ev -> {
            double lon = ev.getEventData().get("event.detail.lon").asDouble();
            double lat = ev.getEventData().get("event.detail.lat").asDouble();
            setPicked(lon, lat, true);
        }).addEventData("event.detail.lon").addEventData("event.detail.lat");
        getElement().addEventListener("geo-denied", ev ->
                Notification.show(getTranslation("wizard.position.locate.denied"), 5000,
                        Notification.Position.MIDDLE));

        geocodeResults.setPadding(false);
        geocodeResults.setSpacing(false);

        pickerMap = new MapComponent(props);
        pickerMap.setWidthFull();
        pickerMap.setHeight("380px");
        pickerMap.setPickerEnabled(true);
        pickerMap.showBoundary(boundary.activeBoundaryGeoJson());
        pickerMap.addPickListener(e -> setPicked(e.getLon(), e.getLat(), false));

        // Alternative sans carte / sans GPS : saisie manuelle de coordonnées.
        Details manual = new Details(getTranslation("wizard.position.manual"));
        latField.setLabel(getTranslation("wizard.position.lat"));
        latField.setMin(-90);
        latField.setMax(90);
        latField.setWidth("170px");
        lonField.setLabel(getTranslation("wizard.position.lon"));
        lonField.setMin(-180);
        lonField.setMax(180);
        lonField.setWidth("170px");
        latField.addValueChangeListener(e -> applyManualCoords());
        lonField.addValueChangeListener(e -> applyManualCoords());
        HorizontalLayout coords = new HorizontalLayout(latField, lonField);
        manual.add(coords);

        TextField addressDetails = new TextField(getTranslation("wizard.position.address"));
        addressDetails.setWidthFull();
        addressDetails.setId("address-details");
        addressDetails.setMaxLength(300);
        addressDetails.addValueChangeListener(e -> this.addressDetailsValue = e.getValue());

        positionError.getStyle().set("color", "var(--lumo-error-text-color)");
        positionError.setVisible(false);
        positionError.getElement().setAttribute("role", "alert");

        layout.add(searchRow, locate, geocodeResults, pickerMap, manual, addressDetails, positionError);
        steps.add(layout);
    }

    private String addressValue;
    private String addressDetailsValue;

    private void doGeocode() {
        geocodeResults.removeAll();
        String query = addressField.getValue();
        if (query == null || query.isBlank()) {
            return;
        }
        List<Geocoder.GeocodeResult> results = geocoder.search(query, locale());
        if (results.isEmpty()) {
            geocodeResults.add(new Span(getTranslation("wizard.position.geocoder.none")));
            return;
        }
        for (Geocoder.GeocodeResult result : results) {
            Button pick = new Button(result.label(), VaadinIcon.MAP_MARKER.create());
            pick.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            pick.addClickListener(e -> {
                addressValue = result.label();
                addressField.setValue(result.label());
                setPicked(result.longitude(), result.latitude(), true);
                geocodeResults.removeAll();
            });
            geocodeResults.add(pick);
        }
        if (geocoder.isDemo()) {
            Span demoNote = new Span("Résultats de démonstration (géocodeur local) — نتائج تجريبية");
            demoNote.getStyle().set("font-size", "var(--lumo-font-size-xs)")
                    .set("color", "var(--lumo-tertiary-text-color)");
            geocodeResults.add(demoNote);
        }
    }

    private void setPicked(double lon, double lat, boolean flyTo) {
        pickedLon = lon;
        pickedLat = lat;
        latField.setValue(lat);
        lonField.setValue(lon);
        pickerMap.setPickedPoint(lon, lat);
        if (flyTo) {
            pickerMap.flyTo(lon, lat, 16);
        }
        positionError.setVisible(false);
        // Retour immédiat si le point sort du périmètre pris en charge.
        if (!boundary.isInsideBoundary(lon, lat)) {
            positionError.setText(getTranslation("wizard.position.outside"));
            positionError.setVisible(true);
        }
        // Géocodage inverse best-effort : jamais d'adresse inventée si introuvable.
        if (addressValue == null || addressValue.isBlank()) {
            geocoder.reverse(lon, lat, locale()).ifPresent(r -> {
                addressValue = r.label();
                addressField.setValue(r.label());
            });
        }
    }

    private void applyManualCoords() {
        if (latField.getValue() != null && lonField.getValue() != null) {
            pickedLat = latField.getValue();
            pickedLon = lonField.getValue();
            pickerMap.setPickedPoint(pickedLon, pickedLat);
        }
    }

    // ===== Étape 2 : type =====

    private void buildStep2() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.add(new Paragraph(getTranslation("wizard.type.intro")));

        typeSearch.setPlaceholder(getTranslation("wizard.type.search"));
        typeSearch.setWidthFull();
        typeSearch.setClearButtonVisible(true);
        typeSearch.setValueChangeMode(ValueChangeMode.LAZY);
        typeSearch.setValueChangeTimeout(300);
        typeSearch.addValueChangeListener(e -> renderTypeList(e.getValue()));
        typeSearch.setId("type-search");

        typeError.getStyle().set("color", "var(--lumo-error-text-color)");
        typeError.setVisible(false);
        typeError.getElement().setAttribute("role", "alert");

        typeList.setPadding(false);
        layout.add(typeSearch, typeError, typeList);
        steps.add(layout);
        renderTypeList("");
    }

    private void renderTypeList(String query) {
        typeList.removeAll();
        Locale locale = locale();
        List<ServiceType> types = (query == null || query.isBlank())
                ? catalog.activeTypes()
                : catalog.searchTypes(query, locale);
        Map<String, List<ServiceType>> byGroup = new LinkedHashMap<>();
        for (ServiceType type : types) {
            byGroup.computeIfAbsent(type.getGroup().label(locale), k -> new ArrayList<>()).add(type);
        }
        byGroup.forEach((groupLabel, groupTypes) -> {
            H4 header = new H4(groupLabel);
            header.getStyle().set("margin", "var(--lumo-space-s) 0 0 0");
            typeList.add(header);
            HorizontalLayout row = new HorizontalLayout();
            row.setWrap(true);
            for (ServiceType type : groupTypes) {
                Button typeButton = new Button(type.label(locale));
                typeButton.getElement().setAttribute("data-type-code", type.getCode());
                boolean selected = selectedType != null && selectedType.getId().equals(type.getId());
                typeButton.addThemeVariants(selected
                        ? ButtonVariant.LUMO_PRIMARY : ButtonVariant.LUMO_CONTRAST);
                if (!selected) {
                    typeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
                }
                typeButton.addClickListener(e -> {
                    selectedType = type;
                    fieldValues.clear();
                    typeError.setVisible(false);
                    renderTypeList(typeSearch.getValue());
                });
                row.add(typeButton);
            }
            typeList.add(row);
        });
        // Aide contextuelle du type sélectionné
        if (selectedType != null && selectedType.help(locale) != null) {
            Details help = new Details("ℹ " + selectedType.label(locale));
            help.add(new Paragraph(selectedType.help(locale)));
            help.setOpened(true);
            typeList.add(help);
        }
    }

    // ===== Étape 3 : détails =====

    private void buildStep3() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.add(new Paragraph(getTranslation("wizard.details.intro")));

        description.setWidthFull();
        description.setMaxLength(ReportService.DESCRIPTION_MAX);
        description.setValueChangeMode(ValueChangeMode.EAGER);
        description.setId("description-field");
        description.addValueChangeListener(e -> description.setHelperText(
                getTranslation("wizard.details.description.counter",
                        e.getValue() == null ? 0 : e.getValue().length(), ReportService.DESCRIPTION_MAX)));

        conditionalFields.setPadding(false);

        long maxMb = props.maxPhotoBytes() / (1024 * 1024);
        H4 photosTitle = new H4(getTranslation("wizard.details.photos", props.maxPhotosPerReport()));
        Paragraph photosHint = new Paragraph(getTranslation("wizard.details.photos.hint", maxMb));
        photosHint.getStyle().set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");

        Upload upload = new Upload(UploadHandler.inMemory((metadata, data) -> {
            if (photos.size() >= props.maxPhotosPerReport()) {
                Notification.show(getTranslation("wizard.details.photos.tooMany"))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            try {
                ImageProcessor.Processed processed = mediaService.validateAndProcess(data);
                photos.add(new NamedPhoto(metadata.fileName(), processed));
                refreshPhotoPreview();
            } catch (ValidationException ex) {
                Notification.show(getTranslation("wizard.details.photos.invalid"), 5000,
                                Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        }));
        upload.setAcceptedFileTypes("image/jpeg", "image/png", ".jpg", ".jpeg", ".png");
        upload.setMaxFiles(props.maxPhotosPerReport());
        upload.setMaxFileSize((int) props.maxPhotoBytes());
        upload.addFileRejectedListener(e -> Notification
                .show(getTranslation("wizard.details.photos.invalid"), 5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR));

        photosPreview.setPadding(false);
        photoCount.getStyle().set("font-size", "var(--lumo-font-size-s)");
        refreshPhotoPreview();

        layout.add(description, conditionalFields, photosTitle, photosHint, upload, photoCount, photosPreview);
        steps.add(layout);
    }

    /** Reconstruit les champs dépendant du type (description standard, champs conditionnels). */
    private void rebuildDetailsStep() {
        Locale locale = locale();
        description.setVisible(selectedType.isStandardDescription());
        description.setLabel(getTranslation("wizard.details.description", ReportService.DESCRIPTION_MAX));
        description.setRequiredIndicatorVisible(selectedType.isStandardDescription());
        conditionalFields.removeAll();
        for (FieldDefinition field : selectedType.getFields()) {
            if (field.getKind() == FieldDefinition.Kind.SELECT) {
                Select<FieldOption> select = new Select<>();
                select.setLabel(field.label(locale));
                select.setRequiredIndicatorVisible(field.isRequired());
                select.setItems(field.getOptions().stream().filter(FieldOption::isActive).toList());
                select.setItemLabelGenerator(o -> o.label(locale));
                select.setWidthFull();
                select.setMaxWidth("420px");
                select.getElement().setAttribute("data-field-code", field.getCode());
                if (fieldValues.containsKey(field.getCode())) {
                    field.getOptions().stream()
                            .filter(o -> o.getCode().equals(fieldValues.get(field.getCode())))
                            .findFirst().ifPresent(select::setValue);
                }
                select.addValueChangeListener(e -> {
                    if (e.getValue() != null) {
                        fieldValues.put(field.getCode(), e.getValue().getCode());
                    }
                });
                conditionalFields.add(select);
            } else {
                TextField text = new TextField(field.label(locale));
                text.setMaxLength(field.getMaxLen());
                text.setRequiredIndicatorVisible(field.isRequired());
                text.setWidthFull();
                text.setMaxWidth("420px");
                text.getElement().setAttribute("data-field-code", field.getCode());
                text.setValue(fieldValues.getOrDefault(field.getCode(), ""));
                text.addValueChangeListener(e -> fieldValues.put(field.getCode(), e.getValue()));
                conditionalFields.add(text);
            }
        }
    }

    private boolean validateConditionalFields() {
        for (FieldDefinition field : selectedType.getFields()) {
            String value = fieldValues.get(field.getCode());
            if (field.isRequired() && (value == null || value.isBlank())) {
                Notification.show(getTranslation("wizard.field.required") + " — "
                                + field.label(locale()), 4000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return false;
            }
        }
        return true;
    }

    private void refreshPhotoPreview() {
        photosPreview.removeAll();
        photoCount.setText(photos.size() + "/" + props.maxPhotosPerReport());
        for (NamedPhoto photo : photos) {
            HorizontalLayout row = new HorizontalLayout();
            row.setAlignItems(Alignment.CENTER);
            var resource = new com.vaadin.flow.server.streams.DownloadHandler() {
                @Override
                public void handleDownloadRequest(com.vaadin.flow.server.streams.DownloadEvent event)
                        throws java.io.IOException {
                    event.setContentType("image/jpeg");
                    event.getOutputStream().write(photo.processed().thumbnail());
                }
            };
            Image thumb = new Image(resource, photo.name());
            thumb.setWidth("90px");
            thumb.getStyle().set("border-radius", "var(--lumo-border-radius-s)");
            Button remove = new Button(VaadinIcon.TRASH.create(), e -> {
                photos.remove(photo);
                refreshPhotoPreview();
            });
            remove.getElement().setAttribute("aria-label", getTranslation("common.delete"));
            row.add(thumb, new Span(photo.name()), remove);
            photosPreview.add(row);
        }
    }

    // ===== Étape 4 : contact + récapitulatif =====

    private void buildStep4() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.add(new Paragraph(getTranslation("wizard.contact.intro")));

        email.setLabel(getTranslation("wizard.contact.email"));
        email.setRequiredIndicatorVisible(true);
        email.setWidthFull();
        email.setMaxWidth("420px");
        email.setId("contact-email");

        phone.setLabel(getTranslation("wizard.contact.phone"));
        phone.setWidthFull();
        phone.setMaxWidth("420px");
        phone.setId("contact-phone");
        phone.setHelperText("+216 …");
        phone.getElement().setAttribute("dir", "ltr");

        consent.setLabel(getTranslation("wizard.consent"));
        consent.setId("consent-checkbox");

        summary.setPadding(false);
        summary.addClassName("report-card");

        layout.add(email, phone, consent, new H4(getTranslation("wizard.summary.title")), summary);
        steps.add(layout);
    }

    private void refreshSummary() {
        summary.removeAll();
        Locale locale = locale();
        if (selectedType != null) {
            summary.add(new Span(getTranslation("detail.category") + " : "
                    + selectedType.getGroup().label(locale) + " — " + selectedType.label(locale)));
        }
        if (pickedLat != null) {
            String position = (addressValue != null ? addressValue + " · " : "")
                    + String.format(Locale.ROOT, "%.5f, %.5f", pickedLat, pickedLon);
            summary.add(new Span(getTranslation("detail.address") + " : " + position));
        }
        if (selectedType != null && selectedType.isStandardDescription() && description.getValue() != null) {
            summary.add(new Span(getTranslation("detail.description") + " : " + description.getValue()));
        }
        summary.add(new Span(getTranslation("wizard.details.photos", props.maxPhotosPerReport())
                + " : " + photos.size()));
        Button edit = new Button(getTranslation("wizard.summary.edit"), e -> goToStep(0));
        edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        summary.add(edit);
    }

    // ===== Soumission =====

    private void submit() {
        String emailValue = email.getValue() == null ? "" : email.getValue().trim();
        if (emailValue.isEmpty() || email.isInvalid()) {
            email.setInvalid(true);
            email.setErrorMessage(getTranslation("wizard.contact.email.invalid"));
            return;
        }
        if (!Boolean.TRUE.equals(consent.getValue())) {
            Notification.show(getTranslation("wizard.consent.required"), 4000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        // Prévention des doublons avant création (ne bloque jamais un incident distinct).
        if (!duplicatesConfirmed) {
            List<ReportQueryService.DuplicateCandidate> candidates =
                    queries.findDuplicateCandidates(selectedType.getId(), pickedLon, pickedLat);
            if (!candidates.isEmpty()) {
                showDuplicatesDialog(candidates);
                return;
            }
        }
        doSubmit();
    }

    private void showDuplicatesDialog(List<ReportQueryService.DuplicateCandidate> candidates) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("wizard.duplicates.title"));
        VerticalLayout content = new VerticalLayout(new Paragraph(getTranslation("wizard.duplicates.intro")));
        Locale locale = locale();
        for (ReportQueryService.DuplicateCandidate candidate : candidates) {
            var s = candidate.summary();
            HorizontalLayout row = new HorizontalLayout();
            row.setAlignItems(Alignment.CENTER);
            row.add(new Span(s.typeLabel(locale) + " · " + s.reference()
                    + " · " + Math.round(candidate.distanceMeters()) + " m"));
            Anchor view = new Anchor("/requests/" + s.reference(), getTranslation("wizard.duplicates.view"));
            view.setTarget("_blank");
            row.add(view);
            content.add(row);
        }
        Button confirmDistinct = new Button(getTranslation("wizard.duplicates.confirm"), e -> {
            duplicatesConfirmed = true;
            dialog.close();
            doSubmit();
        });
        confirmDistinct.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        confirmDistinct.setId("confirm-distinct");
        Button cancel = new Button(getTranslation("common.cancel"), e -> dialog.close());
        dialog.add(content);
        dialog.getFooter().add(cancel, confirmDistinct);
        dialog.open();
    }

    private void doSubmit() {
        nextButton.setEnabled(false);
        try {
            rateLimiter.check("report:" + idempotencyKey.substring(0, 8), 5, 600);
            Map<String, String> values = new HashMap<>(fieldValues);
            Report created = reportService.create(new ReportService.CreateReportCommand(
                    selectedType.getId(), pickedLon, pickedLat,
                    addressValue, addressDetailsValue,
                    selectedType.isStandardDescription() ? description.getValue() : null,
                    values, emailValue(), phone.getValue(), true, idempotencyKey, locale(),
                    photos.stream().map(NamedPhoto::processed).toList()));
            showSuccess(created);
        } catch (ValidationException ex) {
            nextButton.setEnabled(true);
            String message = switch (ex.getMessage() == null ? "" : ex.getMessage()) {
                case "position.outside" -> getTranslation("wizard.position.outside");
                case "phone.invalid" -> getTranslation("wizard.contact.phone.invalid");
                case "email.invalid" -> getTranslation("wizard.contact.email.invalid");
                case "consent.required" -> getTranslation("wizard.consent.required");
                case "description.invalid" ->
                        getTranslation("wizard.details.description.required", ReportService.DESCRIPTION_MAX);
                default -> getTranslation("wizard.error");
            };
            Notification.show(message, 6000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        } catch (Exception ex) {
            nextButton.setEnabled(true);
            Notification.show(getTranslation("common.error"), 6000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private String emailValue() {
        return email.getValue() == null ? "" : email.getValue().trim();
    }

    private void showSuccess(Report created) {
        removeAll();
        VerticalLayout success = new VerticalLayout();
        success.setAlignItems(Alignment.CENTER);
        success.addClassName("report-card");
        Icon check = VaadinIcon.CHECK_CIRCLE.create();
        check.setSize("56px");
        check.setColor("var(--lumo-success-color)");
        H2 successTitle = new H2(getTranslation("wizard.success.title"));
        Span reference = new Span(getTranslation("wizard.success.reference", created.getReference()));
        reference.setId("success-reference");
        reference.getStyle().set("font-weight", "700").set("font-size", "var(--lumo-font-size-l)");
        Paragraph body = new Paragraph(getTranslation("wizard.success.body"));
        body.getStyle().set("max-width", "560px");
        Anchor view = new Anchor("/requests/" + created.getReference(),
                getTranslation("wizard.success.view"));
        success.add(check, successTitle, reference, body, view);
        add(success);
    }
}
