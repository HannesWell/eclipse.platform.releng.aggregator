package vendor;

import provider.External;

public class Library {
    private int retained, unused;

    public int used() {
        retained = External.answer();
        return retained;
    }

    public int unused() {
        return unused;
    }
}
