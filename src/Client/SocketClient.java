package Client;

import java.io.*;
import java.net.*;
import Communication.Command;
import Communication.MessageWrapper;

import static Communication.Command.Terminate;

public class SocketClient  {

    private Socket clientSocket;
    private ObjectOutputStream out;    // FLuxo de saída de dados
    private ObjectInputStream in;    // FLuxo de entrada de dados
    private final String ip;
    private final int port;
    private boolean ready = false;  // Para gerir o estado da comunicação

    public SocketClient(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public synchronized void startSocket(){
        try{
            this.clientSocket = new Socket(ip, port);   // Conecta-se ao servidor com determinado IP e por uma determinada porta
            System.out.println(clientSocket.getLocalPort());
            System.out.println("Client connected to " + ip + ":" + port);
            this.out = new ObjectOutputStream(clientSocket.getOutputStream());  // Iniciado o fluxo de saída
            this.in = new ObjectInputStream(clientSocket.getInputStream());   // Iniciado o fluxo de entrada
            this.ready = true;  // Definida a conexão como pronta
            notify();
        }catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public void sendObject(Command command,  Object object) throws IOException, InterruptedException {
        while (!ready) {    // Aguarda até que a conexão esteja pronta
            wait();
        }
        System.out.println("Client sent message to " + ip + ":" + port);
        MessageWrapper message = new MessageWrapper(this.ip, this.port,command, object); // "Embrulha" e envia uma mensagem
        this.out.writeObject(message);   // Envia a mensagem
    }

    public synchronized MessageWrapper receiveObject() throws IOException, InterruptedException {
        while (!ready) {
            wait();
        }
        try {
            return (MessageWrapper) in.readObject();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

    }

    public synchronized void stopConnection() throws IOException, InterruptedException {
        try {
            sendObject(Terminate, null); // Send the terminate command.
        } catch (IOException e) {
            System.err.println("Error sending terminate command: " + e.getMessage());
        }
        // Close the socket safely.
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                System.out.println("Client disconnected");
                clientSocket.close();
                System.out.println("Connection closed for client.");
            }
        } catch (IOException e) {
            System.err.println("Error closing socket: " + e.getMessage());
        }
    }

    public String getLocalIP() {
        return clientSocket.getLocalAddress().getHostAddress();
    }

    public int getLocalPort() {
        return clientSocket.getLocalPort();
    }

}
