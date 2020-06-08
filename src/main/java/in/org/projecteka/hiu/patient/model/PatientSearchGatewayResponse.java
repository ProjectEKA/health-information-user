package in.org.projecteka.hiu.patient.model;

import in.org.projecteka.hiu.common.GatewayResponse;
import in.org.projecteka.hiu.common.RespError;
import in.org.projecteka.hiu.patient.PatientRepresentation;
import lombok.Data;

import java.util.UUID;

@Data
public class PatientSearchGatewayResponse {
    private UUID requestId;
    private String timestamp;
    private PatientRepresentation patient;
    private RespError error;
    private GatewayResponse resp;
}
