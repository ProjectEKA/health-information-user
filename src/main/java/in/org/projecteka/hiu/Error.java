package in.org.projecteka.hiu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@AllArgsConstructor
@Builder
@NoArgsConstructor
@Data
public class Error {
    private ErrorCode code;
    private String message;

    public static Error serviceDownError(String message) {
        return new Error(ErrorCode.SERVICE_DOWN, message);
    }
}


