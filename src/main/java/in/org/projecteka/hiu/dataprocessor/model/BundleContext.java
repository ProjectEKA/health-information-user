package in.org.projecteka.hiu.dataprocessor.model;

import org.hl7.fhir.r4.model.Bundle;

public class BundleContext {
    private Bundle bundle;

    public BundleContext(Bundle bundle) {
        this.bundle = bundle;
    }
}
