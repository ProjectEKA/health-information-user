package in.org.projecteka.hiu.common;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class TraceableMessage {
    String correlationId;
    Object message;
}
