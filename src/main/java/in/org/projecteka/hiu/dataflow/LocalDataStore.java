package in.org.projecteka.hiu.dataflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.org.projecteka.hiu.dataflow.model.DataNotificationRequest;
import org.apache.log4j.Logger;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

public class LocalDataStore {
    private static final Logger logger = Logger.getLogger(LocalDataStore.class);

    public Mono<Void> serializeDataToFile(DataNotificationRequest dataNotificationRequest, Path outFileName) {
        return Mono.create(monoSink ->
                contentFromRequest(dataNotificationRequest)
                        .ifPresent(bytes -> {
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
                                logger.error(e);
                                monoSink.error(e);
                            }
                        }));
    }

    private void createParentDirectoriesIfNotExists(Path outFileName) throws IOException {
        Files.createDirectories(outFileName.getParent());
    }

    private static Optional<byte[]> contentFromRequest(DataNotificationRequest dataNotificationRequest) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return Optional.ofNullable(objectMapper.writeValueAsBytes(dataNotificationRequest));
        } catch (JsonProcessingException e) {
            logger.error(e);
            return Optional.empty();
        }
    }
}
