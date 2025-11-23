package Maikol.feature.impl.combat;

import Maikol.feature.Category;
import Maikol.feature.Module;
import Maikol.feature.impl.misc.Teams;
import Maikol.feature.Value;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.Comparator;
import java.util.List;

public class AimAssist extends Module {

    private final Value<Double> MAX_RANGE = new Value<>("RANGE", 5.0D);
    private final Value<Double> MAX_FOV = new Value<>("FOV", 180D);
    private final Value<Double> SMOOTH_SPEED = new Value<>("SPEED", 0.03D);

    private static final double SPEED_MULTIPLIER_WITH_BACKTRACK = 3.33;
    private double currentSpeedMultiplier = 1.0;
    private boolean wasBackTrackEnabled = false;

    private static final int TARGET_LOSS_GRACE = 15;
    private static boolean leftMouseDown = false;
    private static Player currentTarget = null;
    private static int ticksSinceTargetLost = 0;
    private static double targetOffsetX = 0;
    private static double targetOffsetY = 0;
    private static long lastOffsetUpdateTime = 0;
    private static float targetYaw = 0f;
    private static float targetPitch = 0f;
    private static double currentOffsetX = 0;
    private static double currentOffsetY = 0;

    public AimAssist() {
        super("AimAssist");
        category = Category.combat;
    }

