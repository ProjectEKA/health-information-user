package in.org.projecteka.hiu.clients;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class PatientRepresentation {
    private String identifier;
    private String name;

    public Patient toPatient() {
        String[] names = name.split(" ", 2);
        return Patient.builder().identifier(identifier)
                .firstName(names[0])
                .lastName(names.length > 1 ? names[1] : null)
                .build();
    }
}