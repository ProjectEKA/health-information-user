package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.consent.ConsentRepository;
import in.org.projecteka.hiu.consent.DataFlowRequestPublisher;
import in.org.projecteka.hiu.dataflow.cryptohelper.CryptoHelper;
import in.org.projecteka.hiu.dataflow.model.Entry;
import in.org.projecteka.hiu.dataflow.model.DataNotificationRequest;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequestKeyMaterial;
import in.org.projecteka.hiu.dataflow.model.DataEntry;
import in.org.projecteka.hiu.dataflow.model.Status;
import lombok.AllArgsConstructor;
import org.apache.log4j.Logger;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@AllArgsConstructor
public class DataFlowService {
    private DataFlowRepository dataFlowRepository;
    private static final Logger logger = Logger.getLogger(DataFlowRequestPublisher.class);
    private HealthInformationRepository healthInformationRepository;
    private ConsentRepository consentRepository;
    private CryptoHelper cryptoHelper;

    private Mono<Entry> getDecodedData(DataFlowRequestKeyMaterial savedKeyMaterial,
                                       DataNotificationRequest dataNotificationRequest,
                                       Entry entry){
        try {
            var entryString = getEntryData(dataNotificationRequest.getKeyMaterial(), entry, savedKeyMaterial);
            return Mono.just(Entry.builder()
                    .checksum(entry.getChecksum())
                    .content(entryString)
                    .link(entry.getLink())
                    .media(entry.getMedia())
                    .build());
        } catch (Exception e) {
            logger.error("Error while decrypting {exception}", e);
            return Mono.error(e);
        }

    }

    private String getEntryData(KeyMaterial receivedKeyMaterial,
                                Entry entry,
                                DataFlowRequestKeyMaterial keyPairs) throws Exception {
        return cryptoHelper.decrypt(receivedKeyMaterial, keyPairs, entry.getContent());
    }

    public Mono<Void> handleNotification(DataNotificationRequest dataNotificationRequest) {
        return dataFlowRepository.getKeys(dataNotificationRequest.getTransactionId())
                .flatMap(keyPair -> Flux.fromIterable(dataNotificationRequest.getEntries())
                                        .filter(entry -> isComponent(entry))
                                        .flatMap(entry -> getDecodedData(keyPair, dataNotificationRequest, entry))
                                        .flatMap(entry -> insertHealthInformation(entry,
                                                dataNotificationRequest.getTransactionId()))
                                        .then());
    }

    private boolean isComponent(Entry entry){
        return entry.getLink() == null;
    }

    private Mono<Void> insertHealthInformation(Entry entry, String transactionId) {
        return dataFlowRepository.insertHealthInformation(transactionId, entry);
    }

    public Flux<DataEntry> fetchHealthInformation(String consentRequestId, String requesterId) {
        return consentRepository.getConsentDetails(consentRequestId)
                .filter(consentDetail -> consentDetail.get("requester").equals(requesterId))
                .flatMap(consentDetail ->
                        dataFlowRepository.getTransactionId(consentDetail.get("consentId"))
                                .flatMapMany(transactionId -> getDataEntries(
                                        transactionId,
                                        consentDetail.get("hipId"),
                                        consentDetail.get("hipName")))
                ).switchIfEmpty(Flux.error(ClientError.unauthorizedRequester()));
    }

    private Flux<DataEntry> getDataEntries(String transactionId, String hipId, String hipName) {
        return healthInformationRepository.getHealthInformation(transactionId)
                .map(entry -> DataEntry.builder()
                        .hipId(hipId)
                        .hipName(hipName)
                        .status(entry != null ? Status.COMPLETED : Status.REQUESTED)
                        .entry(entry)
                        .build());
    }
}

