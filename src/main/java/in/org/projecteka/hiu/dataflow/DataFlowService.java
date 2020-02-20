package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.dataflow.model.DataEntry;
import in.org.projecteka.hiu.dataflow.model.DataNotificationRequest;
import in.org.projecteka.hiu.dataflow.model.Entry;
import in.org.projecteka.hiu.dataflow.model.Status;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@AllArgsConstructor
public class DataFlowService {
    private DataFlowRepository dataFlowRepository;
    private HealthInformationRepository healthInformationRepository;

    public Mono<Void> handleNotification(DataNotificationRequest dataNotificationRequest) {
        return Flux.fromIterable(dataNotificationRequest.getEntries())
                .filter(entry -> entry.getLink() == null)
                .flatMap(entry -> insertHealthInformation(entry, dataNotificationRequest.getTransactionId()))
                .then();
    }

    private Mono<Void> insertHealthInformation(Entry entry, String transactionId) {
        return dataFlowRepository.insertHealthInformation(transactionId, entry);
    }

    public Flux<DataEntry> fetchHealthInformation(String consentRequestId, String requesterId) {
        return dataFlowRepository.getConsentDetails(consentRequestId)
                .flatMapMany(consentDetails -> fetchDataEntries(consentDetails, requesterId));
    }

    private Flux<DataEntry> fetchDataEntries(List<Map<String, String>> consentDetails, String requesterId) {
        return Flux.fromIterable(consentDetails)
                .flatMap(consentDetail -> {
                    if (consentDetail.get("requester").equals(requesterId))
                        return dataFlowRepository.getTransactionId(consentDetail.get("consentId"))
                                .flatMapMany(transactionId -> getDataEntries(
                                        transactionId,
                                        consentDetail.get("hipId"),
                                        consentDetail.get("hipName")));
                    return Flux.error(ClientError.unauthorized());
                });
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