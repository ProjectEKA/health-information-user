package in.org.projecteka.hiu.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import in.org.projecteka.hiu.consent.model.DateRange;
import in.org.projecteka.hiu.patient.PatientRepresentation;
import in.org.projecteka.hiu.patient.model.PatientSearchGatewayResponse;
import org.jeasy.random.EasyRandom;

public class TestBuilders {
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final EasyRandom easyRandom = new EasyRandom();

    public static String string() {
        return easyRandom.nextObject(String.class);
    }

    public static DateRange.DateRangeBuilder dateRange() {
        return easyRandom.nextObject(DateRange.DateRangeBuilder.class);
    }

    public static GatewayResponse.GatewayResponseBuilder gatewayResponse() {
        return easyRandom.nextObject(GatewayResponse.GatewayResponseBuilder.class);
    }

    public static PatientSearchGatewayResponse.PatientSearchGatewayResponseBuilder patientSearchGatewayResponse() {
        return easyRandom.nextObject(PatientSearchGatewayResponse.PatientSearchGatewayResponseBuilder.class);
    }

    public static PatientRepresentation.PatientRepresentationBuilder patientRepresentation() {
        return easyRandom.nextObject(PatientRepresentation.PatientRepresentationBuilder.class);
    }
}
