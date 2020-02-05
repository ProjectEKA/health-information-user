package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.consent.model.ConsentCreationResponse;
import in.org.projecteka.hiu.consent.model.ConsentRequestData;
import in.org.projecteka.hiu.consent.model.consentmanager.ConsentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.test.StepVerifier;

import static in.org.projecteka.hiu.consent.TestBuilders.consentCreationResponse;
import static in.org.projecteka.hiu.consent.TestBuilders.consentRequestDetails;
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
        ConsentRequestData consentRequestData = consentRequestDetails().build();
        ConsentCreationResponse consentCreationResponse = consentCreationResponse().build();
        ConsentRequest consentRequest = new ConsentRequest(consentRequestData.getConsent()
                .to("1","hiuId","hiuName", "localhost:8080"));

        when(hiuProperties.getId()).thenReturn("hiuId");
        when(hiuProperties.getName()).thenReturn("hiuName");
        when(hiuProperties.getCallBackUrl()).thenReturn("localhost:8080");
        when(consentManagerClient.createConsentRequest(consentRequest))
                .thenReturn(Mono.just(consentCreationResponse));
        when(consentRepository.insert(consentRequestData.getConsent().toConsentRequest(
                consentCreationResponse.getId(),
                "requesterId", "localhost:8080")))
                .thenReturn(Mono.create(MonoSink::success));

        StepVerifier.create(consentService.create("1", consentRequestData))
                .expectNext(consentCreationResponse).expectComplete();
        verify(consentManagerClient).createConsentRequest(consentRequest);
    }
}
