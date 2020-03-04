package in.org.projecteka.hiu.dataflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeyMaterial {
    private String cryptoAlg;
    private String curve;
    private KeyStructure dhPublicKey;
    private String nonce;
}
