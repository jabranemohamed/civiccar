package tn.civiccare.reports;

import java.util.Set;

/**
 * Statut de traitement. DONE_OR_ORDERED signifie « traité ou intervention commandée » :
 * il ne garantit pas une réparation physique effective.
 */
public enum WorkflowStatus {
    OPEN,
    IN_PROGRESS,
    DONE_OR_ORDERED,
    OUT_OF_SCOPE;

    public boolean isTerminal() {
        return this == DONE_OR_ORDERED || this == OUT_OF_SCOPE;
    }

    /** Transitions autorisées. La réouverture d'un état terminal exige autorisation et motif. */
    public Set<WorkflowStatus> allowedTransitions() {
        return switch (this) {
            case OPEN -> Set.of(IN_PROGRESS, DONE_OR_ORDERED, OUT_OF_SCOPE);
            case IN_PROGRESS -> Set.of(DONE_OR_ORDERED, OUT_OF_SCOPE);
            case DONE_OR_ORDERED, OUT_OF_SCOPE -> Set.of(IN_PROGRESS);
        };
    }

    /** Mapping Open311 documenté : OPEN/IN_PROGRESS -> open ; états terminaux -> closed. */
    public String open311Status() {
        return isTerminal() ? "closed" : "open";
    }
}
