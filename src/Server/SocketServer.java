package Server;

import java.io.*;
import java.net.*;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import Communication.Command;
import Communication.MessageWrapper;
import Download.FileBlockAnswerMessage;
import Download.FileBlockRequestMessage;
import Files.FileInfo;
import Search.FileSearchResult;
import Search.WordSearchMessage;

public class SocketServer extends Thread {

    private ServerSocket serverSocket;
    private int port;
    private boolean running = true;    // Para controlar o estado da comunicação do servidor
    boolean isPortBound = false;
    private static final BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();
    private static volatile boolean workerRunning = true;

    public SocketServer(int port) {
        this.port = port;
    }

    // ***
    static {
        Thread workerThread = new Thread(() -> {
            while (workerRunning) {
                try {
                    Runnable task = taskQueue.take();
                    task.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        workerThread.start();
    }

    public synchronized void startServer() {
        try {
            while (!isPortBound) {
                try {
                    serverSocket = new ServerSocket(port);    // Cria um novo ServerSocket na porta especificada
                    isPortBound = true;
                } catch (BindException e) {
                    System.out.println("Port " + port + " is in use. Trying next port...");
                    port++;
                } catch (IOException e) {
                    throw new RuntimeException("An unexpected error occurred while binding the port", e);
                }
            }
            serverSocket.setReuseAddress(true);    // Permite que o endereço de socket criado possa ser reutilizado
            System.out.println("Server listening on port " + port);
            while (running) {
                Socket socket = serverSocket.accept();   // Aceita uma nova conexão de cliente
                new Thread(() -> {
                    try {
                        System.out.println("Server accepted connection from " + socket.getRemoteSocketAddress() + " on thread" + Thread.currentThread().getName() );
                        handleClient(socket);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }).start();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void handleClient(Socket socket) throws IOException {        //Metodo que lidar com o cliente
        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());  // Cria um fluxo de saída para o cliente
        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());    // Cria um fluxo de entrada do cliente

        while(running){     // Loop que continua enquanto o servidor está em execução
            try {
                MessageWrapper message = (MessageWrapper) in.readObject();
                System.out.println("Server received message");

                switch (message.getCommand()) {

                    case Command.WordSearchMessage:{    // Para a busca de palavras
                        WordSearchMessage data =  (WordSearchMessage)  message.getData();    // Obtém os dados da mensagem
                        List<FileInfo> searchResult =  data.search();      // Realiza a busca e obtém os resultados
                        FileSearchResult[] result = new FileSearchResult[searchResult.size()];     // Cria um array para os resultados da busca
                        for(int i = 0; i < searchResult.size(); i++){        // Loop pelos resultados da busca
                            result[i] = new FileSearchResult(
                                    data,
                                    searchResult.get(i),
                                    message.getServerIp() ,
                                    message.getServerPort());       // Cria um resultado de busca para cada item encontrado
                        }
                        out.writeObject(new MessageWrapper(
                                message.getServerIp(),
                                message.getServerPort(),
                                Command.FileSearchResult,
                                result));    // Envia os resultados de volta para o cliente
                        break; // Sai do switch
                    }
                    // ***
                    case Command.DownloadMessage: {
                        FileBlockRequestMessage data = (FileBlockRequestMessage) message.getData();
                        // Adicionar à fila em vez de processar imediatamente
                        taskQueue.add(() -> {
                            try {
                                FileBlockAnswerMessage result = new FileBlockAnswerMessage(
                                        data.getFileHash(),
                                        data.getBlock(),
                                        data.getBlockID(),
                                        data.getDtmUID(),
                                        socket.getInetAddress().getHostAddress(),
                                        this.port
                                );
                                synchronized (out) { // Garantir sincronização ao escrever
                                    out.writeObject(new MessageWrapper(
                                            message.getServerIp(),
                                            message.getServerPort(),
                                            Command.DownloadResult,
                                            result
                                    ));
                                }
                                System.out.println("Server sending block " + result.getBlockId());
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
                        break;
                    }

                    case Command.Terminate:{   // Para terminar a comunicação
                        socketStop();
                        return;
                    }

                    case Command.String:{     // Para tratamento de Strings
                        out.writeObject(message);
                        break;
                    }

                    default:{      // Para comandos desconhecidos
                        System.out.println("Unknown command");
                        break;
                    }
                }

            } catch (IOException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void stopWorker() {
        workerRunning = false;
    }

    public synchronized void socketStop() throws IOException {
        this.running = false;   // Altera o estado do servidor para "já não estou em execução"
        //notifyAll();  // Não é necessário notificar todas as Threads
        this.serverSocket.close();   // Fecha o socket do servidor
        System.out.println("Connection closed server ");
    }
}
