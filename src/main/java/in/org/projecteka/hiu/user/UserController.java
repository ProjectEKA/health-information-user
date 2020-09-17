package in.org.projecteka.hiu.user;

import in.org.projecteka.hiu.Caller;
import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.Error;
import in.org.projecteka.hiu.ErrorRepresentation;
import in.org.projecteka.hiu.user.model.UserAuthOnConfirmResponse;
import in.org.projecteka.hiu.user.model.UsersAuthOnInitResponse;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import static in.org.projecteka.hiu.ErrorCode.INVALID_REQUEST;
import static in.org.projecteka.hiu.common.Constants.PATH_ON_AUTH_CONFIRM;
import static in.org.projecteka.hiu.common.Constants.PATH_ON_AUTH_INIT;
import static org.springframework.http.HttpStatus.*;

@RestController
@AllArgsConstructor
public class UserController {
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @PostMapping("/users")
    public Mono<Void> createNew(@RequestBody Mono<User> userPublisher) {
        return userPublisher
                .filter(user -> user.getUsername() != null && user.getPassword() != null)
                .switchIfEmpty(Mono.error(new ClientError(BAD_REQUEST,
                        new ErrorRepresentation(new Error(INVALID_REQUEST, "Empty username or password")))))
                .filterWhen(this::doesNotExists)
                .switchIfEmpty(Mono.error(new ClientError(CONFLICT,
                        new ErrorRepresentation(new Error(INVALID_REQUEST, "User already exists")))))
                .map(user -> user.toBuilder()
                        .password(passwordEncoder.encode(user.getPassword()))
                        .role(user.getRole() == null ? Role.DOCTOR : user.getRole())
                        .verified(false)
                        .build())
                .flatMap(userRepository::save);
    }

    @PutMapping("/users/password")
    public Mono<Void> changePassword(@RequestBody ChangePasswordRequest changePasswordRequest) {
        var passwordValidation = PasswordValidator.validate(changePasswordRequest);

        if (!passwordValidation.isValid()) {
            return Mono.error(new ClientError(BAD_REQUEST,
                    new ErrorRepresentation(new Error(INVALID_REQUEST, passwordValidation.getError()))));
        }
        
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> (Caller) securityContext.getAuthentication().getPrincipal())
                .map(Caller::getUsername)
                .flatMap(userRepository::with)
                .filter(user -> passwordEncoder.matches(changePasswordRequest.getOldPassword(), user.getPassword()))
                .switchIfEmpty(Mono.error(new ClientError(BAD_REQUEST,
                        new ErrorRepresentation(new Error(INVALID_REQUEST, "Invalid Old password")))))
                .map(User::getUsername)
                .flatMap(username -> userRepository
                        .changePassword(username, passwordEncoder.encode(changePasswordRequest.getNewPassword())));
    }

    private Mono<Boolean> doesNotExists(User user) {
        return userRepository.with(user.getUsername())
                .map(x -> false)
                .switchIfEmpty(Mono.just(true));
    }

    @ResponseStatus(ACCEPTED)
    @SuppressWarnings("PlaceholderCountMatchesArgumentCount")
    @PostMapping(PATH_ON_AUTH_INIT)
    public Mono<Void> usersAuthOnInit(@RequestBody UsersAuthOnInitResponse userAuthOnInitResponse) {

        logger.info("Session request received {} requestId: " + userAuthOnInitResponse.getRequestId() +
                "timestamp: " + userAuthOnInitResponse.getTimestamp());

        if (userAuthOnInitResponse.getError() != null) {
            logger.info("errorCode: " + userAuthOnInitResponse.getError().getCode() +
                    "errorMessage: " + userAuthOnInitResponse.getError().getMessage());
        } else {
            logger.info("transactionId: " + userAuthOnInitResponse.getAuth().getTransactionId() +
                    ", authMetaMode: " + userAuthOnInitResponse.getAuth().getMode() +
                    ", authMetaHint: " + userAuthOnInitResponse.getAuth().getMeta().getHint() +
                    ", authMetaExpiry: " + userAuthOnInitResponse.getAuth().getMeta().getExpiry());
        }
        logger.info("ResponseRequestId: " + userAuthOnInitResponse.getResp().getRequestId());
        return Mono.empty();
    }

    @ResponseStatus(ACCEPTED)
    @PostMapping(PATH_ON_AUTH_CONFIRM)
    public Mono<Void> usersAuthOnConfirm(@RequestBody UserAuthOnConfirmResponse userAuthOnConfirmResponse) {
        logger.info("Session request received {} requestId:" + userAuthOnConfirmResponse.getRequestId() +
                "timestamp: " + userAuthOnConfirmResponse.getTimestamp());

        if (userAuthOnConfirmResponse.getError() != null) {
            logger.info("errorCode: " + userAuthOnConfirmResponse.getError().getCode() +
                    "errorMessage: " + userAuthOnConfirmResponse.getError().getMessage());
        } else {
            if (userAuthOnConfirmResponse.getAuth().getPatient() != null) {
                logger.info("Patient Demographics Details:" +
                        " Id: " + userAuthOnConfirmResponse.getAuth().getPatient().getId() +
                        " Name: " + userAuthOnConfirmResponse.getAuth().getPatient().getName() +
                        ", Birth Year: " + userAuthOnConfirmResponse.getAuth().getPatient().getYearOfBirth() +
                        ", Gender: ", userAuthOnConfirmResponse.getAuth().getPatient().getGender());
                if (userAuthOnConfirmResponse.getAuth().getPatient().getAddress() != null) {
                    logger.info("Patient Address Details: District: " + userAuthOnConfirmResponse.getAuth().getPatient().getAddress().getLine() +
                            ", Line: " + userAuthOnConfirmResponse.getAuth().getPatient().getAddress().getDistrict() +
                            ", Pincode: " + userAuthOnConfirmResponse.getAuth().getPatient().getAddress().getPincode() +
                            ", State: " + userAuthOnConfirmResponse.getAuth().getPatient().getAddress().getState());
                }
            }
        }
        logger.info("ResponseRequestId :" + userAuthOnConfirmResponse.getResp().getRequestId());
        return Mono.empty();
    }


}

