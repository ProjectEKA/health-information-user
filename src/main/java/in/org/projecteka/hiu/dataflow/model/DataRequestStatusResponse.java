package in.org.projecteka.hiu.dataflow.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;

import java.util.List;

@Builder
@AllArgsConstructor
@Value
public class DataRequestStatusResponse {
    List<PatientHealthInfoStatus> statuses;
}
