import java.util.HashMap;
import java.util.Map;

public class PlatformUtils {

    private static final String OS = System.getProperty("os.name").toLowerCase();
    private static final String ARCH = System.getProperty("os.arch").toLowerCase();

    public static boolean isWindows() {
        return OS.contains("win");
    }

    public static boolean isMac() {
        return OS.contains("mac") || OS.contains("darwin");
    }

    public static String getArchForUpdateService() {
        if (isWindows()) {
            return "x64";
        } else if (isMac()) {
            String arch = System.getProperty("os.arch").toLowerCase();
            if (arch.contains("aarch64") || arch.contains("arm")) {
                return "arm64";
            } else {
                return "x64";
            }
        } else {
            throw new UnsupportedOperationException("Unsupported OS: " + OS);
        }
    }

    public static String getPlatformForUpdateService() {
        if (isWindows()) {
            return "win";
        } else if (isMac()) {
            return "mac";
        } else {
            throw new UnsupportedOperationException("Unsupported OS: " + OS);
        }
    }

    public static Map<String, Map<String, String>> getVersionMapping() {
        Map<String, Map<String, String>> map = new HashMap<>();
        if (isWindows()) {
            String stableAppId = "{8A69D345-D564-463C-AFF1-A69D9E530F96}";
            String canaryAppId = "{4EA16AC7-FD5A-47C3-875B-DBF4A2008C20}";
            map.put("Stable", Map.of("channel", "x64-stable-multi-chrome", "appid", stableAppId));
            map.put("Beta", Map.of("channel", "x64-beta-multi-chrome", "appid", stableAppId));
            map.put("Dev", Map.of("channel", "x64-dev-statsdef_1", "appid", stableAppId));
            map.put("Canary", Map.of("channel", "x64-canary", "appid", canaryAppId));
        } else if (isMac()) {
            map.put("Stable", Map.of("channel", "", "appid", "com.google.Chrome"));
            map.put("Beta", Map.of("channel", "betachannel", "appid", "com.google.Chrome.Beta"));
            map.put("Dev", Map.of("channel", "devchannel", "appid", "com.google.Chrome.Dev"));
            map.put("Canary", Map.of("channel", "canarychannel", "appid", "com.google.Chrome.Canary"));
        } else {
            throw new UnsupportedOperationException("Unsupported OS: " + OS);
        }
        return map;
    }

    public static String getOSDisplayName() {
        if (isWindows()) return "Windows";
        if (isMac()) return "macOS";
        return "Unknown";
    }
}