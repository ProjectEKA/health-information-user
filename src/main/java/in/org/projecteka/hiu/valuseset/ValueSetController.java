package in.org.projecteka.hiu.valuseset;

import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@AllArgsConstructor
public class ValueSetController {
    private ValueSetResource valueSetResource;

    @GetMapping(value = "ValueSet", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> defaultQuiz() {
        return Mono.just(valueSetResource.getValueSetDefinition());
    }
}
