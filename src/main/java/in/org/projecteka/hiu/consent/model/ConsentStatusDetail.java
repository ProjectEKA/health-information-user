package in.org.projecteka.hiu.consent.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ConsentStatusDetail {
    String id;
    ConsentStatus status;
    List<ConsentArtefactReference> consentArtefacts;
}
