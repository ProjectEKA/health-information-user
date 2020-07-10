package in.org.projecteka.hiu.common.heartbeat.model;

import in.org.projecteka.hiu.Error;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Builder
@Value
public class HeartbeatResponse {
    private LocalDateTime timeStamp;
    private Status status;
    private Error error;
}
