package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.consent.ConsentRepository;
import in.org.projecteka.hiu.consent.PatientConsentRepository;
import in.org.projecteka.hiu.consent.model.ConsentStatus;
import in.org.projecteka.hiu.dataflow.model.DataEntry;
import in.org.projecteka.hiu.dataflow.model.DataPartDetail;
import in.org.projecteka.hiu.dataflow.model.DataRequestStatus;
import in.org.projecteka.hiu.dataflow.model.HealthInfoStatus;
import in.org.projecteka.hiu.dataflow.model.PatientDataEntry;
import in.org.projecteka.hiu.dataflow.model.PatientDataRequestDetail;
import in.org.projecteka.hiu.dataflow.model.PatientDataRequestMapping;
import in.org.projecteka.hiu.dataflow.model.PatientHealthInfoStatus;
import in.org.projecteka.hiu.dataprocessor.model.EntryStatus;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static in.org.projecteka.hiu.ClientError.consentArtefactGone;
import static in.org.projecteka.hiu.ClientError.invalidHealthInformationRequest;
import static in.org.projecteka.hiu.ClientError.unauthorizedRequester;
import static in.org.projecteka.hiu.common.Constants.STATUS;
import static in.org.projecteka.hiu.dataflow.model.DataRequestStatus.ERRORED;
import static in.org.projecteka.hiu.dataflow.model.DataRequestStatus.PROCESSING;
import static in.org.projecteka.hiu.dataflow.model.HealthInfoStatus.PARTIAL;
import static in.org.projecteka.hiu.dataflow.model.HealthInfoStatus.RECEIVED;
import static java.time.LocalDateTime.now;
import static java.time.ZoneOffset.UTC;
import static java.util.UUID.fromString;
import static org.springframework.util.StringUtils.hasText;
import static org.springframework.util.StringUtils.isEmpty;
import static reactor.core.publisher.Flux.fromIterable;
import static reactor.core.publisher.Mono.error;

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
                .switchIfEmpty(error(unauthorizedRequester()))
                .filter(this::isGrantedConsent)
                .switchIfEmpty(error(invalidHealthInformationRequest()))
                .filter(this::isConsentNotExpired)
                .switchIfEmpty(error(consentArtefactGone()))
                .flatMap(consentDetail -> dataFlowRepository.getTransactionId(consentDetail.get("consentId"))
                        .flatMapMany(transactionId -> getDataEntries(
                                transactionId,
                                consentDetail.get("hipId"),
                                consentDetail.get("hipName"))));
    }

    public Mono<Tuple2<List<PatientDataEntry>, Integer>> fetchHealthInformation(List<String> dataRequestIds,
                                                                                String requesterId,
                                                                                int limit,
                                                                                int offset) {
        return patientConsentRepository.fetchConsentRequestIds(dataRequestIds)
                .map(PatientDataRequestMapping::getConsentRequestId)
                .collectList()
                .flatMapMany(dataFlowRepository::fetchDataPartDetails)
                .collectList()
                .filter(dataParts -> isValidRequester(dataParts, requesterId))
                .switchIfEmpty(error(unauthorizedRequester()))
                .flatMap(dataParts -> getDataEntries(limit, offset, dataParts));
    }

    private boolean isValidRequester(List<DataPartDetail> dataParts, String requesterId) {
        return dataParts.stream().allMatch(dataPart -> dataPart.getRequester().equals(requesterId));
    }

    private Mono<Tuple2<List<PatientDataEntry>, Integer>> getDataEntries(int limit,
                                                                         int offset,
                                                                         List<DataPartDetail> dataParts) {
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
                        .status(toStatus((String) healthInfo.get(STATUS)))
                        .data(healthInfo.get("data"))
                        .docId((String) healthInfo.get("doc_id"))
                        .docOriginId((String) healthInfo.get("doc_origin"))
                        .build())
                .collectList()
                .zipWith(healthInformationRepository.getTotalCountOfEntries(transactionIds));
    }

    public Flux<String> getTransactionIdForConsentRequest(String consentRequestId, String username) {
        return consentRepository.getConsentDetails(consentRequestId)
                .filter(consentArtefact -> consentArtefact.get("requester").equals(username))
                .switchIfEmpty(Mono.error(unauthorizedRequester()))
                .flatMap(consentArtefact -> dataFlowRepository.getTransactionId(consentArtefact.get("consentId")));
    }

    public Flux<PatientHealthInfoStatus> fetchHealthInformationStatus(List<String> dataRequestIds, String username) {
        var dataReqUUIDs = dataRequestIds.stream().filter(this::isUUID).collect(Collectors.toSet());
        return dataFlowRepository.fetchPatientDataRequestDetails(dataReqUUIDs)
                .filter(dataRequestDetail -> dataRequestDetail.getPatientId().equals(username))
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

                        PatientHealthInfoStatus healthInfoStatus = statusBuilder
                                .status(getStatusAgainstDate(
                                        dataRequestDetail.getPatientDataRequestedAt(),
                                        serviceProperties.getDataFlowRequestWaitTime()))
                                .build();

                        if (isEmpty(dataRequestDetail.getConsentRequestId())) {
                            logger.info("Consent request is not yet created for data request id {}", dataRequestDetail.getDataRequestId());
                            patientHealthInfoStatuses.add(healthInfoStatus);
                            return;
                        }

                        if (isEmpty(dataRequestDetail.getConsentArtefactId())) {
                            logger.info("Consent artefact is not yet received for data request id {}", dataRequestDetail.getDataRequestId());
                            patientHealthInfoStatuses.add(healthInfoStatus);
                            return;
                        }

                        if(Objects.isNull(dataRequestDetail.getDataFlowRequestedAt())){
                            logger.info("Data flow is not yet requested for data request id {}", dataRequestDetail.getDataRequestId());
                            patientHealthInfoStatuses.add(healthInfoStatus);
                            return;
                        }

                        if (dataRequestDetails.stream().allMatch(this::isStatusNull)) {
                            logger.info("No data parts are yet received for data request id {}",
                                    dataRequestDetail.getDataRequestId());
                            patientHealthInfoStatuses.add(statusBuilder
                                    .status(getStatusAgainstDate(
                                            dataRequestDetail.getDataFlowRequestedAt(),
                                            serviceProperties.getDataPartWaitTime()))
                                    .build());
                            return;
                        }

                        if (dataRequestDetails.stream().anyMatch(this::isStatusNull)) {
                            logger.info("Some data parts are not yet received for data request id {}",
                                    dataRequestDetail.getDataRequestId());
                            var status = now(UTC)
                                    .isAfter(dataRequestDetail.getDataFlowRequestedAt()
                                    .plusMinutes(serviceProperties.getDataPartWaitTime())) ?
                                    getStatusFor(dataRequestDetails) : PROCESSING;
                            patientHealthInfoStatuses.add(statusBuilder
                                    .status(status)
                                    .build());
                            return;
                        }

                        logger.info("Data received  for data request id {}", dataRequestDetail.getDataRequestId());
                        patientHealthInfoStatuses.add(statusBuilder.status(getStatusFor(dataRequestDetails)).build());
                    });

                    return fromIterable(patientHealthInfoStatuses);
                });
    }

    //TODO: If someone knows a better way to do it please update this.
    private boolean isUUID(String maybeUUID) {
        try {
            fromString(maybeUUID);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isStatusNull(PatientDataRequestDetail dataRequestDetail) {
        return Objects.isNull(dataRequestDetail.getDataPartStatus());
    }

    private DataRequestStatus getStatusAgainstDate(LocalDateTime dateTime, Integer withinMinutes) {
        return now(UTC).isAfter(dateTime.plusMinutes(withinMinutes)) ? ERRORED : PROCESSING;
    }

    private DataRequestStatus getStatusFor(List<PatientDataRequestDetail> dataRequestDetails) {
        var statuses = dataRequestDetails.stream()
                .filter(dataRequestDetail -> !Objects.isNull(dataRequestDetail.getDataPartStatus()))
                .map(PatientDataRequestDetail::getDataPartStatus)
                .collect(Collectors.toList());
        if (isProcessing(statuses)) {
            return DataRequestStatus.PROCESSING;
        }
        if (isErrored(statuses)) {
            return DataRequestStatus.ERRORED;
        }
        if (isPartial(statuses)) {
            return DataRequestStatus.PARTIAL;
        }
        return DataRequestStatus.SUCCEEDED;
    }

    private boolean isPartial(List<HealthInfoStatus> statuses) {
        return statuses.stream().anyMatch(status -> status.equals(PARTIAL) || status.equals(HealthInfoStatus.ERRORED));
    }

    private boolean isErrored(List<HealthInfoStatus> statuses) {
        return statuses.stream().allMatch(status -> status.equals(HealthInfoStatus.ERRORED));
    }

    private boolean isProcessing(List<HealthInfoStatus> statuses) {
        return statuses.stream()
                .anyMatch(status -> status.equals(HealthInfoStatus.PROCESSING) || status.equals(RECEIVED));
    }

    private boolean isConsentNotExpired(Map<String, String> consentDetail) {
        var consentExpiryDate = LocalDateTime.parse(consentDetail.get("consentExpiryDate"));
        return consentExpiryDate.isAfter(now(UTC));
    }

    private boolean isGrantedConsent(Map<String, String> consentDetail) {
        return consentDetail.get(STATUS).equals(ConsentStatus.GRANTED.toString());
    }

    private boolean isValidRequester(String requesterId, Map<String, String> consentDetail) {
        return consentDetail.get("requester").equals(requesterId);
    }

    private Flux<DataEntry> getDataEntries(String transactionId, String hipId, String hipName) {
        return healthInformationRepository.getHealthInformation(transactionId)
                .map(healthInfo -> DataEntry.builder()
                        .hipId(hipId)
                        .hipName(hipName)
                        .status(toStatus((String) healthInfo.get(STATUS)))
                        .data(healthInfo.get("data"))
                        .docId((String) healthInfo.get("doc_id"))
                        .docOriginId((String) healthInfo.get("doc_origin"))
                        .build());
    }

    private EntryStatus toStatus(String status) {
        if (hasText(status)) {
            return EntryStatus.valueOf(status);
        }
        return null;
    }
}
