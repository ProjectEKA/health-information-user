package in.org.projecteka.hiu.dataflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@AllArgsConstructor
@Builder
@Value
public class PatientDataRequestMapping {
    String hipId;
    String dataRequestId;
    String consentRequestId;
}
