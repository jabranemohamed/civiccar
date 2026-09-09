package tn.civiccare.reports;

/** Publication indépendante du traitement : un dossier non publié est néanmoins traité. */
public enum PublicationStatus {
    PENDING_REVIEW,
    PUBLISHED,
    HIDDEN
}
