package in.org.projecteka.hiu.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Builder
@Getter
@AllArgsConstructor
@Data
public class GatewayResponse {
    String requestId;
}