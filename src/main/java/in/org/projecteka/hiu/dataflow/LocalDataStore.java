package in.org.projecteka.hiu.dataflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.org.projecteka.hiu.dataflow.model.DataNotificationRequest;
import lombok.SneakyThrows;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class LocalDataStore {

    public Mono<Void> serializeDataToFile(DataNotificationRequest dataNotificationRequest, Path outFileName) {
        return Mono.create(monoSink -> {
            byte[] bytes = contentFromRequest(dataNotificationRequest);
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
            //TODO: find location from application properties. also create the filename under a directory thats relevant to transaction

            createParentDirectoriesIfNotExists(outFileName);

            AsynchronousFileChannel channel = null;
            try {
                channel = AsynchronousFileChannel.open(outFileName, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            } catch (IOException e) {
                monoSink.error(e);
            }
            channel.write(byteBuffer, 0, byteBuffer, new CompletionHandler<Integer, ByteBuffer>() {
                @Override
                public void completed(Integer result, ByteBuffer attachment) {
                    monoSink.success();
                }
                @Override
                public void failed(Throwable exc, ByteBuffer attachment) {
                    monoSink.error(exc);
                }
            });
        });
    }

    @SneakyThrows
    private void createParentDirectoriesIfNotExists(Path outFileName) {
        Files.createDirectories(outFileName.getParent());
    }

    private static byte[] contentFromRequest(DataNotificationRequest dataNotificationRequest) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsBytes(dataNotificationRequest);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return null;
    }
}
