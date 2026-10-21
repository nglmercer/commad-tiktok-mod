package dev.nglmercer.tiktokminecraft.tiktok;

/** The observable state of the TikTok LIVE connection. */
public enum LiveConnectionState {
    /** No connection has been attempted yet. */
    IDLE,
    /** A connection attempt is in flight. */
    CONNECTING,
    /** The room is live and events are flowing. */
    CONNECTED,
    /** The SDK lost the connection and is retrying. */
    RECONNECTING,
    /** The creator is not live. */
    OFFLINE,
    /** The connection failed. */
    ERROR,
    /** The service was shut down. */
    STOPPED;

    public boolean isActive() {
        return this == CONNECTING || this == CONNECTED || this == RECONNECTING;
    }
}
