package in.org.projecteka.hiu.dataflow.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@Data
@Builder
@NoArgsConstructor
//@JsonIgnoreProperties(ignoreUnknown = true)
public class DataNotificationRequest {
    private String transactionId;
    private List<Entry> entries;
    private KeyMaterial keyMaterial;
}
