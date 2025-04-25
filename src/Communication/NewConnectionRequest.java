package Communication;

import java.io.Serializable;

public class NewConnectionRequest implements Serializable {
    private String senderHost;
    private int senderPort;

    public NewConnectionRequest(String senderHost, int senderPort) {
        this.senderHost = senderHost;
        this.senderPort = senderPort;
    }

    public String getSenderHost() {
        return senderHost;
    }

    public int getSenderPort() {
        return senderPort;
    }
}

