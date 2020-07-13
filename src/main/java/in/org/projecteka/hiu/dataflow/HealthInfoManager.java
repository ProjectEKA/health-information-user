package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.consent.ConsentRepository;
import in.org.projecteka.hiu.consent.model.ConsentStatus;
import in.org.projecteka.hiu.consent.model.Patient;
import in.org.projecteka.hiu.dataflow.model.DataEntry;
import in.org.projecteka.hiu.dataflow.model.HealthInfoStatus;
import in.org.projecteka.hiu.dataflow.model.PatientDataEntry;
import in.org.projecteka.hiu.dataprocessor.model.EntryStatus;
import io.vavr.Tuple;
import io.vertx.sqlclient.Row;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.naming.ldap.HasControls;
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

    public Flux<PatientDataEntry> fetchHealthInformation(List<String> consentRequestIds, String requesterId, int limit, int offset) {
        return dataFlowRepository.fetchDataPartDetails(consentRequestIds)
                .collectList()
                .filter(dataParts -> dataParts.stream().allMatch(dataPart -> dataPart.getString("requester").equals(requesterId)))
                .switchIfEmpty(Mono.error(unauthorizedRequester()))
                .flatMapMany(dataParts -> {
                    HashMap<String, List<String>> dataPartStatuses = new HashMap<>();
                    HashMap<String, PatientDataEntry.PatientDataEntryBuilder> dataEntries = new HashMap<>();
                    dataParts.forEach(dataPart -> {
                        var transactionId = dataPart.getString("transaction_id");
                        var dataPartStatus = dataPart.getString("status");
                        var statuses = dataPartStatuses.get(transactionId);
                        dataEntries.put(transactionId, PatientDataEntry.builder()
                                .consentRequestId(dataPart.getString("consent_request_id"))
                                .hipId(dataPart.getString("hipid"))
                                .consentArtefactId(dataPart.getString("consent_artefact_id")));
                        if (statuses == null) {
                            dataPartStatuses.put(transactionId, List.of(dataPartStatus));
                            return;
                        }
                        statuses.add(dataPartStatus);
                        dataPartStatuses.put(transactionId, statuses);
                    });
                    List<String> transactionIds = new ArrayList<>();
                    List<PatientDataEntry> processingTransactions = new ArrayList<>();
                    dataPartStatuses.forEach((transactionId, statuses) -> {
                        var isProcessing = statuses.stream().anyMatch(status -> status.equals(HealthInfoStatus.PROCESSING.toString()) || status.equals(HealthInfoStatus.RECEIVED.toString()));
                        if (isProcessing) {
                            processingTransactions.add(dataEntries.get(transactionId).status(EntryStatus.PROCESSING).build());
                            return;
                        }
                        transactionIds.add(transactionId);
                    });
                    return healthInformationRepository.getHealthInformation(transactionIds, limit, offset)
                            .map(healthInfo -> dataEntries.get(healthInfo.get("transaction_id").toString())
                                    .status(toStatus((String) healthInfo.get("status")))
                                    .data(healthInfo.get("data")).build())
                            .mergeWith(Flux.fromIterable(processingTransactions));
                });
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
