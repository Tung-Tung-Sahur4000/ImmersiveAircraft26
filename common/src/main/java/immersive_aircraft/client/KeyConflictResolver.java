package immersive_aircraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import immersive_aircraft.mixin.client.KeyMappingAccessorMixin;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

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
     * What this class last assigned, by mapping name. Minecraft writes every
     * binding to options.txt when it shuts down, so without a record of our own
     * an automatic move becomes indistinguishable from a deliberate one - and
     * the checks below would then refuse to touch the key again, leaving the
     * next conflict for the player to sort out by hand. Shared by every mod that
     * calls in; mapping names are already namespaced.
     */
    private static final File RECORD = new File("./config/immersive_keybinds.properties");

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

        Properties record = loadRecord(log);
        boolean changed = false;

        // Hand back anything we assigned before deciding anything, so each
        // launch resolves from the defaults. A conflict that has since gone away
        // - the other mod was removed - gives the original key back on its own.
        for (KeyMapping ourKey : ours) {
            if (isManaged(ourKey) && describe(keyOf(ourKey)).equals(record.getProperty(ourKey.getName()))) {
                ourKey.setKey(ourKey.getDefaultKey());
                changed = true;
            }
        }

        List<KeyMapping> others = new ArrayList<>();
        for (KeyMapping candidate : client.options.keyMappings) {
            if (!ours.contains(candidate)) {
                others.add(candidate);
            }
        }

        for (KeyMapping ourKey : ours) {
            if (!isManaged(ourKey)) {
                continue;
            }
            if (ourKey.isUnbound() || !ourKey.isDefault()) {
                // Unbound, or the player picked this themselves - not ours to change.
                record.remove(ourKey.getName());
                continue;
            }

            KeyMapping clash = findClash(ourKey, others);
            if (clash == null) {
                record.remove(ourKey.getName());
                continue;
            }

            InputConstants.Key free = findFreeKey(others);
            if (free == null) {
                record.remove(ourKey.getName());
                log.warn("[keybinds] {} clashes with {} and no free fallback key was available; "
                                + "please rebind one of them manually",
                        ourKey.getName(), clash.getName());
                continue;
            }

            InputConstants.Key was = keyOf(ourKey);
            ourKey.setKey(free);
            record.setProperty(ourKey.getName(), describe(free));
            changed = true;
            log.info("[keybinds] moved {} from {} to {} because {} already uses {}",
                    ourKey.getName(), describe(was), describe(free), clash.getName(), describe(was));
        }

        if (changed) {
            KeyMapping.resetMapping();
        }
        saveRecord(log, record);
    }

    /**
     * Movement keys deliberately mirror the player's own WASD/jump/sneak, so a
     * "clash" there is the entire point.
     */
    private static boolean isManaged(KeyMapping mapping) {
        return !(mapping instanceof MultiKeyMapping) && !(mapping instanceof FallbackKeyMapping);
    }

    private static KeyMapping findClash(KeyMapping ourKey, List<KeyMapping> others) {
        for (KeyMapping other : others) {
            if (!other.isUnbound() && !isChord(other) && other.same(ourKey)) {
                return other;
            }
        }
        return null;
    }

    /**
     * Vanilla's debug bindings (F3+B for hitboxes, F3+H for tooltips, ...) are
     * registered as ordinary mappings on B and H, but only fire while F3 is
     * held. Treating them as conflicts would shuffle perfectly usable keys for
     * no reason, so they do not count - here or when looking for a free key.
     */
    private static boolean isChord(KeyMapping mapping) {
        return mapping.getName().startsWith("key.debug.");
    }

    private static InputConstants.Key findFreeKey(List<KeyMapping> others) {
        for (int code : FALLBACKS) {
            InputConstants.Key candidate = InputConstants.Type.KEYSYM.getOrCreate(code);
            boolean taken = false;
            for (KeyMapping other : others) {
                if (!isChord(other) && candidate.equals(keyOf(other))) {
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

    private static Properties loadRecord(Logger log) {
        Properties record = new Properties();
        if (RECORD.exists()) {
            try (FileReader reader = new FileReader(RECORD)) {
                record.load(reader);
            } catch (IOException e) {
                // Worst case we treat an automatic move as the player's own and
                // leave the key alone, which is the safe direction to fail in.
                log.warn("[keybinds] could not read {}: {}", RECORD, e.toString());
            }
        }
        return record;
    }

    private static void saveRecord(Logger log, Properties record) {
        try {
            if (record.isEmpty()) {
                //noinspection ResultOfMethodCallIgnored
                RECORD.delete();
                return;
            }
            //noinspection ResultOfMethodCallIgnored
            RECORD.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(RECORD)) {
                record.store(writer, "Keys moved automatically because another mod already used the default. "
                        + "Delete this file to start over; rebinding a key in-game also releases it.");
            }
        } catch (IOException e) {
            log.warn("[keybinds] could not write {}: {}", RECORD, e.toString());
        }
    }

    private static InputConstants.Key keyOf(KeyMapping mapping) {
        return ((KeyMappingAccessorMixin) mapping).getKey();
    }

    private static String describe(InputConstants.Key key) {
        return key == null ? "<none>" : key.getName();
    }
}
