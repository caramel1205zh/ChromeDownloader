import javafx.concurrent.Task;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.net.URISyntaxException;
import java.io.IOException;

/**
 * Task for fetching download links from Google Update service.
 */
public class FetchLinksTask extends Task<List<String>> {
    private final String versionLabel;
    private final ProxySettings proxySettings;
    
    public FetchLinksTask(String versionLabel) {
        this(versionLabel, new ProxySettings());
    }
    
    public FetchLinksTask(String versionLabel, ProxySettings proxySettings) {
        if (versionLabel == null || versionLabel.trim().isEmpty()) {
            throw new IllegalArgumentException("Version label cannot be null or empty");
        }
        
        this.versionLabel = versionLabel;
        this.proxySettings = proxySettings != null ? proxySettings : new ProxySettings();
    }

    @Override
    protected List<String> call() throws Exception {
        String sessionid = UUID.randomUUID().toString().toUpperCase();
        String requestid = UUID.randomUUID().toString().toUpperCase();

        Map<String, String> config = PlatformUtils.getVersionMapping().get(versionLabel);
        if (config == null) {
            throw new IllegalArgumentException("Unsupported version: " + versionLabel);
        }

        String channel = config.get("channel");
        String appid = config.get("appid");
        String platform = PlatformUtils.getPlatformForUpdateService();
        String arch = PlatformUtils.getArchForUpdateService();

        String osVersion = PlatformUtils.isWindows() ? "10.0" : "13.0";

        URI uri = new URI("https://tools.google.com/service/update2");
        HttpURLConnection connection;
        
        if (proxySettings.isUseProxy()) {
            Proxy proxy = proxySettings.createProxy();
            connection = (HttpURLConnection) uri.toURL().openConnection(proxy);
            
            if (proxySettings.hasAuthentication()) {
                String authHeader = "Basic " + proxySettings.getProxyAuthorization();
                connection.setRequestProperty("Proxy-Authorization", authHeader);
            }
        } else {
            connection = (HttpURLConnection) uri.toURL().openConnection();
        }
        
        connection.setRequestMethod("POST");
        connection.setRequestProperty("User-Agent", "Google Update/1.3.32.7;winhttp;cup-ecdsa");
        connection.setRequestProperty("Content-Type", "text/xml; charset=UTF-8");
        connection.setRequestProperty("Host", "tools.google.com");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setDoOutput(true);

        String requestBody = String.format(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<request protocol=\"3.0\" version=\"1.3.23.9\" shell_version=\"1.3.21.103\" ismachine=\"0\" " +
            "sessionid=\"%s\" installsource=\"ondemandcheckforupdate\" requestid=\"%s\" dedup=\"cr\">" +
            "<hw physmemory=\"1200000\" sse=\"1\" sse2=\"1\" sse3=\"1\" ssse3=\"1\" sse41=\"1\" sse42=\"1\" avx=\"1\"/>" +
            "<os platform=\"%s\" version=\"%s\" arch=\"%s\"/>" +
            "<app appid=\"%s\" version=\"\" nextversion=\"\" ap=\"%s\" lang=\"en-US\">" +
            "    <updatecheck/>" +
            "</app>" +
            "</request>",
            sessionid, requestid, platform, osVersion, arch, appid, channel
        );

        try (OutputStream os = connection.getOutputStream()) {
            os.write(requestBody.getBytes("UTF-8"));
        }

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            String errorMessage = connection.getResponseMessage();
            connection.disconnect();
            throw new IOException(I18n.format("error.http", responseCode, errorMessage));
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(connection.getInputStream());
        connection.disconnect();

        NodeList appNodes = doc.getElementsByTagName("app");
        if (appNodes.getLength() == 0) {
            throw new RuntimeException(I18n.get("error.noAppElement"));
        }
        
        Element appElement = (Element) appNodes.item(0);
        NodeList urlNodes = appElement.getElementsByTagName("url");
        NodeList packageNodes = appElement.getElementsByTagName("package");

        List<String> links = new ArrayList<>();
        for (int i = 0; i < urlNodes.getLength(); i++) {
            Element urlEl = (Element) urlNodes.item(i);
            String codebase = urlEl.getAttribute("codebase");
            if (!codebase.endsWith("/")) {
                codebase += "/";
            }
            for (int j = 0; j < packageNodes.getLength(); j++) {
                Element pkgEl = (Element) packageNodes.item(j);
                String name = pkgEl.getAttribute("name");
                if (name != null && !name.isEmpty()) {
                    String fullUrl = codebase + name;
                    if (!fullUrl.startsWith("https://www.google.com/dl/") && 
                        !fullUrl.startsWith("http://www.google.com/dl/")) {
                        links.add(fullUrl);
                    }
                }
            }
        }
        
        if (links.isEmpty()) {
            throw new RuntimeException(I18n.get("error.noLinks"));
        }
        
        return links;
    }
}