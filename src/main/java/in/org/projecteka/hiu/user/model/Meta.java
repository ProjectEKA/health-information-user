package in.org.projecteka.hiu.user.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@AllArgsConstructor
@Builder
public class Meta {
    String hint;
    LocalDateTime expiry;
}
