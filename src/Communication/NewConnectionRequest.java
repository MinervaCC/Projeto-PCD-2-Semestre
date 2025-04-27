package Communication;

import java.io.Serializable;

public class NewConnectionRequest implements Serializable {
    private String ip;
    private int port;
    public NewConnectionRequest(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }
}
