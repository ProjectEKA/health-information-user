package in.org.projecteka.hiu.consent.model;

import in.org.projecteka.hiu.dataflow.model.DataFlowRequest;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class DataFlowRequestTraceableMessage {
    String correlationId;
    DataFlowRequest dataFlowRequest;
}
