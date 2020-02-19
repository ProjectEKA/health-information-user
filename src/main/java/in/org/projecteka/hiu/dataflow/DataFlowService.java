package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.dataflow.model.DataNotificationRequest;
import in.org.projecteka.hiu.dataflow.model.Entry;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@AllArgsConstructor
public class DataFlowService {
    private DataFlowRepository dataFlowRepository;

    public Mono<Void> handleNotification(DataNotificationRequest dataNotificationRequest) {
        return Flux.fromIterable(dataNotificationRequest.getEntries())
                .filter(entry -> entry.getLink() == null)
                .flatMap(entry -> insertHealthInformation(entry, dataNotificationRequest.getTransactionId()))
                .then();
    }

    private Mono<Void> insertHealthInformation(Entry entry, String transactionId) {
        return dataFlowRepository.insertHealthInformation(transactionId, entry);
    }
}
