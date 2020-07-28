package in.org.projecteka.hiu.consent.model;

import in.org.projecteka.hiu.common.GatewayResponse;
import in.org.projecteka.hiu.common.RespError;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Builder
public class GatewayConsentArtefactResponse {
    private UUID requestId;
    private String timestamp;
    private ConsentArtefactResponse consent;
    private RespError error;
    private GatewayResponse resp;
}
