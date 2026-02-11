package rover.model.rstp;

/**
 * RSTP port states as defined in IEEE 802.1w.
 *
 * @author Benjamin FOUILHAC, ADM21 - January/February 2026
 */
public enum RSTPPortState {
    UNKNOWN("Unknown"),
    DISABLED("Disabled"),
    BLOCKING("Blocking"),
    LISTENING("Listening"),
    LEARNING("Learning"),
    FORWARDING("Forwarding"),
    BROKEN("Broken"),
    DISCARDING("Discarding");

    private final String displayName;

    RSTPPortState(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}