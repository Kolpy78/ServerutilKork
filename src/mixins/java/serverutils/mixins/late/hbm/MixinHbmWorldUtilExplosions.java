package serverutils.mixins.late.hbm;

import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import serverutils.ServerUtilities;
import serverutils.ServerUtilitiesConfig;
import serverutils.handlers.ServerUtilitiesWorldEventHandler;
import serverutils.lib.math.ChunkDimPos;
import serverutils.data.ClaimedChunks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "com.hbm.world.WorldUtil")
public abstract class MixinHbmWorldUtilExplosions {

    /**
     * Intercepts HBM's explosion gate to enforce ServerUtilities claim protection and war bypass.
     * 
     * Returns false to prevent block destruction if the target chunk is claimed and explosions
     * are disabled. Allows destruction if the chunk owner's team is at war with the attacker.
     */
    @Overwrite(remap = false)
    public static boolean canExplosionAffect(World world, int x, int y, int z) {
        if (world == null || !ClaimedChunks.isActive()) {
            return true;
        }

        // ChunkDimPos(x, y, z, dim) takes block coords and shifts internally
        ChunkDimPos targetChunkPos = new ChunkDimPos(x, y, z, world.provider.dimensionId);

        Explosion currentExplosion = ServerUtilitiesWorldEventHandler.getCurrentExplosion();

        boolean allowed = ServerUtilitiesWorldEventHandler.canExplosionAffect(world, targetChunkPos, currentExplosion);

        if (!allowed && ServerUtilitiesConfig.world.log_hbm_explosion_checks) {
            ServerUtilities.LOGGER.info(
                    "HBM explosion blocked at chunk {} (block {},{},{}) by ServerUtilities claimed chunk protection",
                    targetChunkPos, x, y, z);
        }

        return allowed;
    }
}
