package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.dataflow.model.DataNotificationRequest;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Mono;

@AllArgsConstructor
public class DataFlowService {
    private DataFlowRepository dataFlowRepository;

    public Mono<Void> handleNotification(DataNotificationRequest dataNotificationRequest) {
        return dataFlowRepository.addDataResponse(dataNotificationRequest.getTransactionId(),
                dataNotificationRequest.getEntries());
    }
}
