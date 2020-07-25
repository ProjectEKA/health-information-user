package in.org.projecteka.hiu.clients;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
@Builder
public class PatientSearchThrowable extends Throwable {
    private enum ErrorCode {
        NOTFOUND,
        UNKNOWN
    }

    ErrorCode code;
    String message;

    static PatientSearchThrowable notFound() {
        final String userDoesNotExist = "User does not exist";
        return new PatientSearchThrowable(ErrorCode.NOTFOUND, userDoesNotExist);
    }

    static PatientSearchThrowable unknown() {
        final String somethingWentWrong = "Something went wrong";
        return new PatientSearchThrowable(ErrorCode.UNKNOWN, somethingWentWrong);
    }
}
