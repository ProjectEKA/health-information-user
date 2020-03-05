package in.org.projecteka.hiu.dataprocessor.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DataAvailableMessage {
    private String transactionId;
    private String pathToFile;
    private String partNumber;
}
