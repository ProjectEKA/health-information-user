package in.org.projecteka.hiu.clients;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class Patient {
    private String identifier;
    private String firstName;
    private String lastName;
}