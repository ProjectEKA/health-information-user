package in.org.projecteka.hiu.dataflow.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KeyMaterial {
    private String cryptoAlg;
    private String curve;
    private KeyStructure dhPublicKey;
    private KeyStructure randomKey;
}
