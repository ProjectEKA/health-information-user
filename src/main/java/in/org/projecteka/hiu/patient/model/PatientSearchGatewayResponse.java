package in.org.projecteka.hiu.patient.model;

import in.org.projecteka.hiu.common.GatewayResponse;
import in.org.projecteka.hiu.common.RespError;
import in.org.projecteka.hiu.patient.PatientRepresentation;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.util.UUID;

@ToString
@Data
@Builder
public class PatientSearchGatewayResponse {
    private UUID requestId;
    private String timestamp;
    private PatientRepresentation patient;
    private RespError error;
    private GatewayResponse resp;

    public static PatientSearchGatewayResponse empty() {
        return PatientSearchGatewayResponse
                .builder()
                .patient(PatientRepresentation.builder().build())
                .resp(GatewayResponse.builder().build())
                .build();
    }
}
