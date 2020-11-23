package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.Error;
import in.org.projecteka.hiu.ErrorRepresentation;
import in.org.projecteka.hiu.common.Serializer;
import in.org.projecteka.hiu.consent.model.CertDetails;
import in.org.projecteka.hiu.consent.model.CertResponse;
import in.org.projecteka.hiu.consent.model.VerificationConsentRequest;
import in.org.projecteka.hiu.consent.model.consentmanager.Consent;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import reactor.core.publisher.Mono;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;

import static in.org.projecteka.hiu.ErrorCode.UNABLE_TO_PARSE_KEY;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@AllArgsConstructor
public class PatientConsentCertService {

    public static final String SHA_256_WITH_RSA = "SHA256withRSA";
    private final KeyPair keyPair;


    @SneakyThrows
    public String signConsentRequest(Consent consentRequest) {
        var verificationMessage = getVerificationMessage(consentRequest);
        Signature signature = Signature.getInstance(SHA_256_WITH_RSA);
        PrivateKey privateKey = keyPair.getPrivate();
        signature.initSign(privateKey);

        byte[] messageBytes = Serializer.from(verificationMessage).getBytes();
        signature.update(messageBytes);
        byte[] digitalSignature = signature.sign();
        var encodedSign = Base64.getEncoder().encodeToString(digitalSignature);
        return encodedSign;
    }

    private VerificationConsentRequest getVerificationMessage(Consent consentRequest) {
        return VerificationConsentRequest.builder()
                .patientId(consentRequest.getPatient().getId())
                .purposeCode(consentRequest.getPurpose().getCode())
                .hiuId(consentRequest.getHiu().getId())
                .build();
    }

    public Mono<CertResponse> getCert(){
        try {
            CertDetails certDetails = CertDetails.builder()
                    .publicKey(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()))
                    .startDate("2020-01-01:00:00:00Z")
                    .build();
            var keys = new ArrayList<CertDetails>();
            keys.add(certDetails);
            return Mono.just(CertResponse.builder().keys(keys).build());
        }catch (Exception e){
            return Mono.error(new ClientError(INTERNAL_SERVER_ERROR,
                    new ErrorRepresentation(new Error(UNABLE_TO_PARSE_KEY, "Unable to parse public key"))));
        }
    }
}
