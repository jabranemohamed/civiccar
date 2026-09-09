package tn.civiccare.open311;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tn.civiccare.AbstractIntegrationTest;
import tn.civiccare.catalog.CatalogService;
import tn.civiccare.reports.PublicationStatus;
import tn.civiccare.reports.Report;
import tn.civiccare.reports.ReportService;
import tn.civiccare.reports.WorkflowStatus;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * A14 : contrat Open311 GET JSON/XML, mapping open/closed, validation des paramètres,
 * exclusion des données privées et des dossiers non publiés.
 */
@AutoConfigureMockMvc
class Open311ContractIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ReportService reports;
    @Autowired
    CatalogService catalog;
    @Autowired
    org.springframework.transaction.PlatformTransactionManager txManager;

    private Report create(String email, WorkflowStatus status, PublicationStatus publication) {
        Report report = reports.create(new ReportService.CreateReportCommand(
                catalog.typeByCode("FOUNTAIN_DIRTY").orElseThrow().getId(), 10.1815, 36.7995,
                "Place publique, Tunis", null, "Fontaine sale.", Map.of(), email, null, true,
                UUID.randomUUID().toString(), Locale.forLanguageTag("fr"), List.of()));
        new org.springframework.transaction.support.TransactionTemplate(txManager)
                .executeWithoutResult(tx -> {
                    var r = reports.byReference(report.getReference()).orElseThrow();
                    r.setWorkflowStatus(status);
                    r.setPublicationStatus(publication);
                    if (status.isTerminal()) {
                        r.setClosedAt(java.time.Instant.now());
                    }
                });
        return report;
    }

    @Test
    void servicesJsonListsCatalog() throws Exception {
        mvc.perform(get("/api/georeport/v2/services.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(40)))
                .andExpect(jsonPath("$[0].service_code").exists())
                .andExpect(jsonPath("$[0].group").exists());
    }

    @Test
    void servicesXmlIsWellFormed() throws Exception {
        mvc.perform(get("/api/georeport/v2/services.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/xml"))
                .andExpect(xpath("/services/service").nodeCount(40))
                .andExpect(xpath("/services/service[1]/service_code").exists());
    }

    @Test
    void requestsMappingOpenClosed_privateDataExcluded() throws Exception {
        Report open = create("open311-open@example.com", WorkflowStatus.IN_PROGRESS, PublicationStatus.PUBLISHED);
        Report closed = create("open311-closed@example.com", WorkflowStatus.OUT_OF_SCOPE, PublicationStatus.PUBLISHED);
        create("open311-hidden@example.com", WorkflowStatus.OPEN, PublicationStatus.PENDING_REVIEW);

        // IN_PROGRESS -> open
        mvc.perform(get("/api/georeport/v2/requests/" + open.getReference() + ".json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("open"))
                .andExpect(jsonPath("$[0].service_request_id").value(open.getReference()))
                .andExpect(jsonPath("$[0].lat").isNumber())
                .andExpect(content().string(not(containsString("open311-open@example.com"))));

        // OUT_OF_SCOPE -> closed (mapping conservé même archivé)
        mvc.perform(get("/api/georeport/v2/requests/" + closed.getReference() + ".json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("closed"));

        // XML : mêmes projections publiques
        mvc.perform(get("/api/georeport/v2/requests/" + open.getReference() + ".xml"))
                .andExpect(status().isOk())
                .andExpect(xpath("/service_requests/request/status").string("open"))
                .andExpect(content().string(not(containsString("open311-open@example.com"))));
    }

    @Test
    void unpublishedReportIsNotFoundByReference() throws Exception {
        Report hidden = create("nf@example.com", WorkflowStatus.OPEN, PublicationStatus.HIDDEN);
        mvc.perform(get("/api/georeport/v2/requests/" + hidden.getReference() + ".json"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("nf@example.com"))));
    }

    @Test
    void dateRangeValidation() throws Exception {
        // Plage > 90 jours refusée
        mvc.perform(get("/api/georeport/v2/requests.json")
                        .param("start_date", "2026-01-01T00:00:00Z")
                        .param("end_date", "2026-06-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$[0].code").value(400));
        // Dates mal formées refusées
        mvc.perform(get("/api/georeport/v2/requests.json").param("start_date", "hier"))
                .andExpect(status().isBadRequest());
        // start > end refusé
        mvc.perform(get("/api/georeport/v2/requests.json")
                        .param("start_date", "2026-05-01T00:00:00Z")
                        .param("end_date", "2026-04-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void jurisdictionAndStatusValidation() throws Exception {
        mvc.perform(get("/api/georeport/v2/requests.json").param("jurisdiction_id", "tunis"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/georeport/v2/requests.json").param("jurisdiction_id", "munich"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/georeport/v2/requests.json").param("status", "pending"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void serviceCodeFilterWorks() throws Exception {
        create("filter@example.com", WorkflowStatus.OPEN, PublicationStatus.PUBLISHED);
        mvc.perform(get("/api/georeport/v2/requests.json").param("service_code", "FOUNTAIN_DIRTY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].service_code", everyItem(is("FOUNTAIN_DIRTY"))));
    }
}
