package in.org.projecteka.hiu.common.heartbeat.model;

import in.org.projecteka.hiu.Error;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class HeartbeatResponse {
    private String timeStamp;
    private Status status;
    private Error error;
}
