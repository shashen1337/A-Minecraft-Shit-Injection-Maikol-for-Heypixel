package Maikol;

import Maikol.auth.AuthManager;
import Maikol.feature.Command;
import Maikol.feature.Module;
import Maikol.feature.PacketInterceptor;
import Maikol.feature.impl.combat.*;
import Maikol.feature.impl.misc.AutoJoin;
import Maikol.feature.impl.misc.Teams;
import Maikol.feature.impl.movement.Eagle;
import Maikol.feature.impl.movement.JumpReset;
import Maikol.feature.impl.movement.Sprint;
import Maikol.feature.impl.player.*;
import Maikol.feature.impl.render.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class Maikolclient {

    private static AuthManager authManager;
    private static final List<Module> allModules = new ArrayList<>();
    private static Command command;

    public Maikolclient() {
        authManager = new AuthManager();
        showWelcomeMessage();
        initializeModules();
        new PacketInterceptor();
    }

    private void showWelcomeMessage() {
        Thread welcomeThread = new Thread(() -> {
            try {
                Thread.sleep(2000);
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    if (!AuthManager.isAuthenticated()) {
                        mc.player.sendSystemMessage(Component.literal("§c========================================"));
                        mc.player.sendSystemMessage(Component.literal("§e Maikol Client"));
                        mc.player.sendSystemMessage(Component.literal("§c请输入密钥激活客户端"));
                        mc.player.sendSystemMessage(Component.literal("§a使用方法: §f.auth <密钥>"));
                        mc.player.sendSystemMessage(Component.literal("§7HWID: §f" + AuthManager.getCurrentHWID()));
                        mc.player.sendSystemMessage(Component.literal("§c========================================"));
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        welcomeThread.setDaemon(true);
        welcomeThread.start();
    }

    private void initializeModules() {
        command = new Command();
        new PacketInterceptor();
        //Blink blink = new Blink();
        NightVision nightVision = new NightVision();
        ForcedName forcedName = new ForcedName();
        AutoSoup autoSoup = new AutoSoup();
        KillerDetection killerDetection = new KillerDetection();
        NoBadEffect noBadEffect = new NoBadEffect();
        CrystalAura crystalAura = new CrystalAura();
        ChestESP chestESP = new ChestESP();
        ChestStealer chestStealer = new ChestStealer();
        AutoTool autoTool = new AutoTool();
        FastPlace fastPlace = new FastPlace();
        Teams teams = new Teams();
        AimAssist aimAssist = new AimAssist();
        AutoClicker autoClicker = new AutoClicker();
        JumpReset jumpReset = new JumpReset();
        Sprint sprint = new Sprint();
        Reach reach = new Reach();
        InventoryManager inventoryManager = new InventoryManager();
        AutoJoin autoJoin = new AutoJoin();
        HUD hud = new HUD();
        BackTrack backTrack = new BackTrack();
        ESP esp = new ESP();
        Eagle eagle = new Eagle();
        //allModules.add(blink);
        allModules.add(nightVision);
        allModules.add(forcedName);
        allModules.add(autoSoup);
        allModules.add(killerDetection);
        allModules.add(noBadEffect);
        allModules.add(crystalAura);
        allModules.add(chestESP);
        allModules.add(autoTool);
        allModules.add(fastPlace);
        allModules.add(teams);
        allModules.add(aimAssist);
        allModules.add(autoClicker);
        allModules.add(jumpReset);
        allModules.add(sprint);
        allModules.add(reach);
        allModules.add(chestStealer);
        allModules.add(inventoryManager);
        allModules.add(autoJoin);
        allModules.add(hud);
        allModules.add(backTrack);
        allModules.add(esp);
        allModules.add(eagle);
        Command.registerModule(new AimAssist());
        Command.registerModule(new AutoClicker());
        Command.registerModule(new JumpReset());
        Command.registerModule(new Sprint());
        Command.registerModule(new Reach());
        Command.registerModule(new ChestStealer());
        Command.registerModule(new InventoryManager());
        Command.registerModule(new AutoJoin());
        Command.registerModule(new HUD());
        Command.registerModule(new BackTrack());
        Command.registerModule(new ESP());
        Command.registerModule(new Eagle());
        Command.registerModule(new Teams());
        Command.registerModule(new FastPlace());
        Command.registerModule(new AutoTool());
        Command.registerModule(new ChestESP());
        Command.registerModule(new CrystalAura());
        Command.registerModule(new AutoSoup());
        Command.registerModule(new KillerDetection());
        Command.registerModule(new NoBadEffect());
        Command.registerModule(new ForcedName());
        Command.registerModule(new NightVision());
        //Command.registerModule(new Blink());
}

    public static boolean isAuthenticated() {
        return AuthManager.isAuthenticated();
    }

    public static void disableAllModules() {
        for (Module module : allModules) {
            if (module.isEnabled()) {
                module.setEnabled(false);
            }
        }

        Minecraft mc = Minecraft.getInstance();
        /*if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§c功能已禁用！请输入有效密钥。"));
        }*/
    }
}