package immersive_aircraft.mixin.client;

import immersive_aircraft.ContentVisibility;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;

/**
 * Leaves hidden mods' keys out of the controls screen.
 * <p>
 * Redirects the one field read the list builds itself from, rather than
 * removing the keys from {@link Options#keyMappings}: that array is also what
 * the game writes to options.txt, so a key missing from it at the wrong moment
 * loses whatever the player had bound to it.
 */
@Mixin(KeyBindsList.class)
public class KeyBindsListMixin {
    @Redirect(
            method = "<init>",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/Options;keyMappings:[Lnet/minecraft/client/KeyMapping;",
                    opcode = Opcodes.GETFIELD
            )
    )
    private KeyMapping[] immersiveAircraft$hideOurKeys(Options options) {
        List<KeyMapping> visible = new ArrayList<>(options.keyMappings.length);
        for (KeyMapping key : options.keyMappings) {
            if (!ContentVisibility.isHiddenKey(key)) {
                visible.add(key);
            }
        }
        return visible.toArray(new KeyMapping[0]);
    }
}
