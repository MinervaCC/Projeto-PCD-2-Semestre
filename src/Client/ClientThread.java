package Client;

import Communication.Command;
import Communication.MessageWrapper;
import Communication.NewConnectionRequest;

import java.io.IOException;
import java.util.UUID;

public class ClientThread extends Thread implements Comparable<ClientThread> {
    private final SocketClient socketClient;
    private final String clientName;
    private final ClientManager clientManager;
    private volatile boolean isRunning = true;

    public ClientThread(ClientManager clientManager, String ip, int port) {
        this.clientManager = clientManager;
        this.socketClient = new SocketClient(ip, port);
        this.clientName = UUID.randomUUID().toString();

        // Envia NewConnectionRequest após conectar
        try {
            socketClient.startSocket();
            // Envia mensagem de pedido de conexão
            socketClient.sendObject(
                    Command.ConnectionRequest,
                    new NewConnectionRequest(socketClient.getLocalIP(), socketClient.getLocalPort())
            );
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Falha ao enviar NewConnectionRequest", e);
        }

        this.start();
    }


    @Override
    public void run() {
        try {
            // Aguarda confirmação (ConnectionAck)
            MessageWrapper ack = socketClient.receiveObject();
            if (ack.getCommand() != Command.ConnectionAck) {
                throw new IOException("Conexão recusada pelo nó remoto");
            }

            System.out.println("Conexão confirmada com " + socketClient.getLocalIP() + ":" + socketClient.getLocalPort());

            // Processamento normal das mensagens
            while (isRunning) {
                this.clientManager.receive(socketClient.receiveObject(), this);
            }
        } catch (InterruptedException | IOException e) {
            System.out.println("Erro na conexão: " + e.getMessage());
        }
    }

    public synchronized void sendObject(Command command, Object message) throws IOException, InterruptedException {
        socketClient.sendObject(command, message);
    }

    public String getClientName() {
        return clientName;
    }

    public void terminate() throws IOException, InterruptedException {
        isRunning = false; // Signal the thread to stop.
        interrupt(); // Interrupt the thread if it's blocked.
        socketClient.stopConnection();
        System.out.println(clientName + " has been terminated.");
    }

    @Override
    public int compareTo(ClientThread other) {
        return this.clientName.compareTo(other.clientName);
    }


}
