package Maikol.feature;

import Maikol.Maikolclient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Module {

    private static final List<Module> enabledModules = new ArrayList<>();
    private static List<Module> cachedUnmodifiableView = null;
    private static boolean needsUpdate = true;

    protected String name;
    protected boolean enabled;
    public String category;

    public Module(String name) {
        this.name = name;
        this.enabled = false;
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public void setEnabled(boolean enabled) {
        if (enabled && !Maikolclient.isAuthenticated()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("§c未激活！请先使用 .auth <密钥> 激活客户端。"));
            }
            return;
        }

        this.enabled = enabled;

        if (enabled) {
            if (!enabledModules.contains(this)) {
                enabledModules.add(this);
                needsUpdate = true;
            }
            onEnable();
        } else {
            enabledModules.remove(this);
            needsUpdate = true;
            onDisable();
        }
    }

    public boolean isEnabled() {
        if (!Maikolclient.isAuthenticated()) {
            return false;
        }
        return enabled;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    public static List<Module> getEnabledModules() {
        if (needsUpdate || cachedUnmodifiableView == null) {
            cachedUnmodifiableView = Collections.unmodifiableList(new ArrayList<>(enabledModules));
            needsUpdate = false;
        }
        return cachedUnmodifiableView;
    }

    public static List<Module> getEnabledModulesCopy() {
        return new ArrayList<>(enabledModules);
    }

    public static void clearAllModules() {
        enabledModules.clear();
        needsUpdate = true;
    }
}