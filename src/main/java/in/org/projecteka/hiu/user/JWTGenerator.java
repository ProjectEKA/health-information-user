package in.org.projecteka.hiu.user;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;

@AllArgsConstructor
public class JWTGenerator {

    private final byte[] sharedSecret;

    @SneakyThrows
    public String tokenFrom(User user) {
        JWSSigner signer = new MACSigner(sharedSecret);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim("username", user.getUsername())
                .claim("role", user.getRole())
                .build();
        JWSObject jwsObject = new JWSObject(new JWSHeader(JWSAlgorithm.HS256), new Payload(claims.toJSONObject()));
        jwsObject.sign(signer);
        return jwsObject.serialize();
    }
}
