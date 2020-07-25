package in.org.projecteka.hiu.dataprocessor.model;

import in.org.projecteka.hiu.dataflow.model.DataNotificationRequest;
import in.org.projecteka.hiu.dataflow.model.KeyMaterial;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Builder
@Getter
@Setter
public class DataContext {
    private DataNotificationRequest notifiedData;
    private Path dataFilePath;
    private String dataPartNumber;
    private List<TrackedResourceReference> trackedResources;
    private static final Logger logger = LoggerFactory.getLogger(DataContext.class);

    public Path getLocalStoragePath() {
        return dataFilePath.getParent();
    }

    public String getTransactionId() {
        return notifiedData.getTransactionId();
    }

    public KeyMaterial getKeyMaterial(){
        return notifiedData.getKeyMaterial();
    }

    public void addTrackedResources(List<TrackedResourceReference> trackedResources) {
        this.trackedResources.addAll(trackedResources);
    }
    public LocalDateTime latestResourceDate() {
        if (trackedResources == null || trackedResources.isEmpty()) {
            return null;
        }
        List<LocalDateTime> dateTimes = trackedResources.stream()
                .map(res -> res.getLocalDateTime())
                .filter(resDate -> resDate != null).collect(Collectors.toList());
        return dateTimes.isEmpty() ?  null : dateTimes.stream().max(LocalDateTime::compareTo).get();
    }

    public int getNumberOfEntries() {
        if (notifiedData == null) {
            logger.info("There are no notified data in this transaction.");
            return 0;
        }
        if (notifiedData.getEntries() == null ) {
            logger.info("Notified data does not have any entry");
            return 0;
        }
        return notifiedData.getEntries().size();
    }
}
