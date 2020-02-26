package in.org.projecteka.hiu.patient;

import in.org.projecteka.hiu.clients.Patient;
import org.jeasy.random.EasyRandom;

public class TestBuilders {

    private static final EasyRandom easyRandom = new EasyRandom();

    static Patient.PatientBuilder patient() {
        return easyRandom.nextObject(Patient.PatientBuilder.class);
    }
}
