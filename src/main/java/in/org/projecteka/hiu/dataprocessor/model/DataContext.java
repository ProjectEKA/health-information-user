package in.org.projecteka.hiu.dataprocessor.model;

import in.org.projecteka.hiu.dataflow.model.DataNotificationRequest;
import in.org.projecteka.hiu.dataflow.model.KeyMaterial;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.hl7.fhir.r4.model.ResourceType;

import java.nio.file.Path;
import java.util.Date;

@Builder
@Getter
@Setter
public class DataContext {
    private DataNotificationRequest notifiedData;
    private Path dataFilePath;
    private String dataPartNumber;

    public Path getLocalStoragePath() {
        return dataFilePath.getParent();
    }

    public String getTransactionId() {
        return notifiedData.getTransactionId();
    }

    public KeyMaterial getKeyMaterial(){
        return notifiedData.getKeyMaterial();
    }
}
