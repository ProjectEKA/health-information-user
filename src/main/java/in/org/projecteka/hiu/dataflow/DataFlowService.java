package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.common.cache.CacheAdapter;
import in.org.projecteka.hiu.consent.TokenUtils;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequestKeyMaterial;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequestResult;
import in.org.projecteka.hiu.dataflow.model.DataNotificationRequest;
import in.org.projecteka.hiu.dataflow.model.Entry;
import in.org.projecteka.hiu.dataflow.model.HealthInfoStatus;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static reactor.core.publisher.Mono.defer;

@AllArgsConstructor
public class DataFlowService {
    public static final String TRANSACTION_ID = "transactionId";
    public static final String PATH_TO_FILE = "pathToFile";
    private static final String DATA_PART_NUMBER = "partNumber";
    private final DataFlowRepository dataFlowRepository;
    private final DataAvailabilityPublisher dataAvailabilityPublisher;
    private final DataFlowServiceProperties dataFlowServiceProperties;
    private final LocalDataStore localDataStore;
    private final CacheAdapter<String, DataFlowRequestKeyMaterial> dataFlowCache;

    private static final Logger logger = LoggerFactory.getLogger(DataFlowService.class);

    public Mono<Void> handleNotification(DataNotificationRequest dataNotificationRequest) {
        logger.info("[DataFlowService] Received data transfer for transactionId={}", dataNotificationRequest.getTransactionId());
        List<Entry> invalidEntries = dataNotificationRequest.getEntries().parallelStream().filter(entry ->
                !(hasLink(entry) || hasContent(entry))).collect(Collectors.toList());

        if (!invalidEntries.isEmpty()) {
            return Mono.error(ClientError.invalidEntryError("Entry must either have content or provide a link."));
        }

        int dataFlowPartNo = 1;
        return validateAndRetrieveRequestedConsent(dataNotificationRequest.getTransactionId())
                .flatMap(consentRequestId -> serializeDataTransferred(dataNotificationRequest, consentRequestId,
                        dataFlowPartNo))
                .flatMap(contentReference -> saveDataAvailability(contentReference, dataFlowPartNo))
                .flatMap(this::notifyDataProcessor);
    }

    private Mono<Map<String, String>> saveDataAvailability(Map<String, String> contentReference, int partNumber) {
        contentReference.put(DATA_PART_NUMBER, String.valueOf(partNumber));
        return dataFlowRepository.insertDataPartAvailability(contentReference.get(TRANSACTION_ID),
                partNumber,
                HealthInfoStatus.RECEIVED)
                .thenReturn(contentReference);
    }

    private Mono<Void> notifyDataProcessor(Map<String, String> contentRef) {
        return dataAvailabilityPublisher.broadcastDataAvailability(contentRef);
    }

    public Mono<Void> updateDataFlowRequest(DataFlowRequestResult dataFlowRequestResult) {
        String requestId = dataFlowRequestResult.getResp().getRequestId();
        if (dataFlowRequestResult.getError() != null) {
            logger.error("[DataFlowService] Received error response for data flow request. HIU " +
                            "requestId={}, error_code= {}, message= {}",
                    requestId,
                    dataFlowRequestResult.getError().getCode(),
                    dataFlowRequestResult.getError().getMessage());
            return Mono.empty();
        }
        if (dataFlowRequestResult.getHiRequest() == null) {
            logger.error("[DataFlowService] Received null response for data flow request. HIU " +
                    "requestId={}", requestId);
            return Mono.empty();
        }
        var transactionId = dataFlowRequestResult.getHiRequest().getTransactionId().toString();
        var sessionStatus = dataFlowRequestResult.getHiRequest().getSessionStatus();

        logger.info("[DataFlowService] Received response for data flow request. HIU " +
                "transactionId={}, sessionStatus={}, requestId={}", transactionId, sessionStatus, requestId);
        return dataFlowRepository.updateDataRequest(transactionId, sessionStatus, requestId)
                .then(defer(() -> dataFlowCache.get(requestId)))
                .flatMap(dataFlowRequestKeyMaterial ->
                        dataFlowRepository.addKeys(transactionId, dataFlowRequestKeyMaterial));
    }

    private Mono<Map<String, String>> serializeDataTransferred(DataNotificationRequest dataNotificationRequest,
                                                               String consentRequestId, int dataFlowPartNo) {
        Path pathToFile = Paths.get(dataFlowServiceProperties.getLocalStoragePath(),
                getLocalDirectoryName(consentRequestId),
                getLocalDirectoryName(dataNotificationRequest.getTransactionId()),
                localFileNameToSave(dataNotificationRequest.getTransactionId(), dataFlowPartNo));
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

    private String localFileNameToSave(String transactionId, int dataFlowPartNo) {
        //TODO: potentially append part (e.g. page number)
        return String.format("%s_%d.json", TokenUtils.encode(transactionId), dataFlowPartNo);
    }

    private String getLocalDirectoryName(String consentRequestId) {
        return String.format("%s", TokenUtils.encode(consentRequestId));
    }

    private Mono<String> validateAndRetrieveRequestedConsent(String transactionId) {
        return dataFlowRepository.retrieveDataFlowRequest(transactionId)
                .filter(dataMap -> !hasConsentArtefactExpired((LocalDateTime) dataMap.get("consentExpiryDate")))
                .switchIfEmpty(Mono.error(ClientError.consentArtefactGone()))
                .map(dataMap -> (String) dataMap.get("consentRequestId"))
                .doOnError(throwable -> logger.error(throwable.getMessage(), throwable));
    }

    private boolean hasConsentArtefactExpired(LocalDateTime dataEraseAt) {
        return dataEraseAt != null && dataEraseAt.isBefore(LocalDateTime.now());
    }

    private boolean hasContent(Entry entry) {
        return (entry.getContent() != null) && !entry.getContent().isBlank();
    }

    private boolean hasLink(Entry entry) {
        return (entry.getLink() != null) && !entry.getLink().isBlank();
    }
}
