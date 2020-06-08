package in.org.projecteka.hiu.patient.model;

import lombok.Value;

import java.io.Serializable;

@Value
public class Requester implements Serializable {
    String type;
    String id;
}
