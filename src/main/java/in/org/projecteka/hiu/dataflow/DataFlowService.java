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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@AllArgsConstructor
public class DataFlowService {
    private DataFlowRepository dataFlowRepository;
    private HealthInformationRepository healthInformationRepository;
    private ConsentRepository consentRepository;
    private DataAvailabilityPublisher dataAvailabilityPublisher;
    private DataFlowServiceProperties dataFlowServiceProperties;

    public Mono<Void> handleNotification(DataNotificationRequest dataNotificationRequest, String requesterId) {
        List<Entry> invalidEntries = dataNotificationRequest.getEntries().parallelStream().filter(entry -> {
            return !(hasLink(entry) || hasContent(entry));
        }).collect(Collectors.toList());

        if (invalidEntries != null && !invalidEntries.isEmpty()) {
            return Mono.error(ClientError.invalidEntryError("Entry must either have content or provide a link."));
        }
        return validateDataFlowTransaction(dataNotificationRequest.getTransactionId(), requesterId)
            .then(serializeDataTransferred(dataNotificationRequest))
                .doOnSuccess(this::notifyDataProcessor)
                .then(Flux.fromIterable(dataNotificationRequest.getEntries())
                .filter(entry -> entry.getLink() == null)
                .flatMap(entry -> insertHealthInformation(entry, dataNotificationRequest.getTransactionId()))
                .then());

//         TODO: this will be moved to the processor. For the time being, we are storing in db above.
//        return Flux.fromIterable(dataNotificationRequest.getEntries())
//                .filter(entry -> entry.getLink() == null)
//                .flatMap(entry -> insertHealthInformation(entry, dataNotificationRequest.getTransactionId()))
//                .then();
    }

    private Mono<Void> notifyDataProcessor(Map<String, String> contentRef) {
        return dataAvailabilityPublisher.broadcastDataAvailability(contentRef);
    }


    private Mono<Map<String, String>> serializeDataTransferred(DataNotificationRequest dataNotificationRequest) {
        return Mono.create(monoSink -> {
            byte[] bytes = contentFromRequest(dataNotificationRequest);
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
            //TODO: assert that dataFlowServiceProperties.getLocalStoragePath() exists
            Path pathToFile = Paths.get(dataFlowServiceProperties.getLocalStoragePath(), localFileNameToSave(dataNotificationRequest));
            AsynchronousFileChannel channel = null;
            try {
                channel = AsynchronousFileChannel.open(pathToFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            } catch (IOException e) {
                //TODO: send proper ClientError. Don't leak out internal details
                monoSink.error(e);
            }
            channel.write(byteBuffer, 0, byteBuffer, new CompletionHandler<Integer, ByteBuffer>() {
                @Override
                public void completed(Integer result, ByteBuffer attachment) {
                    Map<String, String> contentReference = createContentReference(dataNotificationRequest, pathToFile);
                    monoSink.success(contentReference);
                }
                @Override
                public void failed(Throwable exc, ByteBuffer attachment) {
                    monoSink.error(exc);
                }
            });
        });
    }

    private Map<String, String> createContentReference(DataNotificationRequest dataNotificationRequest, Path pathToFile) {
        Map<String, String> contentRef = new HashMap<>();
        contentRef.put("transactionId", dataNotificationRequest.getTransactionId());
        contentRef.put("pathToFile", pathToFile.toString());
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

    private Mono<Boolean> validateDataFlowTransaction(String transactionId, String requesterId) {
        return dataFlowRepository.retrieveDataFlowRequest(transactionId).flatMap(
                dataRequest -> {
                    //TODO: possibly validate the requesterId
                    return dataRequest != null? Mono.just(true) : Mono.just(false);
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