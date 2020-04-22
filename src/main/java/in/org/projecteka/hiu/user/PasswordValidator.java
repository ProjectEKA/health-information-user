package in.org.projecteka.hiu.user;

import com.google.common.base.Strings;
import io.vavr.control.Validation;
import org.passay.*;
import java.util.Arrays;

public class PasswordValidator {
    public static Validation<String, String> validate(ChangePasswordRequest changePasswordRequest) {
        String newPassword = changePasswordRequest.getNewPassword();
        String oldPassword = changePasswordRequest.getOldPassword();

        if (Strings.isNullOrEmpty(oldPassword)) {
            return Validation.invalid("Old password can't be empty");
        }

        if (Strings.isNullOrEmpty(newPassword)) {
            return Validation.invalid("New password can't be empty");
        }

        if(oldPassword.equals(newPassword)){
            return Validation.invalid("New password cannot be same as old password");
        }
        org.passay.PasswordValidator validator = new org.passay.PasswordValidator(Arrays.asList(
                new LengthRule(8, 30),
                new CharacterRule(EnglishCharacterData.UpperCase, 1),
                new CharacterRule(EnglishCharacterData.LowerCase, 1),
                new CharacterRule(EnglishCharacterData.Digit, 1),
                new CharacterRule(EnglishCharacterData.Special, 1),
                new IllegalSequenceRule(new SequenceData() {
                    @Override
                    public String getErrorCode() {
                        return "ILLEGAL_NUMERICAL_SEQUENCE";
                    }

                    @Override
                    public CharacterSequence[] getSequences() {
                        return new CharacterSequence[]{
                                new CharacterSequence("`1234567890")
                        };
                    }
                }, 3, false)));
        RuleResult result = validator.validate(new PasswordData(newPassword));
        if (result.isValid()) {
            return Validation.valid(newPassword);
        }

        var error = validator.getMessages(result).stream().reduce((acc, msg) -> acc + "\n" + msg);
        return Validation.invalid(error.orElse(""));
    }
}
