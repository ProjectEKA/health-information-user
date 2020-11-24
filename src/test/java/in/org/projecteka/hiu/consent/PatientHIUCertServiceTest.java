package in.org.projecteka.hiu.consent;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import reactor.test.StepVerifier;

import java.security.KeyPair;

import static in.org.projecteka.hiu.consent.TestBuilders.certResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.MockitoAnnotations.initMocks;

class PatientHIUCertServiceTest {

    @MockBean
    private KeyPair keyPair;

    private PatientHIUCertService patientHIUCertService;

    @BeforeEach
    void setUp() throws JOSEException {
        initMocks(this);
        RSAKeyGenerator rsKG = new RSAKeyGenerator(2048);
        keyPair = rsKG.generate().toKeyPair();
        patientHIUCertService = new PatientHIUCertService(keyPair);
    }

    @Test
    void shouldReturnSuccessCertResponse() {
        var certResponse = certResponse().build();
        StepVerifier.create(patientHIUCertService.getCert())
                .assertNext(response -> {
                    assertThat(response.getKeys().contains(certResponse));
                    assertThat(response.getKeys().size()).isEqualTo(1);
                })
                .verifyComplete();
    }

}