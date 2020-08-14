package in.org.projecteka.hiu.user;

import in.org.projecteka.hiu.clients.AccountServiceProperties;
import lombok.AllArgsConstructor;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;

@AllArgsConstructor
public class IdentityService {
    private final HASGatewayClient hasGatewayClient;
    private final AccountServiceProperties accountServiceProperties;

    public Mono<String> authenticateForHASGateway() {
        return hasGatewayClient.
                getToken(accountServiceProperties.getHasAuthUrl(), formData())
                .map(Session::getAccessToken);
    }

    private MultiValueMap<String, String> formData() {
        var formData = new LinkedMultiValueMap<String, String>();
        formData.add("grant_type", "client_credentials");
        formData.add("scope", "openid");
        formData.add("client_id", accountServiceProperties.getClientId());
        formData.add("client_secret", accountServiceProperties.getClientSecret());
        return formData;
    }
}