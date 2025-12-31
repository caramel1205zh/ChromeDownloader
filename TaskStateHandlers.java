import javafx.event.EventHandler;
import javafx.concurrent.WorkerStateEvent;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import java.util.List;
import java.lang.ref.WeakReference;

/**
 * Handlers for task state changes (running, succeeded, failed).
 */
public class TaskStateHandlers {
    
    public static EventHandler<WorkerStateEvent> createFetchLinksRunningHandler(
            Label progressLabel, ProxySettings proxySettings) {
        
        if (progressLabel == null) {
            throw new IllegalArgumentException("Progress label cannot be null");
        }
        
        WeakReference<Label> labelRef = new WeakReference<>(progressLabel);
        WeakReference<ProxySettings> proxyRef = new WeakReference<>(proxySettings);
        
        return event -> {
            Label label = labelRef.get();
            ProxySettings proxy = proxyRef.get();
            
            if (label != null) {
                label.setVisible(true);
                String proxyInfo = proxy != null && proxy.isUseProxy() 
                    ? I18n.format("status.fetchingVia", 
                        I18n.getLocalizedProxyType(proxy.getProxyTypeString())) 
                    : I18n.get("status.fetching");
                label.setText(proxyInfo);
                label.setStyle("-fx-text-fill: blue;");
            }
        };
    }
    
    public static EventHandler<WorkerStateEvent> createFetchLinksSucceededHandler(
            Label progressLabel, ListView<String> linkListView,
            List<String> currentDownloadLinks, Button fetchButton) {
        
        if (progressLabel == null || linkListView == null || 
            currentDownloadLinks == null || fetchButton == null) {
            throw new IllegalArgumentException("All parameters must not be null");
        }
        
        WeakReference<Label> labelRef = new WeakReference<>(progressLabel);
        WeakReference<ListView<String>> listRef = new WeakReference<>(linkListView);
        WeakReference<Button> buttonRef = new WeakReference<>(fetchButton);
        
        return event -> {
            Label label = labelRef.get();
            ListView<String> listView = listRef.get();
            Button button = buttonRef.get();
            
            if (label != null && listView != null && button != null) {
                try {
                    @SuppressWarnings("unchecked")
                    List<String> links = (List<String>) event.getSource().getValue();
                    
                    if (links == null) {
                        throw new ClassCastException("Download links are null");
                    }
                    
                    currentDownloadLinks.clear();
                    currentDownloadLinks.addAll(links);
                    
                    listView.getItems().clear();
                    if (currentDownloadLinks.isEmpty()) {
                        listView.getItems().add("No download links found.");
                        label.setVisible(true);
                        label.setText("No download links found. Please try again later.");
                        label.setStyle("-fx-text-fill: red;");
                    } else {
                        for (int i = 0; i < currentDownloadLinks.size(); i++) {
                            listView.getItems().add((i + 1) + ". " + currentDownloadLinks.get(i));
                        }
                        label.setVisible(false);
                    }
                    button.setDisable(false);
                } catch (ClassCastException e) {
                    label.setVisible(true);
                    label.setText("Failed to fetch links: Invalid response format");
                    label.setStyle("-fx-text-fill: red;");
                    listView.getItems().clear();
                    listView.getItems().add("Error: Invalid response format.");
                    button.setDisable(false);
                }
            }
        };
    }
    
    public static EventHandler<WorkerStateEvent> createFetchLinksFailedHandler(
            Label progressLabel, ListView<String> linkListView, Button fetchButton) {
        
        if (progressLabel == null || linkListView == null || fetchButton == null) {
            throw new IllegalArgumentException("All parameters must not be null");
        }
        
        WeakReference<Label> labelRef = new WeakReference<>(progressLabel);
        WeakReference<ListView<String>> listRef = new WeakReference<>(linkListView);
        WeakReference<Button> buttonRef = new WeakReference<>(fetchButton);
        
        return event -> {
            Label label = labelRef.get();
            ListView<String> listView = listRef.get();
            Button button = buttonRef.get();
            
            if (label != null && listView != null && button != null) {
                Throwable ex = event.getSource().getException();
                label.setVisible(true);
                label.setText("Failed to fetch links: " + 
                    (ex != null ? ex.getLocalizedMessage() : "Unknown error"));
                label.setStyle("-fx-text-fill: red;");
                listView.getItems().clear();
                listView.getItems().add("Error occurred while fetching links.");
                button.setDisable(false);
            }
        };
    }
}