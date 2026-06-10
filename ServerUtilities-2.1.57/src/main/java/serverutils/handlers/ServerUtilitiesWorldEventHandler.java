 package serverutils.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.event.world.WorldEvent;

import serverutils.lib.data.ForgePlayer;
import serverutils.lib.data.ForgeTeam;
import serverutils.lib.data.Universe;
import serverutils.data.WarManager;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import serverutils.ServerUtilities;
import serverutils.ServerUtilitiesConfig;
import serverutils.data.ClaimedChunk;
import serverutils.data.ClaimedChunks;
import serverutils.data.ServerUtilitiesUniverseData;
import serverutils.lib.math.ChunkDimPos;
import serverutils.pregenerator.ChunkLoaderManager;

public class ServerUtilitiesWorldEventHandler {

    public static final ServerUtilitiesWorldEventHandler INST = new ServerUtilitiesWorldEventHandler();
    private static final ThreadLocal<Explosion> currentExplosion = new ThreadLocal<>();

    public static Explosion getCurrentExplosion() {
        return currentExplosion.get();
    }

    public static void setCurrentExplosion(Explosion explosion) {
        currentExplosion.set(explosion);
    }

    public static void clearCurrentExplosion() {
        currentExplosion.remove();
    }

    @SubscribeEvent
    public void onMobSpawned(EntityJoinWorldEvent event) {
        if (!event.world.isRemote && !isEntityAllowed(event.entity)) {
            event.entity.setDead();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onDimensionUnload(WorldEvent.Unload event) {
        if (ClaimedChunks.isActive() && event.world.provider.dimensionId != 0) {
            ClaimedChunks.instance.markDirty();
        }
    }

    private static boolean isEntityAllowed(Entity entity) {
        if (entity instanceof EntityPlayer) {
            return true;
        }

        if (ServerUtilitiesConfig.world.safe_spawn
                && ServerUtilitiesUniverseData.isInSpawn(MinecraftServer.getServer(), new ChunkDimPos(entity))) {
            if (entity instanceof IMob) {
                return false;
            } else {
                return !(entity instanceof EntityChicken) || entity.riddenByEntity == null;
            }
        }

        return true;
    }

    @SubscribeEvent
    public void onExplosionDetonate(ExplosionEvent.Detonate event) {
        World world = event.world;

        if (world.isRemote || event.getAffectedBlocks().isEmpty()) {
            return;
        }

        Explosion explosion = event.explosion;
        List<ChunkPosition> list = new ArrayList<>(event.getAffectedBlocks());
        event.getAffectedBlocks().clear();

        for (ChunkPosition pos : list) {
            ChunkDimPos dimPos = new ChunkDimPos(pos.chunkPosX, pos.chunkPosZ, world.provider.dimensionId);
            if (canExplosionAffect(world, dimPos, explosion)) {
                event.getAffectedBlocks().add(pos);
            }
        }
    }

    public static boolean canExplosionAffect(World world, ChunkDimPos pos) {
        return canExplosionAffect(world, pos, null);
    }

    public static boolean canExplosionAffect(World world, ChunkDimPos pos, Explosion explosion) {
        if (pos.dim == 0 && ServerUtilitiesConfig.world.safe_spawn
                && ServerUtilitiesUniverseData.isInSpawn(MinecraftServer.getServer(), pos)) {
            return false;
        }

        if (ServerUtilitiesConfig.world.enable_explosions.isDefault()) {
            ClaimedChunk chunk = ClaimedChunks.isActive() ? ClaimedChunks.instance.getChunk(pos) : null;
            if (chunk == null || chunk.hasExplosions()) {
                return true;
            }
            if (explosion == null) {
                return isClaimTeamAtWar(chunk.getTeam());
            }
            return isExplosionAllowedByWar(explosion, chunk.getTeam());
        }

        return ServerUtilitiesConfig.world.enable_explosions.isTrue();
    }

    private static boolean isClaimTeamAtWar(ForgeTeam claimTeam) {
        if (claimTeam == null) {
            return false;
        }
        return !WarManager.get().getWarringTeams(claimTeam.getId()).isEmpty();
    }

    private static boolean isExplosionAllowedByWar(Explosion explosion, ForgeTeam claimTeam) {
        if (explosion == null || claimTeam == null) {
            return false;
        }

        Entity source = explosion.exploder;
        if (!(source instanceof EntityPlayerMP)) {
            try {
                EntityPlayerMP player = explosion.getExplosivePlacedBy() instanceof EntityPlayerMP ?
                        (EntityPlayerMP) explosion.getExplosivePlacedBy() : null;
                source = player;
            } catch (Throwable ignored) {
                source = null;
            }
        }

        if (!(source instanceof EntityPlayerMP)) {
            return false;
        }

        ForgePlayer player = Universe.get().getPlayer((EntityPlayerMP) source);
        if (player == null || !player.hasTeam()) {
            return false;
        }

        return WarManager.get().isAtWar(player.team, claimTeam);
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (!event.world.isRemote) {
            int dimensionId = event.world.provider.dimensionId;
            MinecraftServer server = MinecraftServer.getServer();
            if (!ChunkLoaderManager.instance.isGenerating()
                    && ChunkLoaderManager.instance.initializeFromPregeneratorFiles(server, dimensionId)) {
                ServerUtilities.LOGGER.info("Pregenerator loaded and running for dimension Id: " + dimensionId);
            }
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (!event.world.isRemote) {
            if (event.world.provider.dimensionId == ChunkLoaderManager.instance.getDimensionID()
                    && ChunkLoaderManager.instance.isGenerating()) {
                ChunkLoaderManager.instance.reset(false);
            }
        }
    }
}
