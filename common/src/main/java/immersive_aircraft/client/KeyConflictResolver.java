package immersive_aircraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import immersive_aircraft.mixin.client.KeyMappingAccessorMixin;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Moves this mod's keys off any binding another mod already claimed.
 * <p>
 * Defaults collide: dismount sits on R, which Iris also uses to reload shaders.
 * The player then presses R, the other mod answers, and the machine never lets
 * them off - with nothing to suggest the cause. Rather than make everyone find
 * that in the controls menu, a clashing key is moved to a free one at startup
 * and the change is written to the log.
 */
public final class KeyConflictResolver {
    private KeyConflictResolver() {
    }

    /**
     * Keys vanilla leaves unbound, in preference order. The first one nothing
     * else has claimed wins.
     */
    private static final int[] FALLBACKS = {
            GLFW.GLFW_KEY_G, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_N, GLFW.GLFW_KEY_Z,
            GLFW.GLFW_KEY_J, GLFW.GLFW_KEY_K, GLFW.GLFW_KEY_L, GLFW.GLFW_KEY_Y,
            GLFW.GLFW_KEY_U, GLFW.GLFW_KEY_I, GLFW.GLFW_KEY_O, GLFW.GLFW_KEY_P,
            GLFW.GLFW_KEY_M, GLFW.GLFW_KEY_COMMA, GLFW.GLFW_KEY_PERIOD,
            GLFW.GLFW_KEY_SEMICOLON, GLFW.GLFW_KEY_APOSTROPHE,
    };

    /**
     * Call once the client is up, after every mod has registered its keys and
     * the player's own bindings have been loaded from options.
     *
     * @param ours keys belonging to the calling mod; only these are ever moved
     */
    public static void resolve(Logger log, List<KeyMapping> ours) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.options == null) {
            return;
        }

        List<KeyMapping> others = new ArrayList<>();
        for (KeyMapping candidate : client.options.keyMappings) {
            if (!ours.contains(candidate)) {
                others.add(candidate);
            }
        }

        boolean changed = false;
        for (KeyMapping ourKey : ours) {
            // Movement keys deliberately mirror the player's own WASD/jump/sneak,
            // so a "clash" there is the entire point. Leave them alone.
            if (ourKey instanceof MultiKeyMapping || ourKey instanceof FallbackKeyMapping) {
                continue;
            }
            if (ourKey.isUnbound() || !ourKey.isDefault()) {
                // Unbound, or the player picked this themselves - not ours to change.
                continue;
            }

            KeyMapping clash = findClash(ourKey, others);
            if (clash == null) {
                continue;
            }

            InputConstants.Key free = findFreeKey(others);
            if (free == null) {
                log.warn("[keybinds] {} clashes with {} and no free fallback key was available; "
                                + "please rebind one of them manually",
                        ourKey.getName(), clash.getName());
                continue;
            }

            InputConstants.Key was = keyOf(ourKey);
            ourKey.setKey(free);
            changed = true;
            log.info("[keybinds] moved {} from {} to {} because {} already uses {}",
                    ourKey.getName(), describe(was), describe(free), clash.getName(), describe(was));
        }

        if (changed) {
            KeyMapping.resetMapping();
            client.options.save();
        }
    }

    private static KeyMapping findClash(KeyMapping ourKey, List<KeyMapping> others) {
        for (KeyMapping other : others) {
            if (!other.isUnbound() && other.same(ourKey)) {
                return other;
            }
        }
        return null;
    }

    private static InputConstants.Key findFreeKey(List<KeyMapping> others) {
        for (int code : FALLBACKS) {
            InputConstants.Key candidate = InputConstants.Type.KEYSYM.getOrCreate(code);
            boolean taken = false;
            for (KeyMapping other : others) {
                if (candidate.equals(keyOf(other))) {
                    taken = true;
                    break;
                }
            }
            if (!taken) {
                return candidate;
            }
        }
        return null;
    }

    private static InputConstants.Key keyOf(KeyMapping mapping) {
        return ((KeyMappingAccessorMixin) mapping).getKey();
    }

    private static String describe(InputConstants.Key key) {
        return key == null ? "<none>" : key.getName();
    }
}
