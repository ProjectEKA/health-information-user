package in.org.projecteka.hiu.user;

import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.Error;
import in.org.projecteka.hiu.ErrorCode;
import in.org.projecteka.hiu.ErrorRepresentation;
import lombok.AllArgsConstructor;
import org.apache.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import reactor.core.publisher.Mono;

@AllArgsConstructor
public class SessionService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JWTGenerator jwtGenerator;
    private final Logger logger = Logger.getLogger(SessionService.class);

    public Mono<Session> forNew(SessionRequest sessionRequest) {
        return Mono.justOrEmpty(sessionRequest)
                .flatMap(request -> userRepository.with(request.getUsername()))
                .filter(user -> passwordEncoder.matches(sessionRequest.getPassword(), user.getPassword()))
                .map(user -> new Session(jwtGenerator.tokenFrom(user), user.isActivated()))
                .doOnError(logger::error)
                .switchIfEmpty(Mono.error(new ClientError(HttpStatus.UNAUTHORIZED,
                        new ErrorRepresentation(new Error(ErrorCode.INVALID_USERNAME_OR_PASSWORD,
                                "Invalid username or password")))));
    }
}

