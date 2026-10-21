package dev.nglmercer.tiktokminecraft.actions;

/**
 * Backward-compatible alias for the generic {@link BlockCageAction}.
 *
 * Existing "tnt-cage" rules default to TNT even without a material property.
 * The registry maps "block-cage" to the same instance so both types share
 * overlap detection, restoration state and shutdown cleanup.
 */
public final class TntCageAction extends BlockCageAction {
    @Override
    public String type() {
        return "tnt-cage";
    }
}
