package in.org.projecteka.hiu.consent;


import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;


@AllArgsConstructor
@NoArgsConstructor
public class ConsentException extends Throwable {
    private ErrorCode code;
    private String message;

    public static ConsentException creationFailed() {
        final String failedToCreateRequest = "Failed to create consent request";
        return new ConsentException(ErrorCode.CREATION_FAILED, failedToCreateRequest);
    }

    private enum ErrorCode {
        CREATION_FAILED
    }
}
