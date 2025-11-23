package Maikol.feature;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public class Command {

    private static final Minecraft mc = Minecraft.getInstance();
    private static final Map<String, Module> registeredModules = new HashMap<>();
    private static final Map<Integer, List<Module>> keyBindings = new HashMap<>();
    private static final Map<Integer, Boolean> keyStates = new HashMap<>();
    private static String currentConfigName = "default";

    private static int autoClickerCPS = 15;

    public Command() {
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.HIGHEST,
                false,
                ClientChatEvent.class,
                this::onClientChat
        );
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.NORMAL,
                false,
                InputEvent.Key.class,
                this::onKeyInput
        );
        loadDefaultConfigOnStartup();
        //sendMessage("Loaded default config successfully");
    }

    public static void registerModule(Module module) {
        registeredModules.put(module.getName().toLowerCase(), module);
    }

    public static int getAutoClickerCPS() {
        return autoClickerCPS;
    }

    private void onClientChat(ClientChatEvent event) {
        String message = event.getMessage();
        if (message.startsWith(".")) {
            event.setCanceled(true);
            processCommand(message.substring(1));
        }
    }

    private void onKeyInput(InputEvent.Key event) {
        if (mc.screen != null) {
            return;
        }

        if (event.getAction() == InputConstants.PRESS) {
            int key = event.getKey();

            if (keyBindings.containsKey(key)) {
                List<Module> modules = keyBindings.get(key);
                boolean wasPressed = keyStates.getOrDefault(key, false);

                if (!wasPressed) {
                    for (Module module : modules) {
                        module.setEnabled(!module.isEnabled());
                    }
                    if (modules.size() == 1) {
                        Module module = modules.get(0);
                        sendMessage("§b" + module.getName() + " §7» " +
                                (module.isEnabled() ? "§aEnabled" : "§cDisabled"));
                    } else {
                        StringBuilder msg = new StringBuilder("§7Toggled: ");
                        for (int i = 0; i < modules.size(); i++) {
                            Module m = modules.get(i);
                            msg.append(m.isEnabled() ? "§a" : "§c")
                                    .append(m.getName());
                            if (i < modules.size() - 1) {
                                msg.append("§7, ");
                            }
                        }
                        sendMessage(msg.toString());
                    }

                    keyStates.put(key, true);
                }
            }
        } else if (event.getAction() == InputConstants.RELEASE) {
            keyStates.put(event.getKey(), false);
        }
    }

    private void processCommand(String input) {
        String[] parts = input.split(" ");
        if (parts.length == 0) return;

        String cmd = parts[0].toLowerCase();

        switch (cmd) {
            case "m":
                handleModuleToggle(parts);
                break;
            case "bind":
                handleBind(parts);
                break;
            case "config":
                if (parts.length >= 2) {
                    String subCmd = parts[1].toLowerCase();
                    if (subCmd.equals("open")) {
                        handleConfigOpen();
                    } else if (parts.length >= 3) {
                        if (subCmd.equals("save")) {
                            String[] saveArgs = new String[] { "save", parts[2] };
                            handleSave(saveArgs);
                        } else if (subCmd.equals("load")) {
                            String[] loadArgs = new String[] { "load", parts[2] };
                            handleLoad(loadArgs);
                        } else {
                            handleConfig(parts);
                        }
                    } else {
                        sendMessage("§cUsage: .config save <name> | .config load <name> | .config open | .config <module> <field> <value>");
                    }
                } else {
                    sendMessage("§cUsage: .config save <name> | .config load <name> | .config open | .config <module> <field> <value>");
                }
                break;
            case "cps":
                handleCPS(parts);
                break;
            case "help":
                handleHelp();
                break;
            default:
                sendMessage("§c不支持的命令，使用 §e.help §c获取帮助");
                break;
        }
    }

    private void handleModuleToggle(String[] parts) {
        if (parts.length != 3) {
            sendMessage("§cUsage: .m <module> <on/off>");
            return;
        }

        String moduleName = parts[1].toLowerCase();
        String state = parts[2].toLowerCase();

        Module module = registeredModules.get(moduleName);
        if (module == null) {
            sendMessage("§c功能 §e" + parts[1] + " §c不存在");
            return;
        }

        if (state.equals("on")) {
            module.setEnabled(true);
            sendMessage("§b" + module.getName() + " §7» §aEnabled");
        } else if (state.equals("off")) {
            module.setEnabled(false);
            sendMessage("§b" + module.getName() + " §7» §cDisabled");
        } else {
            sendMessage("§c格式不正确，使用 §eon §c或 §eoff§c");
        }
    }

    private void handleBind(String[] parts) {
        if (parts.length != 3) {
            sendMessage("§cUsage: .bind <module> <key>");
            return;
        }

        String moduleName = parts[1].toLowerCase();
        String keyName = parts[2].toUpperCase();

        Module module = registeredModules.get(moduleName);
        if (module == null) {
            sendMessage("§c功能 §e" + parts[1] + " §c不存在");
            return;
        }

        try {
            int keyCode = getKeyCode(keyName);
            for (List<Module> modules : keyBindings.values()) {
                modules.remove(module);
            }
            keyBindings.entrySet().removeIf(entry -> entry.getValue().isEmpty());
            keyBindings.computeIfAbsent(keyCode, k -> new ArrayList<>()).add(module);
            List<Module> boundModules = keyBindings.get(keyCode);
            if (boundModules.size() == 1) {
                sendMessage("§b" + module.getName() + " §7bound to §e" + keyName);
            } else {
                StringBuilder msg = new StringBuilder("§e" + keyName + " §7» ");
                for (int i = 0; i < boundModules.size(); i++) {
                    msg.append("§b").append(boundModules.get(i).getName());
                    if (i < boundModules.size() - 1) {
                        msg.append("§7, ");
                    }
                }
                sendMessage(msg.toString());
            }
        } catch (Exception e) {
            sendMessage("§c不支持的按键：§e" + keyName);
        }
    }

    private void handleConfig(String[] parts) {
        if (parts.length != 4) {
            sendMessage("§cUsage: .config <module> <field> <value>");
            return;
        }

        String moduleName = parts[1].toLowerCase();
        String fieldName = parts[2];
        String valueStr = parts[3];

        Module module = registeredModules.get(moduleName);
        if (module == null) {
            sendMessage("§c功能 §e" + parts[1] + " §c不存在");
            return;
        }

        try {
            Class<?> clazz = module.getClass();
            Field[] fields = clazz.getDeclaredFields();
            Field targetField = null;

            for (Field field : fields) {
                if (field.getType() == Value.class) {
                    field.setAccessible(true);
                    Value<?> value = (Value<?>) field.get(module);
                    if (value != null && fieldName.equalsIgnoreCase(getFieldNameFromValue(field))) {
                        targetField = field;
                        break;
                    }
                }
            }

            if (targetField == null) {
                for (Field field : fields) {
                    if (field.getName().equalsIgnoreCase(fieldName)) {
                        targetField = field;
                        break;
                    }
                }
            }

            if (targetField == null) {
                sendMessage("§c字段 §e" + fieldName + " §c不存在于 §e" + module.getName());
                return;
            }

            targetField.setAccessible(true);

            if (targetField.getType() == Value.class) {
                Value value = (Value) targetField.get(module);
                Object currentValue = value.getValue();

                if (currentValue instanceof Double) {
                    value.setValue(Double.parseDouble(valueStr));
                } else if (currentValue instanceof Float) {
                    value.setValue(Float.parseFloat(valueStr));
                } else if (currentValue instanceof Long) {
                    value.setValue(Long.parseLong(valueStr));
                } else if (currentValue instanceof Integer) {
                    value.setValue(Integer.parseInt(valueStr));
                } else if (currentValue instanceof Boolean) {
                    value.setValue(Boolean.parseBoolean(valueStr));
                } else {
                    value.setValue(valueStr);
                }

                sendMessage("§b" + module.getName() + " §7» §e" + fieldName + " §7set to §a" + valueStr);
            } else {
                if (targetField.getType() == double.class || targetField.getType() == Double.class) {
                    targetField.set(module, Double.parseDouble(valueStr));
                } else if (targetField.getType() == float.class || targetField.getType() == Float.class) {
                    targetField.set(module, Float.parseFloat(valueStr));
                } else if (targetField.getType() == long.class || targetField.getType() == Long.class) {
                    targetField.set(module, Long.parseLong(valueStr));
                } else if (targetField.getType() == int.class || targetField.getType() == Integer.class) {
                    targetField.set(module, Integer.parseInt(valueStr));
                } else if (targetField.getType() == boolean.class || targetField.getType() == Boolean.class) {
                    targetField.set(module, Boolean.parseBoolean(valueStr));
                } else {
                    targetField.set(module, valueStr);
                }

                sendMessage("§b" + module.getName() + " §7» §e" + fieldName + " §7set to §a" + valueStr);
            }

        } catch (NumberFormatException e) {
            sendMessage("§c值格式无效 §e" + fieldName);
        } catch (Exception e) {
            sendMessage("§c不存在的值：" + e.getMessage());
        }
    }

    private void handleCPS(String[] parts) {
        if (parts.length != 2) {
            sendMessage("§cUsage: .cps <value>");
            return;
        }

        try {
            int cps = Integer.parseInt(parts[1]);
            if (cps < 1 || cps > 20) {
                sendMessage("§cCPS 必须在1-20之间");
                return;
            }
            autoClickerCPS = cps;
            sendMessage("§bAutoClicker CPS §7set to §a" + cps);
        } catch (NumberFormatException e) {
            sendMessage("§cCPS 值格式无效");
        }
    }

    private void handleHelp() {
        sendMessage("§b§lMaikol Commands:");
        sendMessage("§e.m <module> <on/off> §7- Toggle module");
        sendMessage("§e.bind <module> <key> §7- Bind module to key");
        sendMessage("§e.config <module> <field> <value> §7- Configure module");
        sendMessage("§e.config save <name> §7- Save configuration");
        sendMessage("§e.config load <name> §7- Load configuration");
        sendMessage("§e.config open §7- Open config folder");
        sendMessage("§e.cps <value> §7- Set AutoClicker CPS");
        sendMessage("§e.help §7- Show Help Message");
    }

    private String getFieldNameFromValue(Field field) {
        String name = field.getName();
        if (name.startsWith("f_")) {
            return name.substring(2);
        }
        return name;
    }

    private int getKeyCode(String keyName) {
        keyName = keyName.toUpperCase();
        try {
            Class<?> glfwClass = Class.forName("org.lwjgl.glfw.GLFW");
            Field field = glfwClass.getDeclaredField("GLFW_KEY_" + keyName);
            return field.getInt(null);
        } catch (Exception ignored) {}
        if (keyName.length() == 1) {
            char c = keyName.charAt(0);
            if (c >= '0' && c <= '9') {
                return 48 + (c - '0');
            }
            if (c >= 'A' && c <= 'Z') {
                return 65 + (c - 'A');
            }
        }
        switch (keyName) {
            case "SPACE": return 32;
            case "LSHIFT": case "SHIFT": return 340;
            case "LCTRL": case "CTRL": return 341;
            case "LALT": case "ALT": return 342;
            case "TAB": return 258;
            case "ESCAPE": case "ESC": return 256;
            case "ENTER": return 257;
            case "BACKSPACE": return 259;
            case "INSERT": return 260;
            case "DELETE": return 261;
            case "RIGHT": return 262;
            case "LEFT": return 263;
            case "DOWN": return 264;
            case "UP": return 265;
            case "PAGEUP": return 266;
            case "PAGEDOWN": return 267;
            case "HOME": return 268;
            case "END": return 269;
            case "CAPSLOCK": return 280;
            case "F1": return 290;
            case "F2": return 291;
            case "F3": return 292;
            case "F4": return 293;
            case "F5": return 294;
            case "F6": return 295;
            case "F7": return 296;
            case "F8": return 297;
            case "F9": return 298;
            case "F10": return 299;
            case "F11": return 300;
            case "F12": return 301;
        }
        KeyMapping[] keys = mc.options.keyMappings;
        for (KeyMapping key : keys) {
            String keyString = key.saveString().toUpperCase();
            if (keyString.contains(keyName)) {
                return key.getKey().getValue();
            }
        }

        throw new IllegalArgumentException("未知按键：" + keyName);
    }

    private void sendMessage(String message) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(message), false);
        }
    }

    private void handleSave(String[] parts) {
        if (parts.length != 2) {
            sendMessage("§cUsage: .config save <name>");
            sendMessage("§7Example: §e.config save myconfig");
            return;
        }
        String configName = parts[1];
        if (!configName.matches("[a-zA-Z0-9_-]+")) {
            sendMessage("§c配置名称无效 仅允许使用字母、数字、下划线和连字符 ");
            return;
        }
        boolean success = Config.save(configName, registeredModules, keyBindings, autoClickerCPS);
        if (success) {
            currentConfigName = configName;
            sendMessage("§a配置保存至 §e" + configName + ".yml");
        } else {
            sendMessage("§c保存配置失败！");
        }
    }
    private void handleLoad(String[] parts) {
        if (parts.length != 2) {
            sendMessage("§cUsage: .config load <name>");
            sendMessage("§7Example: §e.config load myconfig");
            return;
        }

        String configName = parts[1];

        if (!configName.matches("[a-zA-Z0-9_-]+")) {
            sendMessage("§c配置名称无效 仅允许使用字母、数字、下划线和连字符 ");
            return;
        }

        try {
            keyBindings.clear();
            keyStates.clear();
            for (Module module : registeredModules.values()) {
                if (module.isEnabled()) {
                    module.setEnabled(false);
                }
            }
            Config.ConfigData data = Config.load(configName);
            autoClickerCPS = data.autoClickerCPS;
            for (Map.Entry<String, Boolean> entry : data.moduleStates.entrySet()) {
                String moduleName = entry.getKey();
                Module module = registeredModules.get(moduleName.toLowerCase());
                if (module != null) {
                    module.setEnabled(entry.getValue());
                }
            }
            for (Map.Entry<String, Map<String, String>> moduleEntry : data.moduleValues.entrySet()) {
                String moduleName = moduleEntry.getKey();
                Module module = registeredModules.get(moduleName.toLowerCase());

                if (module != null) {
                    Map<String, String> values = moduleEntry.getValue();

                    for (Map.Entry<String, String> valueEntry : values.entrySet()) {
                        String fieldName = valueEntry.getKey();
                        String valueStr = valueEntry.getValue();

                        try {
                            Class<?> clazz = module.getClass();
                            Field[] fields = clazz.getDeclaredFields();
                            Field targetField = null;
                            for (Field field : fields) {
                                if (field.getType() == Value.class) {
                                    field.setAccessible(true);
                                    Value<?> value = (Value<?>) field.get(module);
                                    if (value != null && value.getName().equals(fieldName)) {
                                        targetField = field;
                                        break;
                                    }
                                }
                            }

                            if (targetField != null) {
                                targetField.setAccessible(true);
                                Value value = (Value) targetField.get(module);
                                Object currentValue = value.getValue();
                                if (currentValue instanceof Double) {
                                    value.setValue(Double.parseDouble(valueStr));
                                } else if (currentValue instanceof Float) {
                                    value.setValue(Float.parseFloat(valueStr));
                                } else if (currentValue instanceof Long) {
                                    value.setValue(Long.parseLong(valueStr));
                                } else if (currentValue instanceof Integer) {
                                    value.setValue(Integer.parseInt(valueStr));
                                } else if (currentValue instanceof Boolean) {
                                    value.setValue(Boolean.parseBoolean(valueStr));
                                } else {
                                    value.setValue(valueStr);
                                }
                            }
                        } catch (Exception e) {
                            sendMessage("§c加载参数失败 " + moduleName + "." + fieldName + ": " + e.getMessage());
                        }
                    }
                }
            }
            for (Map.Entry<String, List<String>> entry : data.keyBindings.entrySet()) {
                try {
                    int keyCode = getKeyCode(entry.getKey());
                    List<Module> modules = new ArrayList<>();

                    for (String modName : entry.getValue()) {
                        Module module = registeredModules.get(modName.toLowerCase());
                        if (module != null) {
                            modules.add(module);
                        }
                    }

                    if (!modules.isEmpty()) {
                        keyBindings.put(keyCode, modules);
                    }
                } catch (Exception e) {
                    sendMessage("绑定按键失败： " + entry.getKey() + ": " + e.getMessage());
                }
            }

            currentConfigName = configName;
            sendMessage("§a配置 §e" + configName + ".yml §a加载成功");
            int enabledCount = 0;
            for (Boolean enabled : data.moduleStates.values()) {
                if (enabled) enabledCount++;
            }

            sendMessage("§7载入 §e" + enabledCount + " §7个启用模块和 §e" +
                    data.keyBindings.size() + " §7个按键绑定");
        } catch (Exception e) {
            sendMessage("§c无法加载配置：" + e.getMessage());
        }
    }
    private void loadDefaultConfigOnStartup() {
        try {
            Config.ConfigData data = DefaultConfigManager.initializeDefaultConfig();
            autoClickerCPS = data.autoClickerCPS;
            for (Map.Entry<String, Boolean> entry : data.moduleStates.entrySet()) {
                String moduleName = entry.getKey();
                Module module = registeredModules.get(moduleName.toLowerCase());
                if (module != null) {
                    module.setEnabled(entry.getValue());
                    if (data.moduleValues.containsKey(moduleName)) {
                        Map<String, String> values = data.moduleValues.get(moduleName);
                        applyModuleValues(module, values);
                    }
                }
            }
            for (Map.Entry<String, List<String>> entry : data.keyBindings.entrySet()) {
                try {
                    int keyCode = getKeyCode(entry.getKey());
                    List<Module> modules = new ArrayList<>();
                    for (String modName : entry.getValue()) {
                        Module module = registeredModules.get(modName.toLowerCase());
                        if (module != null) {
                            modules.add(module);
                        }
                    }
                    if (!modules.isEmpty()) {
                        keyBindings.put(keyCode, modules);
                    }
                } catch (Exception e) {
                    //System.err.println("Failed to bind key: " + entry.getKey());
                }
            }
            currentConfigName = "default";
            //System.out.println("[Maikol] Loaded default.yml successfully");
        } catch (Exception e) {
            //System.err.println("[Maikol] Failed to load default config: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private void applyModuleValues(Module module, Map<String, String> values) {
        try {
            Class<?> clazz = module.getClass();
            Field[] fields = clazz.getDeclaredFields();

            for (Map.Entry<String, String> valueEntry : values.entrySet()) {
                String fieldName = valueEntry.getKey();
                String valueStr = valueEntry.getValue();

                Field targetField = null;
                for (Field field : fields) {
                    if (field.getType() == Value.class) {
                        field.setAccessible(true);
                        Value<?> value = (Value<?>) field.get(module);
                        if (value != null && value.getName().equals(fieldName)) {
                            targetField = field;
                            break;
                        }
                    }
                }

                if (targetField != null) {
                    targetField.setAccessible(true);
                    Value value = (Value) targetField.get(module);
                    Object currentValue = value.getValue();

                    if (currentValue instanceof Double) {
                        value.setValue(Double.parseDouble(valueStr));
                    } else if (currentValue instanceof Float) {
                        value.setValue(Float.parseFloat(valueStr));
                    } else if (currentValue instanceof Long) {
                        value.setValue(Long.parseLong(valueStr));
                    } else if (currentValue instanceof Integer) {
                        value.setValue(Integer.parseInt(valueStr));
                    } else if (currentValue instanceof Boolean) {
                        value.setValue(Boolean.parseBoolean(valueStr));
                    } else {
                        value.setValue(valueStr);
                    }
                }
            }
        } catch (Exception e) {
            //System.err.println("Failed to apply values for module " + module.getName());
        }
    }
    private void handleConfigOpen() {
        try {
            java.nio.file.Path configDir = java.nio.file.Paths.get("", "Maikol");
            java.io.File configFolder = configDir.toFile();
            if (!configFolder.exists()) {
                boolean created = configFolder.mkdirs();
                if (created) {
                    sendMessage("§a配置文件夹已创建：§e" + configFolder.getAbsolutePath());
                } else {
                    sendMessage("§c无法创建配置文件夹！");
                    return;
                }
            }
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
                if (desktop.isSupported(java.awt.Desktop.Action.OPEN)) {
                    desktop.open(configFolder);
                    sendMessage("§a已打开配置文件夹");
                } else {
                    sendMessage("§c当前系统不支持打开文件夹");
                    sendMessage("§7配置文件夹位置：§e" + configFolder.getAbsolutePath());
                }
            } else {
                sendMessage("§c当前系统不支持打开文件夹");
                sendMessage("§7配置文件夹位置：§e" + configFolder.getAbsolutePath());
            }

        } catch (Exception e) {
            sendMessage("§c打开配置文件夹失败：" + e.getMessage());
            e.printStackTrace();
        }
    }
}