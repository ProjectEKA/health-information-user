package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.consent.ConsentRepository;
import in.org.projecteka.hiu.consent.PatientConsentRepository;
import in.org.projecteka.hiu.consent.model.ConsentStatus;
import in.org.projecteka.hiu.dataflow.model.DataEntry;
import in.org.projecteka.hiu.dataflow.model.DataPartDetail;
import in.org.projecteka.hiu.dataflow.model.PatientDataEntry;
import in.org.projecteka.hiu.dataflow.model.PatientHealthInfoStatus;
import in.org.projecteka.hiu.dataflow.model.DataRequestStatus;
import in.org.projecteka.hiu.dataflow.model.HealthInfoStatus;
import in.org.projecteka.hiu.dataflow.model.PatientDataRequestMapping;
import in.org.projecteka.hiu.dataflow.model.PatientDataRequestDetail;
import in.org.projecteka.hiu.dataprocessor.model.EntryStatus;

import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.time.LocalDateTime;
import java.util.*;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.stream.Collectors;

import static in.org.projecteka.hiu.ClientError.consentArtefactGone;
import static in.org.projecteka.hiu.ClientError.invalidHealthInformationRequest;
import static in.org.projecteka.hiu.ClientError.unauthorizedRequester;

@AllArgsConstructor
public class HealthInfoManager {
    private static final Logger logger = LoggerFactory.getLogger(HealthInfoManager.class);
    private final ConsentRepository consentRepository;
    private final DataFlowRepository dataFlowRepository;
    private final PatientConsentRepository patientConsentRepository;
    private final HealthInformationRepository healthInformationRepository;
    private final DataFlowServiceProperties serviceProperties;

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

    public Mono<Tuple2<List<PatientDataEntry>, Integer>> fetchHealthInformation(List<String> dataRequestIds, String requesterId,
                                                                                int limit, int offset) {
        return patientConsentRepository.fetchConsentRequestIds(dataRequestIds)
                .map(PatientDataRequestMapping::getConsentRequestId)
                .collectList()
                .flatMapMany(dataFlowRepository::fetchDataPartDetails)
                .collectList()
                .filter(dataParts -> isValidRequester(dataParts, requesterId))
                .switchIfEmpty(Mono.error(unauthorizedRequester()))
                .flatMap(dataParts -> getDataEntries(limit, offset, dataParts));
    }

    private boolean isValidRequester(List<DataPartDetail> dataParts, String requesterId) {
        return dataParts.stream().allMatch(dataPart -> dataPart.getRequester().equals(requesterId));
    }

    private Mono<Tuple2<List<PatientDataEntry>, Integer>> getDataEntries(int limit, int offset, List<DataPartDetail> dataParts) {
        HashMap<String, PatientDataEntry.PatientDataEntryBuilder> dataEntries = new HashMap<>();
        dataParts.forEach(dataPartDetail -> {
            dataEntries.put(dataPartDetail.getTransactionId(), PatientDataEntry.builder()
                    .consentRequestId(dataPartDetail.getConsentRequestId())
                    .hipId(dataPartDetail.getHipId())
                    .consentArtefactId(dataPartDetail.getConsentArtifactId()));
        });
        var transactionIds = List.copyOf(dataEntries.keySet());
        return healthInformationRepository.getHealthInformation(transactionIds, limit, offset)
                .map(healthInfo -> dataEntries.get(healthInfo.get("transaction_id").toString())
                        .status(toStatus((String) healthInfo.get("status")))
                        .data(healthInfo.get("data")).build())
                .collectList()
                .zipWith(healthInformationRepository.getTotalCountOfEntries(transactionIds));
    }

    public String getTransactionIdForConsentRequest(String consentRequestId) {
        return consentRepository.getConsentArtefactId(consentRequestId)
                .flatMap(dataFlowRepository::getTransactionId).block();
    }

