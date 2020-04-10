package in.org.projecteka.hiu.dataflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class DataFlowRequest {
    private Consent consent;
    private HIDataRange hiDataRange;
    private String dataPushUrl;
    private KeyMaterial keyMaterial;
}
