package serverutils.mixins.late.hbm;

import com.hbm.entity.logic.EntityNukeExplosionMK5;
import cpw.mods.fml.common.FMLLog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import serverutils.data.WarManager;

@Mixin(EntityNukeExplosionMK5.class)
public class NukeGuardMixin {
    @Shadow
    public int strength;

    @Inject(method = "onUpdate", at = @At("HEAD"))
    private void blegh(CallbackInfo ci){
        if (!WarManager.isWarActive()) {
            strength = 0;
            FMLLog.info("mixin worked ig");
        }
    }
}
