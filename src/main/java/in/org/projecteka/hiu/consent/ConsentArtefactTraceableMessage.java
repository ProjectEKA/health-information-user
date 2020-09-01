package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.consent.model.ConsentArtefactReference;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class ConsentArtefactTraceableMessage {
    String correlationId;
    ConsentArtefactReference consentArtefactReference;
}
