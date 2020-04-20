package in.org.projecteka.hiu.consent;

import com.nimbusds.jose.jwk.JWKSet;
import in.org.projecteka.hiu.consent.model.HIType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
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
    private JWKSet centralRegistryJWKSet;


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
                .create(validator.validateHITypes(Arrays.asList(HIType.CONDITION.getValue(), "DiagnosticReport", "Observation", "MedicationRequest")))
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