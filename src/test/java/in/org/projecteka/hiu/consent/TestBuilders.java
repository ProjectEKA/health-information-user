package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.consent.model.ConsentCreationResponse;
import in.org.projecteka.hiu.consent.model.ConsentRequestDetails;
import in.org.projecteka.hiu.consent.model.consentmanager.ConsentRepresentation;
import org.jeasy.random.EasyRandom;

public class TestBuilders {

    private static final EasyRandom easyRandom = new EasyRandom();

    public static ConsentCreationResponse.ConsentCreationResponseBuilder consentCreationResponse() {
        return easyRandom.nextObject(ConsentCreationResponse.ConsentCreationResponseBuilder.class);
    }

    public static ConsentRequestDetails.ConsentRequestDetailsBuilder consentRequestDetails() {
        return easyRandom.nextObject(ConsentRequestDetails.ConsentRequestDetailsBuilder.class);
    }

    public static ConsentRepresentation.ConsentRepresentationBuilder consentRepresentation() {
        return easyRandom.nextObject(ConsentRepresentation.ConsentRepresentationBuilder.class);
    }
}
