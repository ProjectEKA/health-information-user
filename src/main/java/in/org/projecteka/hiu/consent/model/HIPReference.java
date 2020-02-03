package in.org.projecteka.hiu.consent.model;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotEmpty;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class HIPReference {
    @NotEmpty(message = "HIP identifier is not specified.")
    private String id;
    private String name;
}
