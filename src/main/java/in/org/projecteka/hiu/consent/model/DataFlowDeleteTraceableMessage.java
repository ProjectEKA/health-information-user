package in.org.projecteka.hiu.consent.model;

import in.org.projecteka.hiu.dataflow.model.DataFlowDelete;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class DataFlowDeleteTraceableMessage {
    String correlationId;
    DataFlowDelete dataFlowDelete;
}