    @Override
    protected void onEnable() {
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.NORMAL,
                false,
                InputEvent.MouseButton.class,
                this::onMouseButton
        );
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.NORMAL,
                false,
                RenderLevelStageEvent.class,
                this::onRender
        );
    }

    @Override
    protected void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    public void onMouseButton(InputEvent.MouseButton event) {
        if (event.getButton() == 0) {
            leftMouseDown = (event.getAction() == 1);
            if (!leftMouseDown) {
                currentTarget = null;
                ticksSinceTargetLost = 0;
            }
        }
    }

    public void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        if (!enabled) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer self = mc.player;
        if (mc == null || self == null || mc.level == null) return;
        if (mc.screen != null) {
            currentTarget = null;
            ticksSinceTargetLost = 0;
            return;
        }
        if (!leftMouseDown) return;
        /*if (mc.gameMode != null && mc.gameMode.isDestroying()) {
            currentTarget = null;
            ticksSinceTargetLost = 0;
            return;
        }*/

        updateSpeedMultiplier();

        if (currentTarget != null) {
            if (!Target(self, currentTarget)) {
                ticksSinceTargetLost++;
                if (ticksSinceTargetLost > TARGET_LOSS_GRACE) {
                    currentTarget = null;
                    ticksSinceTargetLost = 0;
                }
            } else {
                ticksSinceTargetLost = 0;
            }
        }

        if (currentTarget == null) {
            currentTarget = findTarget(self);
            if (currentTarget == null) return;
        }

        float[] look = getAimRotation(self, currentTarget);
        targetYaw = look[0];
        targetPitch = look[1];

        float currentYaw = self.getYRot();
        float currentPitch = self.getXRot();

        double effectiveSpeed = SMOOTH_SPEED.getValue() * currentSpeedMultiplier;
        float newYaw = smoothAngle(currentYaw, targetYaw, (float) effectiveSpeed);
        float newPitch = smoothAngle(currentPitch, targetPitch, (float) effectiveSpeed);

        self.setYRot(newYaw);
        self.setXRot(newPitch);
    }

    private void updateSpeedMultiplier() {
        boolean isBackTrackEnabled = isModuleEnabled("BackTrack");

        if (isBackTrackEnabled != wasBackTrackEnabled) {
            wasBackTrackEnabled = isBackTrackEnabled;
        }

        double targetMultiplier = isBackTrackEnabled ? SPEED_MULTIPLIER_WITH_BACKTRACK : 1.0;
        double transitionSpeed = 0.15;

        if (Math.abs(currentSpeedMultiplier - targetMultiplier) > 0.01) {
            currentSpeedMultiplier += (targetMultiplier - currentSpeedMultiplier) * transitionSpeed;
        } else {
            currentSpeedMultiplier = targetMultiplier;
        }
    }

    private boolean isModuleEnabled(String moduleName) {
        for (Module module : Module.getEnabledModules()) {
            if (module.getName().equals(moduleName)) {
                return true;
            }
        }
        return false;
    }

    private boolean Target(LocalPlayer self, Player target) {
        if (target == null || !target.isAlive() || target.isSpectator() || target.isInvisible()) return false;
        double distSq = self.distanceToSqr(target);
        if (distSq > MAX_RANGE.getValue() * MAX_RANGE.getValue()) return false;
        if (!Fov(self, target, MAX_FOV.getValue() * 1.5)) return false;
        return true;
    }

    private Player findTarget(LocalPlayer self) {
        Minecraft mc = Minecraft.getInstance();
        List<? extends Player> players = mc.level.players();

        return players.stream()
                .filter(p -> p != self && p.isAlive() && !p.isSpectator() && !p.isInvisible())
                .filter(p -> self.distanceToSqr(p) <= MAX_RANGE.getValue() * MAX_RANGE.getValue())
                .filter(p -> Fov(self, p, MAX_FOV.getValue()))
                .filter(p -> !Teams.shouldSkipTarget(self, p))
                .min(Comparator.comparingDouble(p -> angleDistance(self, p)))
                .orElse(null);
    }

    private boolean Fov(LocalPlayer self, Player target, double maxFov) {
        float[] look = getAimRotation(self, target);
        float desiredYaw = look[0];
        float yawDiff = Math.abs(Mth.wrapDegrees(desiredYaw - self.getYRot()));
        return yawDiff <= maxFov / 2.0;
    }

    private float[] getAimRotation(LocalPlayer self, LivingEntity target) {
        updateOffsets();

        Vec3 eye = self.getEyePosition();
        double targetY = target.getBoundingBox().minY + target.getBbHeight() * 0.65 + currentOffsetY;
        double targetX = target.getX() + currentOffsetX;
        double targetZ = target.getZ();

        Vec3 targetPos = new Vec3(targetX, targetY, targetZ);

        double dx = targetPos.x - eye.x;
        double dy = targetPos.y - eye.y;
        double dz = targetPos.z - eye.z;

        double distXZ = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float pitch = (float)(-Math.toDegrees(Math.atan2(dy, distXZ)));
        long time = System.currentTimeMillis();
        float randomYawOffset = (float)Math.sin(time * 0.002) * 0.5f;
        float randomPitchOffset = (float)Math.cos(time * 0.002) * 0.3f;
        yaw += randomYawOffset;
        pitch += randomPitchOffset;

        yaw = Mth.wrapDegrees(yaw);
        pitch = Mth.clamp(pitch, -90.0F, 90.0F);
        return new float[] { yaw, pitch };
    }

    private double angleDistance(LocalPlayer self, LivingEntity target) {
        float[] look = getAimRotation(self, target);
        float yawDiff = Mth.wrapDegrees(look[0] - self.getYRot());
        float pitchDiff = Mth.wrapDegrees(look[1] - self.getXRot());
        return Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
    }

    private float smoothAngle(float current, float target, float factor) {
        float delta = Mth.wrapDegrees(target - current);
        return current + delta * factor;
    }

    private void updateOffsets() {
        long time = System.currentTimeMillis();
        if (time - lastOffsetUpdateTime > 2000) {
            lastOffsetUpdateTime = time;
            double choice = Math.random();
            if (choice < 0.33) targetOffsetX = -0.2 + Math.random() * 0.1;
            else if (choice < 0.66) targetOffsetX = 0;
            else targetOffsetX = 0.15 + Math.random() * 0.1;
            targetOffsetY = (Math.random() - 0.5) * 0.08;
        }
        currentOffsetX += (targetOffsetX - currentOffsetX) * 0.02;
        currentOffsetY += (targetOffsetY - currentOffsetY) * 0.02;
    }
}