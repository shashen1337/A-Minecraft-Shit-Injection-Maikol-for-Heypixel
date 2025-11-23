package Maikol.auth;

import Maikol.Maikolclient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import oshi.SystemInfo;
import oshi.hardware.*;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class AuthManager {

    private static final String[] SERVER_URLS = {
            "http://maikolawa.shop:8899/verify.php",
            "http://backup1.maikolawa.shop:8899/verify.php",
            "http://backup2.maikolawa.shop:8899/verify.php"
    };

    private static final String AUTH_FILE = ".maikol_auth.enc";
    private static final AtomicBoolean isAuthenticated = new AtomicBoolean(false);
    private static String currentHWID = "";
    private static String currentKey = "";
    private static long lastVerifyTime = 0;
    private static final long REVERIFY_INTERVAL = 600000;

    private static final boolean ENABLE_ADVANCED_VERIFICATION = false;
    private static final String INTEGRITY_KEY = "8964破解全家死光亲妈猪逼被操烂亲爹没鸡巴生小孩没屁眼操你血妈";

    private static String expireDate = null;
    private static Integer remainingDays = null;
    private static boolean isPermanent = false;

    private final Minecraft mc;
    private Thread heartbeatThread;

    public AuthManager() {
        this.mc = Minecraft.getInstance();
        if (detectBasicTampering()) {
            //System.err.println("检测到代码篡改");
            System.exit(1);
        }

        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.HIGHEST,
                false,
                ClientChatEvent.class,
                this::onClientChat
        );

        currentHWID = getHardwareID();
        loadSavedKey();

        if (!currentKey.isEmpty()) {
            verifyKey(currentKey, true);
        }

        startHeartbeat();
    }

    private boolean detectBasicTampering() {
        try {
            if (java.lang.management.ManagementFactory.getRuntimeMXBean()
                    .getInputArguments().toString().contains("-agentlib:jdwp")) {
                return true;
            }
            try {
                Class.forName("Maikol.auth.ProxyServer");
                return true;
            } catch (ClassNotFoundException e) {
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public void onClientChat(ClientChatEvent event) {
        String message = event.getMessage();
        if (message.startsWith(".auth ")) {
            event.setCanceled(true);
            String key = message.substring(6).trim();
            if (key.isEmpty()) {
                sendMessage("§c用法: .auth <密钥>");
                return;
            }
            currentKey = key;
            verifyKey(key, false);
        }
    }

    private void verifyKey(String key, boolean silent) {
        if (!silent) {
            sendMessage("§e正在验证密钥...");
        }

        Thread verifyThread = new Thread(() -> {
            try {
                String response;

                if (ENABLE_ADVANCED_VERIFICATION) {
                    long timestamp = System.currentTimeMillis();
                    String nonce = generateNonce();
                    String signature = calculateSignature(key, currentHWID, timestamp, nonce);
                    response = sendAdvancedVerificationRequest(key, currentHWID, timestamp, nonce, signature);
                } else {
                    response = sendBasicVerificationRequest(key, currentHWID);
                }

                if (response != null && response.contains("\"success\":true")) {
                    isAuthenticated.set(true);
                    lastVerifyTime = System.currentTimeMillis();

                    parseAuthResponse(response);

                    saveKey(key);

                    if (!silent) {
                        if (response.contains("密钥激活成功")) {
                            sendMessage("§a✓ 密钥激活成功！");
                            displayLicenseInfo();
                        } else {
                            sendMessage("§a✓ 验证成功！");
                            displayLicenseInfo();
                        }
                    }
                } else if (response != null && response.contains("\"success\":false")) {
                    handleAuthFailure(response, silent);
                } else {
                    handleAuthFailure(null, silent);
                }
            } catch (Exception e) {
                handleAuthFailure(null, silent);
                if (!silent) {
                    sendMessage("§c连接错误: " + e.getMessage());
                }
            }
        });
        verifyThread.setDaemon(true);
        verifyThread.start();
    }

    private void parseAuthResponse(String json) {
        try {
            expireDate = parseJsonString(json, "expire_date");

            String remainingStr = parseJsonString(json, "remaining_days");
            if (remainingStr != null && !remainingStr.equals("null")) {
                try {
                    remainingDays = Integer.parseInt(remainingStr);
                } catch (NumberFormatException e) {
                    remainingDays = null;
                }
            } else {
                remainingDays = null;
            }

            String validityStr = parseJsonString(json, "validity_days");
            if (validityStr != null && !validityStr.equals("null")) {
                try {
                    int validityDays = Integer.parseInt(validityStr);
                    isPermanent = (validityDays == 0);
                } catch (NumberFormatException e) {
                    isPermanent = false;
                }
            }

            String isPermanentStr = parseJsonString(json, "is_permanent");
            if (isPermanentStr != null) {
                isPermanent = isPermanentStr.equals("true") || isPermanentStr.equals("1");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private String parseJsonString(String json, String key) {
        try {
            String searchKey = "\"" + key + "\":";
            int keyIndex = json.indexOf(searchKey);
            if (keyIndex == -1) {
                return null;
            }

            int valueStart = keyIndex + searchKey.length();

            while (valueStart < json.length() && json.charAt(valueStart) == ' ') {
                valueStart++;
            }

            if (valueStart >= json.length()) {
                return null;
            }

            char firstChar = json.charAt(valueStart);
            if (firstChar == '"') {
                valueStart++;
                int valueEnd = json.indexOf('"', valueStart);
                if (valueEnd != -1) {
                    return json.substring(valueStart, valueEnd);
                }
            }
            else {
                int valueEnd = valueStart;
                while (valueEnd < json.length()) {
                    char c = json.charAt(valueEnd);
                    if (c == ',' || c == '}' || c == ' ' || c == '\n') {
                        break;
                    }
                    valueEnd++;
                }
                return json.substring(valueStart, valueEnd).trim();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void displayLicenseInfo() {
        if (isPermanent) {
            sendMessage("§e授权类型: §a永久授权");
        } else if (remainingDays != null) {
            sendMessage("§e剩余天数: §a" + remainingDays + " 天");
        } else if (expireDate != null && !expireDate.equals("null")) {
            sendMessage("§e过期时间: §a" + expireDate);
        }
    }

    private void handleAuthFailure(String response, boolean silent) {
        isAuthenticated.set(false);
        expireDate = null;
        remainingDays = null;
        isPermanent = false;
        deleteKey();

        if (!silent) {
            String errorMsg = response != null ? parseErrorMessage(response) : "验证失败";
            sendMessage("§c✗ " + errorMsg);
        }

        try {
            Maikolclient.disableAllModules();
        } catch (Exception e) {
        }
    }

    private String sendBasicVerificationRequest(String key, String hwid) throws Exception {
        Exception lastException = null;

        for (String serverUrl : SERVER_URLS) {
            try {
                URL url = new URL(serverUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", "MaikolClient/2.0");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                String jsonData = String.format("{\"key\":\"%s\",\"hwid\":\"%s\"}", key, hwid);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonData.getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = conn.getResponseCode();
                InputStream inputStream;
                if (responseCode >= 200 && responseCode < 300) {
                    inputStream = conn.getInputStream();
                } else {
                    inputStream = conn.getErrorStream();
                }

                String response = "";
                if (inputStream != null) {
                    try (Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8)) {
                        response = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
                    }
                }

                if (responseCode >= 400 && !response.isEmpty()) {
                    return response;
                }

                if (responseCode == 200) {
                    return response;
                } else {
                    throw new IOException("服务器返回错误码: " + responseCode);
                }
            } catch (Exception e) {
                lastException = e;
                continue;
            }
        }

        throw lastException != null ? lastException : new IOException("所有服务器连接失败");
    }

    private String sendAdvancedVerificationRequest(String key, String hwid, long timestamp,
                                                   String nonce, String signature) throws Exception {
        Exception lastException = null;

        for (String serverUrl : SERVER_URLS) {
            try {
                URL url = new URL(serverUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", "MaikolClient/2.0");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                String jsonData = String.format(
                        "{\"key\":\"%s\",\"hwid\":\"%s\",\"timestamp\":%d,\"nonce\":\"%s\",\"signature\":\"%s\"}",
                        key, hwid, timestamp, nonce, signature
                );

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonData.getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = conn.getResponseCode();
                InputStream inputStream = responseCode < 400 ?
                        conn.getInputStream() : conn.getErrorStream();

                if (inputStream != null) {
                    try (Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8)) {
                        String response = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
                        if (responseCode == 200 || responseCode >= 400) {
                            return response;
                        }
                    }
                }
            } catch (Exception e) {
                lastException = e;
                continue;
            }
        }

        throw lastException != null ? lastException : new IOException("所有服务器连接失败");
    }

    private String calculateSignature(String key, String hwid, long timestamp, String nonce) {
        try {
            String data = key + hwid + timestamp + nonce;
            return calculateHMAC(data, INTEGRITY_KEY);
        } catch (Exception e) {
            return "";
        }
    }

    private String calculateHMAC(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKey);
        byte[] hmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

        StringBuilder hexString = new StringBuilder();
        for (byte b : hmac) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private String generateNonce() {
        SecureRandom random = new SecureRandom();
        byte[] nonce = new byte[16];
        random.nextBytes(nonce);
        StringBuilder sb = new StringBuilder();
        for (byte b : nonce) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void startHeartbeat() {
        heartbeatThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(REVERIFY_INTERVAL);

                    if (isAuthenticated.get() && !currentKey.isEmpty()) {
                        long timeSinceLastVerify = System.currentTimeMillis() - lastVerifyTime;
                        if (timeSinceLastVerify >= REVERIFY_INTERVAL) {
                            verifyKey(currentKey, true);
                        }
                    } else if (!currentKey.isEmpty()) {
                        verifyKey(currentKey, true);
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }

    private String parseErrorMessage(String json) {
        try {
            int messageStart = json.indexOf("\"message\":\"");
            if (messageStart != -1) {
                messageStart += 11;
                int messageEnd = json.indexOf("\"", messageStart);
                if (messageEnd != -1) {
                    String encoded = json.substring(messageStart, messageEnd);
                    return decodeUnicode(encoded);
                }
            }
        } catch (Exception e) {
        }
        return "密钥无效或已过期";
    }

    private String decodeUnicode(String str) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < str.length()) {
            char c = str.charAt(i);
            if (c == '\\' && i + 1 < str.length() && str.charAt(i + 1) == 'u') {
                if (i + 5 < str.length()) {
                    try {
                        String hex = str.substring(i + 2, i + 6);
                        int code = Integer.parseInt(hex, 16);
                        result.append((char) code);
                        i += 6;
                        continue;
                    } catch (NumberFormatException e) {
                    }
                }
            }
            result.append(c);
            i++;
        }
        return result.toString();
    }


    private String getHardwareID() {
        try {
            SystemInfo si = new SystemInfo();
            HardwareAbstractionLayer hardware = si.getHardware();
            StringBuilder hwInfo = new StringBuilder();
            CentralProcessor processor = hardware.getProcessor();
            String cpuIdentifier = processor.getProcessorIdentifier().getIdentifier();
            hwInfo.append(cpuIdentifier);
            long totalMemory = hardware.getMemory().getTotal();
            hwInfo.append(totalMemory);
            List<GraphicsCard> graphicsCards = hardware.getGraphicsCards();
            if (!graphicsCards.isEmpty()) {
                for (GraphicsCard gpu : graphicsCards) {
                    hwInfo.append(gpu.getName());
                    hwInfo.append(gpu.getVRam());
                }
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(hwInfo.toString().getBytes(StandardCharsets.UTF_8));
            return bytesToAlphanumeric(hash, 24);
        } catch (Exception e) {
            e.printStackTrace();
            try {
                String fallback = System.getProperty("os.arch") +
                        System.getProperty("os.version") +
                        System.getProperty("java.vm.vendor");
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(fallback.getBytes(StandardCharsets.UTF_8));
                return bytesToAlphanumeric(hash, 24);
            } catch (Exception ex) {
                return "000000000000000000000000";
            }
        }
    }

    private String bytesToAlphanumeric(byte[] bytes, int length) {
        String base62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        StringBuilder result = new StringBuilder();
        BigInteger value = new BigInteger(1, bytes);
        BigInteger base = BigInteger.valueOf(62);
        while (value.compareTo(BigInteger.ZERO) > 0 && result.length() < length) {
            int remainder = value.mod(base).intValue();
            result.append(base62.charAt(remainder));
            value = value.divide(base);
        }
        while (result.length() < length) {
            result.append('0');
        }
        return result.substring(0, length);
    }

    private void saveKey(String key) {
        try {
            String encrypted = encryptKey(key, currentHWID);
            try (FileWriter writer = new FileWriter(AUTH_FILE)) {
                writer.write(encrypted);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadSavedKey() {
        File file = new File(AUTH_FILE);
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String encrypted = reader.readLine();
                if (encrypted != null && !encrypted.isEmpty()) {
                    currentKey = decryptKey(encrypted, currentHWID);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void deleteKey() {
        File file = new File(AUTH_FILE);
        if (file.exists()) {
            file.delete();
        }
        currentKey = "";
    }

    private String encryptKey(String key, String hwid) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            byte[] keyBytes = hwid.substring(0, 16).getBytes(StandardCharsets.UTF_8);
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(key.getBytes(StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            return java.util.Base64.getEncoder().encodeToString(key.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String decryptKey(String encrypted, String hwid) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            byte[] keyBytes = hwid.substring(0, 16).getBytes(StandardCharsets.UTF_8);
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decrypted = cipher.doFinal(java.util.Base64.getDecoder().decode(encrypted));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            try {
                return new String(java.util.Base64.getDecoder().decode(encrypted), StandardCharsets.UTF_8);
            } catch (Exception ex) {
                return "";
            }
        }
    }

    private void sendMessage(String message) {
        if (mc != null && mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(message));
        }
    }

    public static boolean isAuthenticated() {
        return isAuthenticated.get();
    }

    public static String getCurrentHWID() {
        return currentHWID;
    }

    public static String getExpireDate() {
        return expireDate;
    }

    public static Integer getRemainingDays() {
        return remainingDays;
    }

    public static boolean isPermanentLicense() {
        return isPermanent;
    }
}