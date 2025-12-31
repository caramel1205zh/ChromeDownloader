import javafx.concurrent.Task;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.Proxy;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.io.File;
import java.net.SocketTimeoutException;
import java.net.ConnectException;
import java.net.URISyntaxException;

/**
 * Task for downloading files with progress tracking and proxy support.
 */
public class DownloadFileTask extends Task<Void> {
    private final String url;
    private final File outputFile;
    private final ProxySettings proxySettings;
    private HttpURLConnection connection;
    private ReadableByteChannel rbc;
    private FileOutputStream fos;
    
    public DownloadFileTask(String url, File outputFile) {
        this(url, outputFile, new ProxySettings());
    }
    
    public DownloadFileTask(String url, File outputFile, ProxySettings proxySettings) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("URL cannot be null or empty");
        }
        if (outputFile == null) {
            throw new IllegalArgumentException("Output file cannot be null");
        }
        
        this.url = url;
        this.outputFile = outputFile;
        this.proxySettings = proxySettings != null ? proxySettings : new ProxySettings();
        this.connection = null;
        this.rbc = null;
        this.fos = null;
    }

    @Override
    protected Void call() throws Exception {
        try {
            return downloadFile();
        } catch (Exception e) {
            String cleanMessage = cleanErrorMessage(e.getMessage());
            throw new IOException(cleanMessage, e);
        }
    }
    
    private Void downloadFile() throws Exception {
        Proxy proxy = proxySettings.createProxy();
        
        if (proxySettings.isUseProxy()) {
            try {
                URI uri = new URI(this.url);
                connection = (HttpURLConnection) uri.toURL().openConnection(proxy);
                
                if (proxySettings.hasAuthentication()) {
                    String authHeader = "Basic " + proxySettings.getProxyAuthorization();
                    connection.setRequestProperty("Proxy-Authorization", authHeader);
                }
            } catch (URISyntaxException e) {
                throw new IOException(I18n.format("error.invalidURL", url), e);
            }
        } else {
            try {
                connection = (HttpURLConnection) new URI(url).toURL().openConnection();
            } catch (URISyntaxException e) {
                throw new IOException(I18n.format("error.invalidURL", url), e);
            }
        }
        
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        
        int responseCode;
        try {
            updateMessage(I18n.format("status.connecting", url));
            responseCode = connection.getResponseCode();
        } catch (SocketTimeoutException e) {
            if (proxySettings.isUseProxy()) {
                throw new IOException(I18n.get("error.proxyTimeout"), e);
            } else {
                throw new IOException(I18n.get("error.connectionTimeout"), e);
            }
        } catch (ConnectException e) {
            if (proxySettings.isUseProxy()) {
                throw new IOException(I18n.format("error.connectToProxy", 
                    proxySettings.getProxyHost(), proxySettings.getProxyPort()), e);
            } else {
                throw new IOException(I18n.get("error.cannotConnect"), e);
            }
        }
        
        if (responseCode != HttpURLConnection.HTTP_OK) {
            String errorMsg = connection.getResponseMessage();
            connection.disconnect();
            if (proxySettings.isUseProxy()) {
                throw new IOException(I18n.format("error.httpViaProxy", responseCode, errorMsg));
            } else {
                throw new IOException(I18n.format("error.http", responseCode, errorMsg));
            }
        }
        
        int fileSize = connection.getContentLength();
        if (fileSize <= 0) {
            updateMessage(I18n.get("status.unknownSize"));
        } else {
            if (fileSize > 2L * 1024 * 1024 * 1024) {
                connection.disconnect();
                throw new IOException(I18n.format("error.fileTooLarge", formatFileSize(fileSize)));
            }
            updateMessage(I18n.format("status.totalSize", formatFileSize(fileSize)));
        }

        if (outputFile.exists()) {
            updateMessage(I18n.format("status.overwriteWarning", outputFile.getName()));
        }

        long totalRead = 0;
        
        try {
            rbc = Channels.newChannel(connection.getInputStream());
            fos = new FileOutputStream(outputFile);

            ByteBuffer buffer = ByteBuffer.allocateDirect(1024 * 1024);
            int bytesRead;
            long lastUpdateTime = System.currentTimeMillis();
            long lastBytesRead = 0;

            while ((bytesRead = rbc.read(buffer)) != -1) {
                if (isCancelled()) {
                    updateMessage(I18n.get("status.downloadCancelled"));
                    connection.disconnect();
                    return null;
                }
                
                buffer.flip();
                fos.getChannel().write(buffer);
                buffer.clear();
                totalRead += bytesRead;

                if (fileSize > 0) {
                    double progress = (double) totalRead / fileSize;
                    updateProgress(progress, 1.0);
                    
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastUpdateTime >= 1000) {
                        double bytesPerSecond = (totalRead - lastBytesRead) * 1000.0 / (currentTime - lastUpdateTime);
                        double megabytesPerSecond = bytesPerSecond / (1024 * 1024);
                        updateMessage(I18n.format("status.progressFormat", 
                            progress * 100, megabytesPerSecond));
                        lastUpdateTime = currentTime;
                        lastBytesRead = totalRead;
                    }
                } else {
                    updateMessage(I18n.format("status.downloaded", formatFileSize(totalRead)));
                }
            }
        } catch (SocketTimeoutException e) {
            if (proxySettings.isUseProxy()) {
                throw new IOException(I18n.get("error.proxyTimeout"), e);
            } else {
                throw new IOException(I18n.get("error.connectionTimeout"), e);
            }
        } finally {
            cleanupResources();
        }
        
        updateMessage(I18n.format("status.downloadCompleted", formatFileSize(totalRead)));
        return null;
    }
    
    @Override
    protected void cancelled() {
        super.cancelled();
        cleanupResources();
    }
    
    @Override
    protected void failed() {
        super.failed();
        cleanupResources();
    }
    
    @Override
    protected void succeeded() {
        super.succeeded();
        cleanupResources();
    }
    
    /**
     * Cleans up resources (streams, channels, connections).
     */
    private void cleanupResources() {
        if (fos != null) {
            try {
                fos.flush();
                fos.close();
            } catch (IOException e) {
                System.err.println("Failed to close FileOutputStream: " + e.getMessage());
            } finally {
                fos = null;
            }
        }
        
        if (rbc != null) {
            try {
                rbc.close();
            } catch (IOException e) {
                System.err.println("Failed to close ReadableByteChannel: " + e.getMessage());
            } finally {
                rbc = null;
            }
        }
        
        if (connection != null) {
            try {
                connection.disconnect();
            } catch (Exception e) {
                System.err.println("Failed to disconnect connection: " + e.getMessage());
            } finally {
                connection = null;
            }
        }
        
        if (isCancelled() && outputFile.exists() && outputFile.length() > 0) {
            try {
                if (outputFile.delete()) {
                    System.out.println("Deleted incomplete file: " + outputFile.getName());
                }
            } catch (SecurityException e) {
                System.err.println("Cannot delete file due to security restrictions: " + outputFile.getName());
            }
        }
    }
    
    /**
     * Cleans error messages for user display.
     */
    private String cleanErrorMessage(String originalMessage) {
        if (originalMessage == null) {
            return I18n.get("error.unknown");
        }
        
        if (originalMessage.contains("timed out") || 
            originalMessage.contains("timeout") || 
            originalMessage.contains("getsockopt")) {
            return I18n.get("error.connectionTimeout");
        }
        
        if (originalMessage.contains("Connection refused")) {
            return I18n.get("error.connectionRefused");
        }
        
        if (originalMessage.contains("ConnectException")) {
            return I18n.get("error.connectionFailed");
        }
        
        int colonIndex = originalMessage.indexOf(":");
        if (colonIndex > 0) {
            return originalMessage.substring(0, colonIndex).trim();
        }
        
        return originalMessage;
    }
    
    /**
     * Formats file size for display.
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}