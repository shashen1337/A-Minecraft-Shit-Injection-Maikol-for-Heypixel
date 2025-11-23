package Maikol.feature.impl.combat;

import Maikol.feature.Category;
import Maikol.feature.Module;
import Maikol.feature.Value;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.Comparator;
import java.util.List;

public class CrystalAura extends Module {

    private final Minecraft mc = Minecraft.getInstance();
    private final Value<Double> RANGE = new Value<>("AttackRange", 3.1);
    private final Value<Integer> DELAY = new Value<>("DelayTick", 0);

    private int tickCounter = 0;
    private boolean hasRotated = false;

    public CrystalAura() {
        super("CrystalAura");
        category = Category.combat;
    }

    @Override
    protected void onEnable() {
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.NORMAL,
                false,
                TickEvent.ClientTickEvent.class,
                this::onClientTick
        );
    }

    @Override
    protected void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
        tickCounter = 0;
        hasRotated = false;
    }

    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!enabled) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        if (DELAY.getValue() > 0) {
            tickCounter++;
            if (tickCounter < DELAY.getValue()) return;
            tickCounter = 0;
        }

        Entity crystal = findNearestCrystal(player);
        if (crystal == null) return;

        attackCrystal(player, crystal);
    }

    private Entity findNearestCrystal(LocalPlayer player) {
        List<Entity> entities = mc.level.getEntities(player,
                player.getBoundingBox().inflate(RANGE.getValue()));

        return entities.stream()
                .filter(this::isValidCrystal)
                .filter(e -> canSeeEntity(player, e))
                .min(Comparator.comparingDouble(e -> player.distanceToSqr(e)))
                .orElse(null);
    }

    private boolean isValidCrystal(Entity entity) {
        if (entity == null) return false;
        String entityName = entity.getClass().getSimpleName().toLowerCase();
        if (!entityName.contains("crystal")) return false;

        if (entity instanceof ArmorStand || entity instanceof ItemEntity ||
                entity instanceof EnderDragon) {
            return false;
        }

        return entity.isAlive();
    }

    private boolean canSeeEntity(LocalPlayer player, Entity entity) {
        Vec3 playerEye = player.getEyePosition();
        Vec3 entityCenter = entity.getBoundingBox().getCenter();
        var hitResult = mc.level.clip(new net.minecraft.world.level.ClipContext(
                playerEye,
                entityCenter,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                player
        ));
        return hitResult.getType() == net.minecraft.world.phys.HitResult.Type.MISS ||
                hitResult.getLocation().distanceToSqr(entityCenter) < 0.5;
    }

    private void attackCrystal(LocalPlayer player, Entity crystal) {
        float[] rotation = getRotationTo(player, crystal);
        float targetYaw = rotation[0];
        float targetPitch = rotation[1];
        sendRotationPacket(targetYaw, targetPitch);
        mc.getConnection().send(ServerboundInteractPacket.createAttackPacket(
                crystal,
                player.isShiftKeyDown()
        ));
        player.swing(InteractionHand.MAIN_HAND);
    }

    private float[] getRotationTo(LocalPlayer player, Entity entity) {
        Vec3 playerEye = player.getEyePosition();
        Vec3 targetPos = entity.getBoundingBox().getCenter();

        double dx = targetPos.x - playerEye.x;
        double dy = targetPos.y - playerEye.y;
        double dz = targetPos.z - playerEye.z;

        double distXZ = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, distXZ)));
        yaw = wrapDegrees(yaw);
        pitch = Math.max(-90.0f, Math.min(90.0f, pitch));

        return new float[]{yaw, pitch};
    }

    private float wrapDegrees(float degrees) {
        degrees = degrees % 360.0f;
        if (degrees >= 180.0f) {
            degrees -= 360.0f;
        }
        if (degrees < -180.0f) {
            degrees += 360.0f;
        }
        return degrees;
    }

    private void sendRotationPacket(float yaw, float pitch) {
        LocalPlayer player = mc.player;
        if (player == null || mc.getConnection() == null) return;
        mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
                player.getX(),
                player.getY(),
                player.getZ(),
                yaw,
                pitch,
                player.onGround()
        ));
    }
}