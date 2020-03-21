package in.org.projecteka.hiu.user;

import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.Error;
import in.org.projecteka.hiu.ErrorCode;
import in.org.projecteka.hiu.ErrorRepresentation;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import reactor.core.publisher.Mono;

@AllArgsConstructor
public class SessionService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JWTGenerator jwtGenerator;

    public Mono<Session> forNew(SessionRequest sessionRequest) {
        return Mono.justOrEmpty(sessionRequest)
                .flatMap(sessionRequest1 -> userRepository.with(sessionRequest1.getUsername()))
                .filter(user -> passwordEncoder.matches(sessionRequest.getPassword(), user.getPassword()))
                .map(user -> new Session(jwtGenerator.tokenFrom(user)))
                .switchIfEmpty(Mono.error(new ClientError(HttpStatus.UNAUTHORIZED,
                        new ErrorRepresentation(new Error(ErrorCode.INVALID_USERNAME_OR_PASSWORD,
                                "Invalid username or password")))));
    }
}

