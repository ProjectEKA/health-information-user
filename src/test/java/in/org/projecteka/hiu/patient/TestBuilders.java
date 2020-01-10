package in.org.projecteka.hiu.patient;

import org.jeasy.random.EasyRandom;

public class TestBuilders {

    private static final EasyRandom easyRandom = new EasyRandom();

    static SearchRepresentation.SearchRepresentationBuilder searchRepresentation() {
        return easyRandom.nextObject(SearchRepresentation.SearchRepresentationBuilder.class);
    }
}
