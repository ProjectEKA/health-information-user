package in.org.projecteka.hiu.consent.model.consentmanager;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@Builder
@NoArgsConstructor
@Data
public class ConsentAcknowledgement {
    private ConsentAcknowledgementStatus status;
    private String consentId;
}
