package in.org.projecteka.hiu.dataflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Builder
@AllArgsConstructor
@Data
public class DataPartDetail {
    private String transactionId;
    private String status;
    private String consentArtifactId;
    private String hipId;
    private String consentRequestId;
    private String requester;
}
