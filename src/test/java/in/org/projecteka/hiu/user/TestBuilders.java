package in.org.projecteka.hiu.user;

import org.jeasy.random.EasyRandom;

public class TestBuilders {

    private static final EasyRandom easyRandom = new EasyRandom();

    public static SessionRequest.SessionRequestBuilder sessionRequest() {
        return easyRandom.nextObject(SessionRequest.SessionRequestBuilder.class);
    }

    public static User.UserBuilder user() {
        return easyRandom.nextObject(User.UserBuilder.class);
    }

    public static String string() {
        return easyRandom.nextObject(String.class);
    }
}