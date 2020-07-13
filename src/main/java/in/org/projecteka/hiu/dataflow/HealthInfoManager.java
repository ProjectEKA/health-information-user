package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.consent.ConsentRepository;
import in.org.projecteka.hiu.consent.model.ConsentStatus;
import in.org.projecteka.hiu.dataflow.model.DataEntry;
import in.org.projecteka.hiu.dataflow.model.DataPartDetail;
import in.org.projecteka.hiu.dataflow.model.HealthInfoStatus;
import in.org.projecteka.hiu.dataflow.model.PatientDataEntry;
import in.org.projecteka.hiu.dataprocessor.model.EntryStatus;

import lombok.AllArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static in.org.projecteka.hiu.ClientError.consentArtefactGone;
import static in.org.projecteka.hiu.ClientError.invalidHealthInformationRequest;
import static in.org.projecteka.hiu.ClientError.unauthorizedRequester;

@AllArgsConstructor
public class HealthInfoManager {
    private final ConsentRepository consentRepository;
    private final DataFlowRepository dataFlowRepository;
    private final HealthInformationRepository healthInformationRepository;

    public Flux<DataEntry> fetchHealthInformation(String consentRequestId, String requesterId) {
        return consentRepository.getConsentDetails(consentRequestId)
                .filter(consentDetail -> isValidRequester(requesterId, consentDetail))
                .switchIfEmpty(Flux.error(unauthorizedRequester()))
                .filter(this::isGrantedConsent)
                .switchIfEmpty(Flux.error(invalidHealthInformationRequest()))
                .filter(this::isConsentNotExpired)
                .switchIfEmpty(Flux.error(consentArtefactGone()))
                .flatMap(consentDetail -> dataFlowRepository.getTransactionId(consentDetail.get("consentId"))
                        .flatMapMany(transactionId -> getDataEntries(
                                transactionId,
                                consentDetail.get("hipId"),
                                consentDetail.get("hipName"))));
    }

    public Flux<PatientDataEntry> fetchHealthInformation(List<String> consentRequestIds, String requesterId,
                                                         int limit, int offset) {
        return dataFlowRepository.fetchDataPartDetails(consentRequestIds)
                .collectList()
                .filter(dataParts -> isValidRequester(dataParts, requesterId))
                .switchIfEmpty(Mono.error(unauthorizedRequester()))
                .flatMapMany(dataParts -> getDataEntries(limit, offset, dataParts));
    }

    private boolean isValidRequester(List<DataPartDetail> dataParts, String requesterId) {
        return dataParts.stream().allMatch(dataPart -> dataPart.getRequester().equals(requesterId));
    }

    private Flux<PatientDataEntry> getDataEntries(int limit, int offset, List<DataPartDetail> dataParts) {
        HashMap<String, List<String>> dataPartStatuses = new HashMap<>();
        HashMap<String, PatientDataEntry.PatientDataEntryBuilder> dataEntries = new HashMap<>();
        dataParts.forEach(dataPart -> {
            var statuses = dataPartStatuses.get(dataPart.getTransactionId());
            dataEntries.put(dataPart.getTransactionId(), PatientDataEntry.builder()
                    .consentRequestId(dataPart.getConsentRequestId())
                    .hipId(dataPart.getHipId())
                    .consentArtefactId(dataPart.getConsentArtifactId()));
            if (statuses == null) {
                dataPartStatuses.put(dataPart.getTransactionId(), List.of(dataPart.getStatus()));
                return;
            }
            statuses.add(dataPart.getStatus());
            dataPartStatuses.put(dataPart.getTransactionId(), statuses);
        });
        List<String> transactionIds = List.copyOf(dataEntries.keySet());
        List<PatientDataEntry> partsInProcess = getDataPartsInProcess(dataPartStatuses, dataEntries);
        return healthInformationRepository.getHealthInformation(transactionIds, limit, offset)
                .map(healthInfo -> dataEntries.get(healthInfo.get("transaction_id").toString())
                        .status(toStatus((String) healthInfo.get("status")))
                        .data(healthInfo.get("data")).build())
                .mergeWith(Flux.fromIterable(partsInProcess));
    }

    private List<PatientDataEntry> getDataPartsInProcess(HashMap<String, List<String>> dataPartStatuses,
                                                         HashMap<String, PatientDataEntry.PatientDataEntryBuilder> dataEntries) {
        List<PatientDataEntry> processingTransactions = new ArrayList<>();
        dataPartStatuses.forEach((transactionId, statuses) -> {
            var isProcessing = statuses.stream().anyMatch(this::isProcessingOrReceived);
            if (isProcessing) {
                processingTransactions.add(dataEntries.get(transactionId).status(EntryStatus.PROCESSING).build());
            }
        });
        return processingTransactions;
    }

    private boolean isProcessingOrReceived(String status) {
        return status.equals(HealthInfoStatus.PROCESSING.toString()) || status.equals(HealthInfoStatus.RECEIVED.toString());
    }

    public String getTransactionIdForConsentRequest(String consentRequestId) {
        return consentRepository.getConsentArtefactId(consentRequestId)
                .flatMap(dataFlowRepository::getTransactionId).block();
    }

    private boolean isConsentNotExpired(Map<String, String> consentDetail) {
        var consentExpiryDate = LocalDateTime.parse(consentDetail.get("consentExpiryDate"));
        return consentExpiryDate.isAfter(LocalDateTime.now());
    }

    private boolean isGrantedConsent(Map<String, String> consentDetail) {
        return consentDetail.get("status").equals(ConsentStatus.GRANTED.toString());
    }

    private boolean isValidRequester(String requesterId, Map<String, String> consentDetail) {
        return consentDetail.get("requester").equals(requesterId);
    }

    private Flux<DataEntry> getDataEntries(String transactionId, String hipId, String hipName) {
        return healthInformationRepository.getHealthInformation(transactionId)
                .map(healthInfo -> DataEntry.builder()
                        .hipId(hipId)
                        .hipName(hipName)
                        .status(toStatus((String) healthInfo.get("status")))
                        .data(healthInfo.get("data"))
                        .build());
    }

    private EntryStatus toStatus(String status) {
        if ((status != null) && !"".equals(status)) {
            return EntryStatus.valueOf(status);
        }
        return null;
    }
}
