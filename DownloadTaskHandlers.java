import javafx.application.Platform;
import javafx.concurrent.WorkerStateEvent;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.geometry.Pos;

/**
 * Handlers for download task state changes and UI updates.
 */
public class DownloadTaskHandlers {
    
    public static void bindProgressToBar(DownloadFileTask task, ProgressBar bar) {
        task.progressProperty().addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> {
                bar.setProgress(newVal.doubleValue());
                bar.setVisible(true);
            });
        });
    }
    
    public static void bindMessageToLabel(DownloadFileTask task, Label label) {
        task.messageProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                Platform.runLater(() -> {
                    label.setText(newVal);
                    updateLabelStyle(label, newVal);
                    // Set alignment to left for progress text
                    label.setAlignment(Pos.CENTER_LEFT);
                });
            }
        });
    }
    
    private static void updateLabelStyle(Label label, String message) {
        if (message.contains("Download completed") || message.contains(I18n.get("status.downloadCompleted").replace("{0}", ""))) {
            label.setStyle("-fx-text-fill: green;");
        } else if (message.contains("failed") || message.contains("error") || message.contains("Error")) {
            label.setStyle("-fx-text-fill: red;");
        } else if (message.contains("cancelled")) {
            label.setStyle("-fx-text-fill: orange;");
        } else {
            label.setStyle("-fx-text-fill: black;");
        }
    }
    
    public static void handleSucceeded(DownloadFileTask task, Label progressLabel, 
                                     Label resultLabel, java.io.File outputFile, Runnable cleanupAction) {
        task.setOnSucceeded(event -> {
            Platform.runLater(() -> {
                progressLabel.setText(I18n.get("result.completed"));
                progressLabel.setStyle("-fx-text-fill: green;");
                progressLabel.setAlignment(Pos.CENTER_LEFT); // Align left
                
                if (resultLabel != null) {
                    resultLabel.setText(I18n.format("result.savedTo", outputFile.getAbsolutePath()));
                    resultLabel.setVisible(true);
                }
                
                if (cleanupAction != null) {
                    cleanupAction.run();
                }
            });
        });
    }
    
    public static void handleFailed(DownloadFileTask task, Label progressLabel, 
                                  Runnable cleanupAction) {
        task.setOnFailed(event -> {
            Platform.runLater(() -> {
                Throwable ex = task.getException();
                progressLabel.setText(I18n.format("result.failed", 
                    (ex != null ? ex.getLocalizedMessage() : I18n.get("error.unknown"))));
                progressLabel.setStyle("-fx-text-fill: red;");
                progressLabel.setAlignment(Pos.CENTER_LEFT); // Align left
                
                if (cleanupAction != null) {
                    cleanupAction.run();
                }
            });
        });
    }
    
    public static void handleCancelled(DownloadFileTask task, Label progressLabel, 
                                     Runnable cleanupAction) {
        task.setOnCancelled(event -> {
            Platform.runLater(() -> {
                progressLabel.setText(I18n.get("result.cancelled"));
                progressLabel.setStyle("-fx-text-fill: orange;");
                progressLabel.setAlignment(Pos.CENTER_LEFT); // Align left
                
                if (cleanupAction != null) {
                    cleanupAction.run();
                }
            });
        });
    }
    
    public static void setupDownloadHandlers(DownloadFileTask task, ProgressBar progressBar, 
                                           Label progressLabel, Label resultLabel, 
                                           java.io.File outputFile, Runnable cleanupAction) {
        if (progressBar != null) {
            bindProgressToBar(task, progressBar);
        }
        
        if (progressLabel != null) {
            bindMessageToLabel(task, progressLabel);
        }
        
        handleSucceeded(task, progressLabel, resultLabel, outputFile, cleanupAction);
        handleFailed(task, progressLabel, cleanupAction);
        handleCancelled(task, progressLabel, cleanupAction);
    }
}