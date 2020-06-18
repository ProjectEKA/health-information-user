package in.org.projecteka.hiu.dataflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import in.org.projecteka.hiu.dataflow.model.DataNotificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;

public class LocalDataStore {
    private static final Logger logger = LoggerFactory.getLogger(LocalDataStore.class);

    public Mono<Void> serializeDataToFile(DataNotificationRequest dataNotificationRequest, Path outFileName) {
        return Mono.create(monoSink ->
                contentFromRequest(dataNotificationRequest)
                        .ifPresentOrElse(
                                bytes -> {
                                    ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
                                    //TODO: find location from application properties.
                                    // also create the filename under a directory that's relevant to transaction
                                    try {
                                        createParentDirectoriesIfNotExists(outFileName);
                                        var channel = AsynchronousFileChannel.open(outFileName,
                                                StandardOpenOption.CREATE,
                                                StandardOpenOption.WRITE);
                                        channel.write(byteBuffer, 0, byteBuffer, new CompletionHandler<>() {
                                            @Override
                                            public void completed(Integer result, ByteBuffer attachment) {
                                                monoSink.success();
                                            }

                                            @Override
                                            public void failed(Throwable exc, ByteBuffer attachment) {
                                                monoSink.error(exc);
                                            }
                                        });
                                    } catch (IOException e) {
                                        logger.error(e.getMessage());
                                        monoSink.error(e);
                                    }
                                },
                                () -> monoSink.error(new Exception("Not able to process the request"))));
    }

    public void deleteExpiredConsentData(Path pathToTransactionDirectory) {
        logger.info(String.format("Deleting the health information from: %s", pathToTransactionDirectory.toString()));
        try (Stream<Path> paths = Files.walk(pathToTransactionDirectory).sorted(Comparator.reverseOrder())) {
            paths.forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    logger.error(e.getMessage());
                }
            });
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    private void createParentDirectoriesIfNotExists(Path outFileName) throws IOException {
        Files.createDirectories(outFileName.getParent());
    }

    private static Optional<byte[]> contentFromRequest(DataNotificationRequest dataNotificationRequest) {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(WRITE_DATES_AS_TIMESTAMPS, false);

        try {
            return Optional.ofNullable(objectMapper.writeValueAsBytes(dataNotificationRequest));
        } catch (JsonProcessingException e) {
            logger.error(e.getMessage());
            return Optional.empty();
        }
    }
}
