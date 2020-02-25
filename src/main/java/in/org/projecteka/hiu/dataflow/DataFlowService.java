package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.consent.DataFlowRequestPublisher;
import in.org.projecteka.hiu.dataflow.cryptohelper.CryptoHelper;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequestKeyMaterial;
import in.org.projecteka.hiu.dataflow.model.DataNotificationRequest;
import in.org.projecteka.hiu.dataflow.model.Entry;
import in.org.projecteka.hiu.dataflow.model.KeyMaterial;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.apache.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.Console;
import java.security.Security;

@AllArgsConstructor
public class DataFlowService {
    private DataFlowRepository dataFlowRepository;
    private static final Logger logger = Logger.getLogger(DataFlowRequestPublisher.class);

    @SneakyThrows
    public Mono<Void> handleNotification(DataNotificationRequest dataNotificationRequest) throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        var entries = dataNotificationRequest.getEntries();
        return Flux.fromIterable(entries).flatMap(entry -> getDecodedData(dataNotificationRequest, entry)
                .flatMap(data -> {
                    System.out.println(data);
                    return Mono.empty();
                })).then( dataFlowRepository.addDataResponse(dataNotificationRequest.getTransactionId(),
                dataNotificationRequest.getEntries()));
    }

    private Mono<String> getDecodedData(DataNotificationRequest dataNotificationRequest, Entry entry){
        var senderPublicKey = dataNotificationRequest.getKeyMaterial().getDhPublicKey().getKeyValue();
        var randomKeySender = dataNotificationRequest.getKeyMaterial().getRandomKey();
        return dataFlowRepository.getKeys(dataNotificationRequest.getTransactionId())
                .flatMap(keyPairs -> {
                    try {
                        return Mono.just(getEntryData(senderPublicKey, randomKeySender, entry, keyPairs));
                    } catch (Exception e) {
                        logger.error("Error while decrypting {exception}", e);
                        return Mono.error(e);
                    }
                });
    }

    private String getEntryData(String senderPublicKey, String randomKeySender, Entry entry, DataFlowRequestKeyMaterial keyPairs) throws Exception {
        return CryptoHelper.decrypt(keyPairs.getPrivateKey(),
                senderPublicKey,
                randomKeySender,
                keyPairs.getRandomKey(),
                entry.getContent());
    }


}
