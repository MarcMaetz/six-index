package com.example.indexreviewer.review;

/** Why a ranked security is or is not in the new composition (rulebook 5.12.3.2). */
public enum SelectionDecision {
    /** Ranked within the direct selection ranks. */
    SELECTED_DIRECT(true),
    /** In the buffer and a current constituent, which have priority. */
    SELECTED_BUFFER_INCUMBENT(true),
    /** In the buffer, not a current constituent, and a slot was left after the incumbents. */
    SELECTED_BUFFER_NEW(true),
    /** In the buffer, but all slots were taken by incumbents or higher-ranked candidates. */
    NOT_SELECTED_BUFFER_FULL(false),
    /** Ranked below the buffer. */
    NOT_SELECTED_BELOW_BUFFER(false);

    private final boolean selected;

    SelectionDecision(boolean selected) {
        this.selected = selected;
    }

    public boolean selected() {
        return selected;
    }
}
