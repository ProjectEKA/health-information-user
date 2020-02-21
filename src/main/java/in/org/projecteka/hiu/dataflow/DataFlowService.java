package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.consent.DataFlowRequestPublisher;
import in.org.projecteka.hiu.dataflow.cryptohelper.CryptoHelper;
import in.org.projecteka.hiu.dataflow.model.DataNotificationRequest;
import lombok.AllArgsConstructor;
import org.apache.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import reactor.core.publisher.Mono;

import java.security.Security;

@AllArgsConstructor
public class DataFlowService {
    private DataFlowRepository dataFlowRepository;
    private static final Logger logger = Logger.getLogger(DataFlowRequestPublisher.class);

    public Mono<Void> handleNotification(DataNotificationRequest dataNotificationRequest) throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        var privateKey = "AOvbHEsn8vtNW7hFfotS5NnbWlfkwxX2rA==";
        var randomKey = "pk5xT1Xk+KUlf/LC1LZawKECPNvOvIzhZNEyIdh7oJE=";

        var senderPublicKey = dataNotificationRequest.getKeyMaterial().getDhPublicKey().getKeyValue();
        var randomKeySender = dataNotificationRequest.getKeyMaterial().getRandomKey().getKeyValue();
        String data;

        try{
            data = CryptoHelper.decrypt(privateKey,senderPublicKey,randomKeySender, randomKey,
                    dataNotificationRequest.getEntries().get(0).getContent());
        } catch (Exception e){
           logger.error("Error while decrypting {exception}", e);
        }

        return dataFlowRepository.addDataResponse(dataNotificationRequest.getTransactionId(),
                dataNotificationRequest.getEntries());
    }
}
