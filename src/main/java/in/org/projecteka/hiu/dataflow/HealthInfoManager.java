package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.consent.ConsentRepository;
import in.org.projecteka.hiu.dataflow.model.DataEntry;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Flux;

@AllArgsConstructor
public class HealthInfoManager {
    private ConsentRepository consentRepository;
    private DataFlowRepository dataFlowRepository;
    private HealthInformationRepository healthInformationRepository;

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
                .map(data -> DataEntry.builder()
                        .hipId(hipId)
                        .hipName(hipName)
                        //.status(data != null ? Status.COMPLETED : Status.REQUESTED)
                        .data(data)
                        .build());
    }
}
