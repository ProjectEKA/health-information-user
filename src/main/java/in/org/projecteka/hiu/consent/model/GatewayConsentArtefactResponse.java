package in.org.projecteka.hiu.consent.model;

import in.org.projecteka.hiu.common.GatewayResponse;
import in.org.projecteka.hiu.common.RespError;
import lombok.Data;

import java.util.UUID;

@Data
public class GatewayConsentArtefactResponse {
    private UUID requestId;
    private String timestamp;
    private ConsentArtefactResponse consent;
    private RespError error;
    private GatewayResponse resp;
}