    public Flux<PatientHealthInfoStatus> fetchHealthInformationStatus(List<String> dataRequestIds) {
        var dataReqUUIDs = dataRequestIds.stream().filter(this::isUUID).collect(Collectors.toSet());
        return dataFlowRepository.fetchPatientDataRequestDetails(dataReqUUIDs)
                .collectList()
                .flatMapMany(patientDataRequestDetails -> {
                    var detailsByDataReqId = patientDataRequestDetails.stream()
                            .collect(Collectors.groupingBy(PatientDataRequestDetail::getDataRequestId));

                    var patientHealthInfoStatuses = new ArrayList<PatientHealthInfoStatus>();
                    detailsByDataReqId.forEach((dataReqId, dataRequestDetails) -> {
                        var dataRequestDetail = dataRequestDetails.get(0);
                        var statusBuilder = PatientHealthInfoStatus.builder()
                                .hipId(dataRequestDetail.getHipId())
                                .requestId(dataRequestDetail.getDataRequestId());

                        if (StringUtils.isEmpty(dataRequestDetail.getConsentRequestId())) {
                            patientHealthInfoStatuses.add(statusBuilder
                                    .status(getStatusAgainstDate(
                                            dataRequestDetail.getPatientDataRequestedAt(),
                                            serviceProperties.getConsentRequestWaitTime()))
                                    .build());
                            return;
                        }

                        if (StringUtils.isEmpty(dataRequestDetail.getConsentArtefactId())) {
                            patientHealthInfoStatuses.add(statusBuilder
                                    .status(getStatusAgainstDate(
                                            dataRequestDetail.getConsentRequestedAt(),
                                            serviceProperties.getConsentArtefactWaitTime()))
                                    .build());
                            return;
                        }

                        if (Objects.isNull(dataRequestDetail.getDataPartStatus())) {
                            patientHealthInfoStatuses.add(statusBuilder
                                    .status(getStatusAgainstDate(
                                            dataRequestDetail.getDataFlowRequestedAt(),
                                            serviceProperties.getDataPartWaitTime()))
                                    .build());
                            return;
                        }

                        patientHealthInfoStatuses.add(statusBuilder.status(getStatusFor(dataRequestDetails)).build());
                    });

                    return Flux.fromIterable(patientHealthInfoStatuses);
                });
    }

    //TODO: If someone knows a better way to do it please update this.
    private boolean isUUID(String maybeUUID){
        try {
            UUID.fromString(maybeUUID);
            return true;
        }catch(Exception e) {
            return false;
        }
    }

    private DataRequestStatus getStatusAgainstDate(LocalDateTime dateTime, Integer withinMinutes) {
        return LocalDateTime.now(ZoneOffset.UTC).isAfter(dateTime.plusMinutes(withinMinutes))
                ? DataRequestStatus.ERRORED
                : DataRequestStatus.PROCESSING;
    }

    private DataRequestStatus getStatusFor(List<PatientDataRequestDetail> dataRequestDetails) {
        var statuses = dataRequestDetails.stream().map(PatientDataRequestDetail::getDataPartStatus)
                .collect(Collectors.toList());
        if (isProcessing(statuses)) {
            return DataRequestStatus.PROCESSING;
        }
        if(isErrored(statuses)){
            return DataRequestStatus.ERRORED;
        }
        if (isPartial(statuses)) {
            return DataRequestStatus.PARTIAL;
        }
        return DataRequestStatus.SUCCEEDED;
    }

    private boolean isPartial(List<HealthInfoStatus> statuses) {
        return statuses.stream().anyMatch(status -> status.equals(HealthInfoStatus.PARTIAL));
    }

    private boolean isErrored(List<HealthInfoStatus> statuses) {
        return statuses.stream().allMatch(status -> status.equals(HealthInfoStatus.ERRORED));
    }

    private boolean isProcessing(List<HealthInfoStatus> statuses) {
        return statuses.stream().anyMatch(status -> status.equals(HealthInfoStatus.PROCESSING) || status.equals(HealthInfoStatus.RECEIVED));
    }

    private boolean isConsentNotExpired(Map<String, String> consentDetail) {
        var consentExpiryDate = LocalDateTime.parse(consentDetail.get("consentExpiryDate"));
        return consentExpiryDate.isAfter(LocalDateTime.now(ZoneOffset.UTC));
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
