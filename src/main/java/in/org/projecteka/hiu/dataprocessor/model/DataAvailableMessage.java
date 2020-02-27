package in.org.projecteka.hiu.dataprocessor.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class DataAvailableMessage {
    private String transactionId;
    private String pathToFile;
    private String partNumber;
}
