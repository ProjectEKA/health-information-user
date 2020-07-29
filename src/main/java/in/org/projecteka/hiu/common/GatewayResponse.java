package in.org.projecteka.hiu.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@AllArgsConstructor
public class GatewayResponse {
    String requestId;
}