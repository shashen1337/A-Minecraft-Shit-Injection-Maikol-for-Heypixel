package Maikol.feature;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DefaultConfigManager {

    private static final String DEFAULT_CONFIG_NAME = "default";
    private static boolean hasLoadedDefault = false;
    private static Path getConfigFolder() {
        Minecraft mc = Minecraft.getInstance();
        Path gameDir = mc.gameDirectory.toPath();
        return gameDir.resolve("Maikol");
    }
    private static Path getDefaultConfigPath() {
        return getConfigFolder().resolve(DEFAULT_CONFIG_NAME + ".yml");
    }
    private static String getDefaultConfigContent() {
        return "autoclicker:\n" +
                "  cps: 18\n" +
                "modules:\n" +
                "  AutoClicker:\n" +
                "    enabled: true\n" +
                "  ChestESP:\n" +
                "    enabled: true\n" +
                "  NoBadEffect:\n" +
                "    enabled: true\n" +
                "  ESP:\n" +
                "    enabled: true\n" +
                "  Teams:\n" +
                "    enabled: true\n" +
                "  CrystalAura:\n" +
                "    enabled: true\n" +
                "    AttackRange: 3.1\n" +
                "    DelayTick: 0\n" +
                "  Reach:\n" +
                "    enabled: true\n" +
                "    Range: 3.1\n" +
                "  Sprint:\n" +
                "    enabled: true\n" +
                "  AutoTool:\n" +
                "    enabled: true\n" +
                "  ForcedName:\n" +
                "    enabled: false\n" +
                "  AutoSoup:\n" +
                "    enabled: true\n" +
                "  ShowKiller:\n" +
                "    enabled: false\n" +
                "    Debug: false\n" +
                "  ChestStealer:\n" +
                "    enabled: false\n" +
                "    Delay: 130\n" +
                "  AutoPlay:\n" +
                "    enabled: false\n" +
                "  AimAssist:\n" +
                "    enabled: true\n" +
                "    RANGE: 5.0\n" +
                "    FOV: 360.0\n" +
                "    SPEED: 0.02\n" +
                "  NightVision:\n" +
                "    enabled: true\n" +
                "  HUD:\n" +
                "    enabled: false\n" +
                "  InventoryManager:\n" +
                "    enabled: false\n" +
                "    equipDelay: 0\n" +
                "    dropDelay: 0\n" +
                "  Eagle:\n" +
                "    enabled: false\n" +
                "  JumpReset:\n" +
                "    enabled: true\n" +
                "    Debug: false\n" +
                "  FastPlace:\n" +
                "    enabled: true\n" +
                "    CPS: 40\n" +
                "  BackTrack:\n" +
                "    enabled: false\n" +
                "    Timeframe: 250\n" +
                "keybinds:\n" +
                "  U: BackTrack\n" +
                "  H: AutoPlay\n" +
                "  Y: AimAssist, AutoClicker, FastPlace\n" +
                "  Z: Eagle\n" +
                "  J: InventoryManager\n" +
                "  N: ChestStealer\n" +
                "  O: ShowKiller, ForcedName\n";
    }
    public static Config.ConfigData initializeDefaultConfig() {
        try {
            Path configFolder = getConfigFolder();
            if (!Files.exists(configFolder)) {
                Files.createDirectories(configFolder);
                //System.out.println("[Maikol] Created config folder: " + configFolder);
            }
            Path defaultConfigPath = getDefaultConfigPath();
            if (!Files.exists(defaultConfigPath)) {
                createDefaultConfig();
                //System.out.println("[Maikol] Created default.yml");
            } else {
                //System.out.println("[Maikol] default.yml already exists, skipping creation");
            }
            if (!hasLoadedDefault) {
                Config.ConfigData data = Config.load(DEFAULT_CONFIG_NAME);
                hasLoadedDefault = true;
                //System.out.println("[Maikol] Loaded default.yml");
                return data;
            }
        } catch (Exception e) {
            //System.err.println("[Maikol] Failed to initialize default config: " + e.getMessage());
            e.printStackTrace();
        }
        return new Config.ConfigData();
    }
    private static void createDefaultConfig() throws IOException {
        Path defaultConfigPath = getDefaultConfigPath();
        String content = getDefaultConfigContent();
        Files.write(defaultConfigPath, content.getBytes());
    }
    public static boolean hasLoadedDefault() {
        return hasLoadedDefault;
    }
    public static void resetLoadState() {
        hasLoadedDefault = false;
    }
    public static void forceRecreateDefaultConfig() {
        try {
            Path configFolder = getConfigFolder();
            if (!Files.exists(configFolder)) {
                Files.createDirectories(configFolder);
            }

            createDefaultConfig();
            //System.out.println("[Maikol] Force recreated default.yml");

        } catch (IOException e) {
            //System.err.println("[Maikol] Failed to force recreate default config: " + e.getMessage());
            e.printStackTrace();
        }
    }
}