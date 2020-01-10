package in.org.projecteka.hiu.patient;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;


@AllArgsConstructor
@NoArgsConstructor
public class PatientSearchException extends Throwable {
    private enum ErrorCode {
        NotFound,
        Unknown
    }
    private ErrorCode code;
    private String message;

    static PatientSearchException notFound() {
        final String userDoesNotExist = "User does not exist";
        return new PatientSearchException(ErrorCode.NotFound, userDoesNotExist);
    }

    static PatientSearchException unknown() {
        final String somethingWentWrong = "Something went wrong";
        return new PatientSearchException(ErrorCode.Unknown, somethingWentWrong);
    }
}
