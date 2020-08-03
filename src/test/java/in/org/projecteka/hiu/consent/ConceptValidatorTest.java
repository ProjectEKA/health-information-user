package in.org.projecteka.hiu.consent;

import com.nimbusds.jose.jwk.JWKSet;
import in.org.projecteka.hiu.DestinationsConfig;
import in.org.projecteka.hiu.consent.model.HIType;
import in.org.projecteka.hiu.dataflow.DataFlowDeleteListener;
import in.org.projecteka.hiu.dataflow.DataFlowRequestListener;
import in.org.projecteka.hiu.dataprocessor.DataAvailabilityListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.test.StepVerifier;

import java.util.Arrays;


@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("dev")
public class ConceptValidatorTest {
    @Autowired
    private ConceptValidator validator;

    @SuppressWarnings("unused")
    @MockBean
    @Qualifier("centralRegistryJWKSet")
    private JWKSet centralRegistryJWKSet;

    @SuppressWarnings("unused")
    @MockBean
    @Qualifier("identityServiceJWKSet")
    private JWKSet identityServiceJWKSet;

    @MockBean
    DestinationsConfig destinationsConfig;

    @MockBean
    DataFlowRequestListener dataFlowRequestListener;

    @MockBean
    DataAvailabilityListener dataAvailabilityListener;

    @MockBean
    DataFlowDeleteListener dataFlowDeleteListener;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void validatePurpose() {
        StepVerifier
                .create(validator.validatePurpose("BTG"))
                .expectNext(true)
                .expectComplete()
                .verify();
        StepVerifier
                .create(validator.validatePurpose("NONEXISTENT-PURPOSE"))
                .expectNext(false)
                .expectComplete()
                .verify();
    }

    @Test
    public void validateHiType() {
        StepVerifier
                .create(validator.validateHITypes(Arrays.asList(HIType.OP_CONSULTATION.getValue(), "DiagnosticReport")))
                .expectNext(true)
                .expectComplete()
                .verify();
        StepVerifier
                .create(validator.validateHITypes(Arrays.asList("NONEXISTENT-HITYPE")))
                .expectNext(false)
                .expectComplete()
                .verify();

    }

}