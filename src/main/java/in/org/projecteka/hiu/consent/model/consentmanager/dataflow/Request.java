package in.org.projecteka.hiu.consent.model.consentmanager.dataflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Request {
    private Consent consent;
    private HIDataRange hiDataRange;
    private String callBackUrl;
    //TODO: Add KeyMaterial as part of encryption
}
