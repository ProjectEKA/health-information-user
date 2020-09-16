package in.org.projecteka.hiu.user.model;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class Address {
    String line;
    String district;
    String state;
    String pincode;
}
