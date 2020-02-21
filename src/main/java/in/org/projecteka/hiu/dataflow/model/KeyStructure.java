package in.org.projecteka.hiu.dataflow.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KeyStructure {
    private String expiry;
    private String parameters;
    private String keyValue;
}
