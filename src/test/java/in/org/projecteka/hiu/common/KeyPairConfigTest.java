package in.org.projecteka.hiu.common;

import com.nimbusds.jose.jwk.JWKSet;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;

import java.security.KeyPair;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest
class KeyPairConfigTest {

    @Autowired
    private KeyPairConfig keyPairConfig;

    @SuppressWarnings("unused")
    @MockBean
    @Qualifier("centralRegistryJWKSet")
    private JWKSet centralRegistryJWKSet;

    @SuppressWarnings("unused")
    @MockBean
    @Qualifier("identityServiceJWKSet")
    private JWKSet identityServiceJWKSet;

    @MockBean
    @Qualifier("userAuthenticator")
    private Authenticator userAuthenticator;

    @Test
    void shouldCreateSignConsentRequestKeyPair() {
        KeyPair signConsentRequestKeyPair = keyPairConfig.createSignConsentRequestKeyPair();
        assertThat(signConsentRequestKeyPair).isNotNull();
        assertThat(signConsentRequestKeyPair.getPublic()).isNotNull();
        assertThat(signConsentRequestKeyPair.getPrivate()).isNotNull();
    }
}