package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.consent.model.ConsentCreationResponse;
import in.org.projecteka.hiu.consent.model.ConsentRequestDetails;
import in.org.projecteka.hiu.consent.model.consentmanager.ConsentRepresentation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.test.StepVerifier;

import static in.org.projecteka.hiu.consent.TestBuilders.consentCreationResponse;
import static in.org.projecteka.hiu.consent.TestBuilders.consentRequestDetails;
import static in.org.projecteka.hiu.consent.Transformer.toConsentRequest;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class ConsentServiceTest {
    @Mock
    private ConsentManagerClient consentManagerClient;

    @Mock
    private ConsentRepository consentRepository;

    @Mock
    private HiuProperties hiuProperties;

    @BeforeEach
    public void setUp() {
        initMocks(this);
    }


    @Test
    public void shouldCreateConsentRequest() {
        ConsentService consentService = new ConsentService(consentManagerClient, hiuProperties, consentRepository);
        ConsentRequestDetails consentRequestDetails = consentRequestDetails().build();
        ConsentCreationResponse consentCreationResponse = consentCreationResponse().build();
        ConsentRepresentation consentRepresentation = new ConsentRepresentation(consentRequestDetails.getConsent()
                .to("1","hiuId","hiuName"));

        when(hiuProperties.getId()).thenReturn("hiuId");
        when(hiuProperties.getName()).thenReturn("hiuName");
        when(consentManagerClient.createConsentRequestInConsentManager(consentRepresentation))
                .thenReturn(Mono.just(consentCreationResponse));
        when(consentRepository.insert(toConsentRequest(
                consentCreationResponse.getId(),
                "requesterId",
                consentRequestDetails.getConsent())))
                .thenReturn(Mono.create(MonoSink::success));

        StepVerifier.create(consentService.create("1", consentRequestDetails))
                .expectNext(consentCreationResponse).expectComplete();
        verify(consentManagerClient).createConsentRequestInConsentManager(consentRepresentation);
    }
}
