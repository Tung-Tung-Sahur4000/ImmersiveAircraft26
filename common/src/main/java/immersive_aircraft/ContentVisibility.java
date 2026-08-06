package immersive_aircraft;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Hides a mod's content on servers that will not run it.
 * <p>
 * On a server without the mod installed, its items are still in the client's
 * registry, so the creative tab and the key bindings are all still there -
 * offering things the server will refuse and controls that answer to nothing.
 * There is no way to tell that from looking; it just quietly does not work.
 * <p>
 * This lives in common and holds only suppliers, because the creative tab is
 * registered on both sides: a dedicated server resolving {@code Minecraft}
 * would die on the spot. The client installs the real check at startup, and
 * until it does the answer is "show everything", which is what a server needs.
 */
public final class ContentVisibility {
    private ContentVisibility() {
    }

    /** Answers whether this client is on a server it does not control. */
    private static BooleanSupplier onMultiplayer = () -> false;

    /** Key lists to leave out of the controls screen, each with its own toggle. */
    private static final List<HiddenKeys> HIDDEN_KEYS = new ArrayList<>();

    private record HiddenKeys(List<?> keys, BooleanSupplier enabled) {
    }

    public static void setMultiplayerCheck(BooleanSupplier check) {
        onMultiplayer = check;
    }

    public static boolean onMultiplayer() {
        return onMultiplayer.getAsBoolean();
    }

    /**
     * @param enabled the owning mod's config toggle, read each time rather than
     *                captured, so turning it off takes effect without a restart
     */
    public static boolean hidden(BooleanSupplier enabled) {
        return enabled.getAsBoolean() && onMultiplayer();
    }

    /**
     * Registers keys to leave out of the controls screen while hidden. Held as
     * {@code List<?>} so nothing here drags a client class onto a server.
     */
    public static void hideKeys(List<?> keys, BooleanSupplier enabled) {
        HIDDEN_KEYS.add(new HiddenKeys(keys, enabled));
    }

    /** True if this key belongs to a mod that is currently hiding itself. */
    public static boolean isHiddenKey(Object key) {
        return wouldHideKey(key, onMultiplayer());
    }

    /**
     * The same answer for a stated situation rather than the current one, so a
     * self-check can drive it both ways without swapping the global check out
     * and back - which is its own trap: a method reference to the getter reads
     * whatever the field holds, so restoring one makes it call itself forever.
     */
    public static boolean wouldHideKey(Object key, boolean onMultiplayer) {
        for (HiddenKeys entry : HIDDEN_KEYS) {
            if (entry.keys().contains(key)) {
                return onMultiplayer && entry.enabled().getAsBoolean();
            }
        }
        return false;
    }
}
