package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.consent.model.ConsentCreationResponse;
import in.org.projecteka.hiu.consent.model.ConsentRequestData;
import in.org.projecteka.hiu.consent.model.consentmanager.ConsentRequest;
import org.jeasy.random.EasyRandom;

public class TestBuilders {

    private static final EasyRandom easyRandom = new EasyRandom();

    public static ConsentCreationResponse.ConsentCreationResponseBuilder consentCreationResponse() {
        return easyRandom.nextObject(ConsentCreationResponse.ConsentCreationResponseBuilder.class);
    }

    public static ConsentRequestData.ConsentRequestDataBuilder consentRequestDetails() {
        return easyRandom.nextObject(ConsentRequestData.ConsentRequestDataBuilder.class);
    }

    public static ConsentRequest.ConsentRequestBuilder consentRepresentation() {
        return easyRandom.nextObject(ConsentRequest.ConsentRequestBuilder.class);
    }
}
