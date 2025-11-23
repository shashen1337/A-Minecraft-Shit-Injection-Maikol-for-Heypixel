package Maikol.feature.impl.render;

import Maikol.feature.Category;
import Maikol.feature.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HUD extends Module {

    private final Minecraft mc;

    private String cachedInfoText = "";
    private long lastUpdateTime = 0;
    private static final long UPDATE_INTERVAL = 100;

    private final Map<String, Float> moduleAnimations = new HashMap<>();
    private static final float ANIMATION_SPEED = 0.15f;

    private static final int GRADIENT_START = 0xFF00D2FF;
    private static final int GRADIENT_END = 0xFFFF6B9D;

    public HUD() {
        super("HUD");
        this.mc = Minecraft.getInstance();
        category = Category.render;
    }

    @Override
    protected void onEnable() {
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.NORMAL,
                false,
                RenderGuiOverlayEvent.Post.class,
                this::onRenderOverlay
        );
    }

    @Override
    protected void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
        moduleAnimations.clear();
    }

    public void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (mc.player == null || mc.level == null) return;
        if (!enabled) return;

        long currentTime = System.currentTimeMillis();

        if (currentTime - lastUpdateTime > UPDATE_INTERVAL) {
            updateInfoText();
            lastUpdateTime = currentTime;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();

        int padding = 6;
        int x = 5;
        int y = 5;

        int textWidth = mc.font.width(cachedInfoText);
        int boxWidth = textWidth + padding * 2;
        int boxHeight = mc.font.lineHeight + padding * 2;

        drawRoundedBorder(guiGraphics, x, y, boxWidth, boxHeight, 0xFF00D9FF);

        guiGraphics.drawString(mc.font, cachedInfoText, x + padding, y + padding, 0xFFFFFF, true);

        List<Module> enabledModules = Module.getEnabledModules();
        int moduleY = y + boxHeight + 3;

        for (Module module : enabledModules) {
            if (module == this) continue;

            String moduleName = module.getName();

            float targetAlpha = module.isEnabled() ? 1.0f : 0.0f;
            float currentAlpha = moduleAnimations.getOrDefault(moduleName, 0.0f);

            if (Math.abs(targetAlpha - currentAlpha) > 0.01f) {
                currentAlpha += (targetAlpha - currentAlpha) * ANIMATION_SPEED;
                moduleAnimations.put(moduleName, currentAlpha);
            } else {
                currentAlpha = targetAlpha;
                moduleAnimations.put(moduleName, currentAlpha);
            }

            if (currentAlpha <= 0.01f) {
                moduleAnimations.remove(moduleName);
                continue;
            }

            int moduleBoxHeight = mc.font.lineHeight + padding * 2;

            drawGradientText(guiGraphics, moduleName, x + padding, moduleY + padding, currentAlpha);

            moduleY += (int)(moduleBoxHeight * currentAlpha) + 2;
        }
    }

    private void updateInfoText() {
        String playerName = mc.player.getName().getString();
        int fps = mc.getFps();
        String serverIP = getServerIP();
        cachedInfoText = playerName + " | " +  fps + " FPS | " + serverIP;
    }

    private void drawGradientText(GuiGraphics guiGraphics, String text, int x, int y, float alpha) {
        int currentX = x;
        int textLength = text.length();

        if (textLength == 0) return;

        for (int i = 0; i < textLength; i++) {
            char c = text.charAt(i);
            String charStr = String.valueOf(c);

            float progress = textLength > 1 ? (float) i / (textLength - 1) : 0;
            int color = interpolateColor(GRADIENT_START, GRADIENT_END, progress, alpha);

            guiGraphics.drawString(mc.font, charStr, currentX, y, color, true);
            currentX += mc.font.width(charStr);
        }
    }

    private int interpolateColor(int color1, int color2, float progress, float alpha) {
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int r = (int) (r1 + (r2 - r1) * progress);
        int g = (int) (g1 + (g2 - g1) * progress);
        int b = (int) (b1 + (b2 - b1) * progress);
        int a = (int) (255 * alpha);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void drawRoundedBorder(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        int radius = 3;
        guiGraphics.fill(x + radius, y, x + width - radius, y + 1, color);
        guiGraphics.fill(x + radius, y + height - 1, x + width - radius, y + height, color);
        guiGraphics.fill(x, y + radius, x + 1, y + height - radius, color);
        guiGraphics.fill(x + width - 1, y + radius, x + width, y + height - radius, color);
        drawCorner(guiGraphics, x, y, radius, color, true, true);
        drawCorner(guiGraphics, x + width - radius - 1, y, radius, color, false, true);
        drawCorner(guiGraphics, x, y + height - radius - 1, radius, color, true, false);
        drawCorner(guiGraphics, x + width - radius - 1, y + height - radius - 1, radius, color, false, false);
    }

    private void drawCorner(GuiGraphics guiGraphics, int x, int y, int radius, int color, boolean left, boolean top) {
        for (int i = 0; i <= radius; i++) {
            for (int j = 0; j <= radius; j++) {
                double distance = Math.sqrt(i * i + j * j);
                if (distance >= radius - 0.5 && distance <= radius + 0.5) {
                    int px = left ? x + radius - i : x + i;
                    int py = top ? y + radius - j : y + j;
                    guiGraphics.fill(px, py, px + 1, py + 1, color);
                }
            }
        }
    }

    private String getServerIP() {
        if (mc.getCurrentServer() != null) {
            ServerData serverData = mc.getCurrentServer();
            return serverData.ip;
        } else if (mc.hasSingleplayerServer()) {
            return "Maikolawa";
        } else {
            return "Maikolawa";
        }
    }
}