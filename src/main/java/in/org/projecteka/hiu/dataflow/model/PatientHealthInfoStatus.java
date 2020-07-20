package in.org.projecteka.hiu.dataflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class PatientHealthInfoStatus {
    private final String hipId;
    private final String requestId;
    private final DataRequestStatus status;
}
