package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.consent.model.ConsentCreationResponse;
import in.org.projecteka.hiu.consent.model.ConsentRequestDetails;
import in.org.projecteka.hiu.consent.model.consentManager.ConsentRepresentation;
import org.jeasy.random.EasyRandom;

public class TestBuilders {

    private static final EasyRandom easyRandom = new EasyRandom();

    static ConsentCreationResponse.ConsentCreationResponseBuilder consentCreationResponse() {
        return easyRandom.nextObject(ConsentCreationResponse.ConsentCreationResponseBuilder.class);
    }

    static ConsentRequestDetails.ConsentRequestDetailsBuilder consentRequestDetails() {
        return easyRandom.nextObject(ConsentRequestDetails.ConsentRequestDetailsBuilder.class);
    }

    static ConsentRepresentation.ConsentRepresentationBuilder consentRepresentation() {
        return easyRandom.nextObject(ConsentRepresentation.ConsentRepresentationBuilder.class);
    }
}
