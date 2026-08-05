package immersive_aircraft.mixin.client;

import immersive_aircraft.entity.VehicleEntity;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    // Camera.setup(Level, Entity, boolean, boolean, float) no longer exists on
    // 26.1; alignWithEntity is the surviving hook that positions the camera
    // relative to its entity, so the extra vehicle zoom goes there.
    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    public void ia$alignWithEntity(float partialTicks, CallbackInfo ci) {
        Camera self = (Camera) (Object) this;
        Entity entity = self.entity();
        if (self.isDetached() && entity != null && entity.getVehicle() instanceof VehicleEntity vehicle) {
            move(-getMaxZoom((float) vehicle.getZoom()), 0.0f, 0.0f);
        }
    }

    @Shadow
    protected abstract void move(float zoom, float dy, float dx);

    @Shadow
    protected abstract float getMaxZoom(float maxZoom);
}