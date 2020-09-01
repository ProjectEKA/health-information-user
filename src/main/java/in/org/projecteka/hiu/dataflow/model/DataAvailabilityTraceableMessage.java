package in.org.projecteka.hiu.dataflow.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Builder
@Data
public class DataAvailabilityTraceableMessage {
    String correlationId;
    Map<String, String> contentRef;
}
