/*package Maikol.feature.impl.combat;
import Maikol.feature.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class KillAura extends Module {
    private static final KillAura INSTANCE = new KillAura();
    public static KillAura getInstance() { return INSTANCE; }

    private LivingEntity currentTarget = null;
    private static final float ROTATION_SPEED = 20.0f;
    private float serverYaw = 0f;
    private float serverPitch = 0f;
    private boolean isLockedOnTarget = false;
    private float lastRealYaw = 0f;
    private float lastRealPitch = 0f;
    private boolean needsViaFix = false;
    private final double cps = 15.0;
    private final double attackDelay = 20.0 / cps;
    private double attackTimer = 0.0;

    public KillAura() {
        super("KillAura");
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
        isLockedOnTarget = false;
        currentTarget = null;
    }

    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!enabled || player == null || mc.level == null) {
            isLockedOnTarget = false;
            return;
        }

        lastRealYaw = player.getYRot();
        lastRealPitch = player.getXRot();
        detectViaFixNeeded(mc);
        double searchDistance = 3.02 + ThreadLocalRandom.current().nextDouble() * 0.1;
        if (currentTarget == null || !currentTarget.isAlive() || currentTarget.distanceTo(player) > searchDistance) {
            currentTarget = findClosestTarget(player, searchDistance);
            isLockedOnTarget = currentTarget != null;
        }
        if (currentTarget == null || !isLockedOnTarget) return;
        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 targetPos = calculateTargetPosition(currentTarget);
        float[] targetRotations = calculateServerRotations(eyePos, targetPos);
        float targetYaw = targetRotations[0];
        float targetPitch = targetRotations[1];
        serverYaw = smoothRotation(serverYaw, targetYaw, ROTATION_SPEED);
        serverPitch = smoothRotation(serverPitch, targetPitch, ROTATION_SPEED);
        applySilentMoveFix(player, serverYaw);
        updateHeadAndBodyRotation(player, serverYaw);
        restoreFirstPersonView(player, lastRealYaw, lastRealPitch);
        attackTimer += 1.0;
        if (attackTimer >= attackDelay) {
            if (holdWeapon(player) && isAimingAtTarget(serverYaw, serverPitch, targetYaw, targetPitch)) {
                executeAttack(mc, player, currentTarget);
                attackTimer = 0.0;
            }
        }
    }

    private void detectViaFixNeeded(Minecraft mc) {
        if (mc.getConnection() != null && mc.getConnection().getConnection() != null) {
            String serverAddress = mc.getConnection().getConnection().getRemoteAddress().toString();
            needsViaFix = serverAddress.contains("via") || serverAddress.contains("hypixel") ||
                    serverAddress.contains("127.0.0.1") || serverAddress.contains("loyisa");
        }
    }

    private void executeAttack(Minecraft mc, LocalPlayer player, LivingEntity target) {
        if (mc.gameMode.isDestroying()) return;
        if (mc.getConnection() == null) return;
        boolean wasSprinting = player.isSprinting();
        if (wasSprinting) {
            player.setSprinting(false);
        }
        Vec3 pos = player.position();
        mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
                pos.x, pos.y, pos.z, serverYaw, serverPitch, player.onGround()
        ));
        ServerboundInteractPacket attackPacket = ServerboundInteractPacket.createAttackPacket(target, player.isShiftKeyDown());
        mc.getConnection().send(attackPacket);
        mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        if (wasSprinting) {
            player.setSprinting(true);
        }
    }

    private boolean isAimingAtTarget(float currentYaw, float currentPitch, float targetYaw, float targetPitch) {
        float yawDiff = Math.abs(normalizeAngle(currentYaw - targetYaw));
        float pitchDiff = Math.abs(normalizeAngle(currentPitch - targetPitch));
        return yawDiff < 8.0f && pitchDiff < 8.0f;
    }

    private Vec3 calculateTargetPosition(LivingEntity target) {
        AABB box = target.getBoundingBox();
        Vec3 motion = new Vec3(target.getX() - target.xOld, target.getY() - target.yOld, target.getZ() - target.zOld).scale(0.5);
        return new Vec3(
                (box.minX + box.maxX) / 2.0 + motion.x,
                (box.minY + box.maxY) / 2.0 + motion.y,
                (box.minZ + box.maxZ) / 2.0 + motion.z
        );
    }

    private void applySilentMoveFix(LocalPlayer player, float yaw) {
        float rad = (float) Math.toRadians(yaw);
        double forward = player.zza;
        double strafe = player.xxa;

        double sin = Math.sin(rad);
        double cos = Math.cos(rad);

        player.xxa = (float)(strafe * cos - forward * sin);
        player.zza = (float)(forward * cos + strafe * sin);
    }

    private void updateHeadAndBodyRotation(LocalPlayer player, float yaw) {
        player.yHeadRot = yaw;
        player.yHeadRotO = yaw;
        player.yBodyRot = yaw;
        player.yBodyRotO = yaw;
    }

    private void restoreFirstPersonView(LocalPlayer player, float realYaw, float realPitch) {
        player.setYRot(realYaw);
        player.setXRot(realPitch);
    }

    private LivingEntity findClosestTarget(LocalPlayer player, double maxDistance) {
        Vec3 pos = player.position();
        List<LivingEntity> entities = player.level().getEntitiesOfClass(LivingEntity.class,
                new AABB(pos.x - maxDistance, pos.y - maxDistance, pos.z - maxDistance,
                        pos.x + maxDistance, pos.y + maxDistance, pos.z + maxDistance),
                e -> e != player && e.isAlive() && !e.isSpectator()
        );
        return entities.stream()
                .min((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)))
                .orElse(null);
    }

    private float[] calculateServerRotations(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double distXZ = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, distXZ));

        return new float[]{yaw, pitch};
    }

    private boolean holdWeapon(LocalPlayer player) {
        var item = player.getInventory().getSelected();
        if (item.isEmpty()) return false;
        String name = item.getItem().toString().toLowerCase();
        return name.contains("sword") || name.contains("axe") || name.contains("mace");
    }

    private float smoothRotation(float current, float target, float speed) {
        float delta = normalizeAngle(target - current);
        if (Math.abs(delta) > speed) {
            return normalizeAngle(current + Math.signum(delta) * speed);
        }
        return target;
    }

    private float normalizeAngle(float angle) {
        angle %= 360f;
        if (angle > 180f) angle -= 360f;
        if (angle < -180f) angle += 360f;
        return angle;
    }
}
*/