package in.org.projecteka.hiu.consent.model;

import in.org.projecteka.hiu.common.GatewayResponse;
import in.org.projecteka.hiu.common.RespError;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class ConsentFetchResponse {
    private String requestId;
    private String timestamp;
    private ConsentArtefactResponse consentArtefactResponse;
    private RespError error;
    private GatewayResponse resp;
}
