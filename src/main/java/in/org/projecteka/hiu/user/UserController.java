package in.org.projecteka.hiu.user;

import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.Error;
import in.org.projecteka.hiu.ErrorRepresentation;
import lombok.AllArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import static in.org.projecteka.hiu.ErrorCode.INVALID_REQUEST;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;

@RestController
@AllArgsConstructor
public class UserController {
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

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
                        .build())
                .flatMap(userRepository::save);
    }

    private Mono<Boolean> doesNotExists(User user) {
        return userRepository.with(user.getUsername())
                .map(x -> false)
                .switchIfEmpty(Mono.just(true));
    }
}

