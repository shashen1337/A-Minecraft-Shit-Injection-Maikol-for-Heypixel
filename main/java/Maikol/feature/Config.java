package Maikol.feature;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Config {

    private static Path getConfigPath(String configName) {
        Minecraft mc = Minecraft.getInstance();
        Path gameDir = mc.gameDirectory.toPath();
        return gameDir.resolve("Maikol").resolve(configName + ".yml");
    }

    private static boolean isValidConfigName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        return name.matches("[a-zA-Z0-9_-]+");
    }

    public static boolean save(String configName, Map<String, Module> modules, Map<Integer, List<Module>> keyBindings, int autoClickerCPS) {
        if (!isValidConfigName(configName)) {
            return false;
        }

        try {
            Path configPath = getConfigPath(configName);
            Files.createDirectories(configPath.getParent());

            StringBuilder yml = new StringBuilder();
            yml.append("autoclicker:\n");
            yml.append("  cps: ").append(autoClickerCPS).append("\n\n");
            yml.append("modules:\n");
            for (Module module : modules.values()) {
                if (module != null) {
                    yml.append("  ").append(module.getName()).append(":\n");
                    yml.append("    enabled: ").append(module.isEnabled()).append("\n");
                    try {
                        Class<?> clazz = module.getClass();
                        Field[] fields = clazz.getDeclaredFields();
                        for (Field field : fields) {
                            if (field.getType() == Value.class) {
                                field.setAccessible(true);
                                Value<?> value = (Value<?>) field.get(module);
                                if (value != null) {
                                    String fieldName = value.getName();
                                    Object fieldValue = value.getValue();
                                    yml.append("    ").append(fieldName).append(": ").append(fieldValue).append("\n");
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to save values for module " + module.getName() + ": " + e.getMessage());
                    }
                }
            }
            yml.append("\n");
            yml.append("keybinds:\n");
            for (Map.Entry<Integer, List<Module>> entry : keyBindings.entrySet()) {
                String keyName = getKeyName(entry.getKey());
                List<Module> modulesList = entry.getValue();
                List<Module> validModules = new ArrayList<>();
                for (Module module : modulesList) {
                    if (module != null) {
                        validModules.add(module);
                    }
                }

                if (!validModules.isEmpty()) {
                    yml.append("  ").append(keyName).append(": ");
                    for (int i = 0; i < validModules.size(); i++) {
                        yml.append(validModules.get(i).getName());
                        if (i < validModules.size() - 1) {
                            yml.append(", ");
                        }
                    }
                    yml.append("\n");
                }
            }

            Files.write(configPath, yml.toString().getBytes());
            return true;

        } catch (IOException e) {
            sendMessage("保存配置失败：" + e.getMessage());
            return false;
        }
    }

    public static ConfigData load(String configName) {
        ConfigData data = new ConfigData();

        if (!isValidConfigName(configName)) {
            return data;
        }

        Path configPath = getConfigPath(configName);
        if (!Files.exists(configPath)) {
            return data;
        }

        try {
            String content = new String(Files.readAllBytes(configPath));
            String[] lines = content.split("\n");

            String currentSection = null;
            String currentModule = null;
            int currentIndent = 0;

            for (String line : lines) {
                if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                    continue;
                }
                int indent = 0;
                for (char c : line.toCharArray()) {
                    if (c == ' ') indent++;
                    else break;
                }
                String trimmedLine = line.trim();
                if (trimmedLine.endsWith(":") && indent == 0) {
                    currentSection = trimmedLine.substring(0, trimmedLine.length() - 1);
                    currentModule = null;
                    currentIndent = 0;
                    continue;
                }
                
                if ("autoclicker".equals(currentSection)) {
                    if (trimmedLine.startsWith("cps:")) {
                        String value = trimmedLine.substring(4).trim();
                        data.autoClickerCPS = Integer.parseInt(value);
                    }
                }

                if ("modules".equals(currentSection)) {
                    if (trimmedLine.endsWith(":")) {
                        if (indent == 2) {
                            currentModule = trimmedLine.substring(0, trimmedLine.length() - 1).trim();
                            currentIndent = indent;
                        }
                    }
                    else if (currentModule != null && indent > currentIndent) {
                        if (trimmedLine.startsWith("enabled:")) {
                            String value = trimmedLine.substring(8).trim();
                            boolean enabled = Boolean.parseBoolean(value);
                            data.moduleStates.put(currentModule, enabled);
                        } else if (trimmedLine.contains(":")) {
                            String[] parts = trimmedLine.split(":", 2);
                            if (parts.length == 2) {
                                String fieldName = parts[0].trim();
                                String fieldValue = parts[1].trim();
                                if (!data.moduleValues.containsKey(currentModule)) {
                                    data.moduleValues.put(currentModule, new HashMap<>());
                                }
                                data.moduleValues.get(currentModule).put(fieldName, fieldValue);
                            }
                        }
                    }
                }
                
                if ("keybinds".equals(currentSection)) {
                    if (trimmedLine.contains(":")) {
                        String[] parts = trimmedLine.split(":", 2);
                        if (parts.length == 2) {
                            String keyName = parts[0].trim();
                            String modulesStr = parts[1].trim();
                            String[] moduleNames = modulesStr.split(",");
                            List<String> modules = new ArrayList<>();
                            for (String moduleName : moduleNames) {
                                modules.add(moduleName.trim());
                            }
                            data.keyBindings.put(keyName, modules);
                        }
                    }
                }
            }

        } catch (IOException e) {
            sendMessage("无法加载配置文件：" + e.getMessage());
        }

        return data;
    }

    private static String getKeyName(int keyCode) {
        if (keyCode >= 65 && keyCode <= 90) {
            return String.valueOf((char) keyCode);
        }
        if (keyCode >= 48 && keyCode <= 57) {
            return String.valueOf((char) keyCode);
        }
        switch (keyCode) {
            case 32: return "SPACE";
            case 340: return "LSHIFT";
            case 341: return "LCTRL";
            case 342: return "LALT";
            case 258: return "TAB";
            case 256: return "ESCAPE";
            case 257: return "ENTER";
            case 259: return "BACKSPACE";
            case 260: return "INSERT";
            case 261: return "DELETE";
            case 262: return "RIGHT";
            case 263: return "LEFT";
            case 264: return "DOWN";
            case 265: return "UP";
            case 266: return "PAGEUP";
            case 267: return "PAGEDOWN";
            case 268: return "HOME";
            case 269: return "END";
            case 280: return "CAPSLOCK";
            case 290: return "F1";
            case 291: return "F2";
            case 292: return "F3";
            case 293: return "F4";
            case 294: return "F5";
            case 295: return "F6";
            case 296: return "F7";
            case 297: return "F8";
            case 298: return "F9";
            case 299: return "F10";
            case 300: return "F11";
            case 301: return "F12";
            default: return "KEY_" + keyCode;
        }
    }

    public static class ConfigData {
        public int autoClickerCPS = 12;
        public Map<String, Boolean> moduleStates = new HashMap<>();
        public Map<String, List<String>> keyBindings = new HashMap<>();
        public Map<String, Map<String, String>> moduleValues = new HashMap<>();
    }

    private static void sendMessage(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(message), false);
        }
    }
}