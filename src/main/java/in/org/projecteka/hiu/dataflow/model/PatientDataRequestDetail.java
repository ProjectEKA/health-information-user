package in.org.projecteka.hiu.dataflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class PatientDataRequestDetail {
    private final String hipId;
    private final String dataRequestId;
    private final String patientId;
    private final String consentRequestId;
    private final String consentArtefactId;
    private final LocalDateTime patientDataRequestedAt;
    private final LocalDateTime dataFlowRequestedAt;
    private final HealthInfoStatus dataPartStatus;
}
