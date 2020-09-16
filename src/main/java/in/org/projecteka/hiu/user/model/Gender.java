package in.org.projecteka.hiu.user.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = GenderDeserializer.class)
public enum Gender {
    M, F, O, U, INVALID_GENDER;

    public static Gender fromText(String gender) {
        if (gender.equalsIgnoreCase("M")
                || gender.equalsIgnoreCase("F")
                || gender.equalsIgnoreCase("O")
                ||gender.equalsIgnoreCase("U")){
            return Gender.valueOf(gender);
        }else{
            return Gender.INVALID_GENDER;
        }
    }
}
