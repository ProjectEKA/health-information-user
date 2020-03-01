package in.org.projecteka.hiu.dataflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.consent.ConsentRepository;
import in.org.projecteka.hiu.dataflow.model.DataEntry;
import in.org.projecteka.hiu.dataflow.model.DataNotificationRequest;
import in.org.projecteka.hiu.dataflow.model.Entry;
import in.org.projecteka.hiu.dataflow.model.Status;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@AllArgsConstructor
public class DataFlowService {
    public static final String TRANSACTION_ID = "transactionId";
    public static final String PATH_TO_FILE = "pathToFile";
    private static final String DATA_PART_NUMBER = "partNumber";
    private DataFlowRepository dataFlowRepository;
    private HealthInformationRepository healthInformationRepository;
    private ConsentRepository consentRepository;
    private DataAvailabilityPublisher dataAvailabilityPublisher;
    private DataFlowServiceProperties dataFlowServiceProperties;
    private LocalDataStore localDataStore;

    public Mono<Void> handleNotification(DataNotificationRequest dataNotificationRequest, String senderId) {
        List<Entry> invalidEntries = dataNotificationRequest.getEntries().parallelStream().filter(entry ->
                !(hasLink(entry) || hasContent(entry))).collect(Collectors.toList());

        if (invalidEntries != null && !invalidEntries.isEmpty()) {
            return Mono.error(ClientError.invalidEntryError("Entry must either have content or provide a link."));
        }
        return validateDataFlowTransaction(dataNotificationRequest.getTransactionId(), senderId)
                .then(serializeDataTransferred(dataNotificationRequest))
                .flatMap(contentReference -> saveDataAvailability(contentReference, 1))
                .flatMap(contentReference -> notifyDataProcessor(contentReference));
    }

    private Mono<Map<String, String>> saveDataAvailability(Map<String, String> contentReference, int partNumber) {
        contentReference.put(DATA_PART_NUMBER, String.valueOf(partNumber));
        return dataFlowRepository.insertDataPartAvailability(contentReference.get(TRANSACTION_ID), partNumber)
                .flatMap(r -> Mono.just(contentReference));
    }

    private Mono<Void> notifyDataProcessor(Map<String, String> contentRef) {
        return dataAvailabilityPublisher.broadcastDataAvailability(contentRef);
    }

    private Mono<Map<String, String>> serializeDataTransferred(DataNotificationRequest dataNotificationRequest) {
        Path pathToFile = Paths.get(dataFlowServiceProperties.getLocalStoragePath(),
                localFileNameToSave(dataNotificationRequest));

        return localDataStore.serializeDataToFile(dataNotificationRequest, pathToFile)
                .thenReturn(createContentAvailabilityRef(dataNotificationRequest, pathToFile));
    }

    private Map<String, String> createContentAvailabilityRef(DataNotificationRequest dataNotificationRequest,
                                                             Path pathToFile) {
        Map<String, String> contentRef = new HashMap<>();
        contentRef.put(TRANSACTION_ID, dataNotificationRequest.getTransactionId());
        contentRef.put(PATH_TO_FILE, pathToFile.toString());
        return contentRef;
    }

    private String localFileNameToSave(DataNotificationRequest dataNotificationRequest) {
        //TODO: potentially append part (e.g. page number)
        return String.format("%s.json", dataNotificationRequest.getTransactionId());
    }

    @SneakyThrows
    private byte[] contentFromRequest(DataNotificationRequest dataNotificationRequest) {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsBytes(dataNotificationRequest);
    }

    private Mono<Boolean> validateDataFlowTransaction(String transactionId, String senderId) {
        return dataFlowRepository.retrieveDataFlowRequest(transactionId).flatMap(
                dataRequest -> {
                    //TODO: possibly validate the senderId
                    return dataRequest != null ? Mono.just(true) : Mono.just(false);
                }
        );
    }

    private boolean hasContent(Entry entry) {
        return (entry.getContent() != null) && !entry.getContent().isBlank();
    }

    private boolean hasLink(Entry entry) {
        return (entry.getLink() != null) && !entry.getLink().getHref().isBlank();
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