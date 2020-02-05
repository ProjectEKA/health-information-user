package in.org.projecteka.hiu.consent.model.consentmanager.dataflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DataFlowRequestResponse {
    private String transactionId;
}
