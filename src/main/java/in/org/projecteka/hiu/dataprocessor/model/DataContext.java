package in.org.projecteka.hiu.dataprocessor.model;

import in.org.projecteka.hiu.dataflow.model.DataNotificationRequest;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.nio.file.Path;

@Builder
@Getter
@Setter
public class DataContext {
    private DataNotificationRequest notifiedData;
    private Path dataFilePath;

    public Path getLocalStoragePath() {
        return dataFilePath.getParent();
    }
}
