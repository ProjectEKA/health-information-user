package in.org.projecteka.hiu.user;

import in.org.projecteka.hiu.Caller;
import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.Error;
import in.org.projecteka.hiu.ErrorRepresentation;
import in.org.projecteka.hiu.user.model.UserAuthOnConfirmResponse;
import in.org.projecteka.hiu.user.model.UserAuthOnInitResponse;
import lombok.AllArgsConstructor;
import lombok.extern.java.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import static in.org.projecteka.hiu.ErrorCode.INVALID_REQUEST;
import static net.logstash.logback.argument.StructuredArguments.keyValue;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;

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

    @SuppressWarnings("PlaceholderCountMatchesArgumentCount")
    @PostMapping("/users/auth/on-init")
    public Mono<Void> usersAuthOnInit(@RequestBody UserAuthOnInitResponse userAuthOnInitResponse) {

        logger.info("Session request received {}", keyValue("requestId", userAuthOnInitResponse.getRequestId()),
                keyValue("timestamp", userAuthOnInitResponse.getTimestamp()));

        if (userAuthOnInitResponse.getError()!=null)
        {
            logger.info("",keyValue("errorCode", userAuthOnInitResponse.getError().getCode()),
                    keyValue("errorMessage", userAuthOnInitResponse.getError().getMessage()));
        }
        else
        {
            logger.info("",keyValue("transactionId", userAuthOnInitResponse.getAuth().getTransactionId()),
                    keyValue("authMetaMode", userAuthOnInitResponse.getAuth().getMode()),
                    keyValue("authMetaHint", userAuthOnInitResponse.getAuth().getMeta().getHint()),
                    keyValue("authMetaExpiry", userAuthOnInitResponse.getAuth().getMeta().getExpiry()));
        }
        logger.info("ResponseRequestId", keyValue("", userAuthOnInitResponse.getResp().getRequestId()));
        return Mono.create(MonoSink::success);
    }

    @PostMapping("/users/auth/on-confrim")
    public Mono<Void> usersAuthOnConfirm(@RequestBody UserAuthOnConfirmResponse userAuthOnConfirmResponse) {
        return Mono.empty();
    }
}

