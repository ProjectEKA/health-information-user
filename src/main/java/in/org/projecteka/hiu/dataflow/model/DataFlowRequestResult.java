package in.org.projecteka.hiu.dataflow.model;

import in.org.projecteka.hiu.common.GatewayResponse;
import in.org.projecteka.hiu.common.RespError;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@Value
public class DataFlowRequestResult {
    UUID requestId;
    LocalDateTime timestamp;
    HIRequest hiRequest;
    RespError error;
    GatewayResponse resp;

    public GatewayResponse getResp() {
        return resp == null ? GatewayResponse.builder().build() : resp;
    }
}
