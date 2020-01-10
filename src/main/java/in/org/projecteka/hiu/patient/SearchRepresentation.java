package in.org.projecteka.hiu.patient;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class SearchRepresentation {
    private PatientRepresentation patient;
}
