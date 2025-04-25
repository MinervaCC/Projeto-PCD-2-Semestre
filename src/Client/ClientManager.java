package Client;

import Communication.Command;
import Communication.MessageWrapper;
import Communication.CountDownLatch;
import Download.FileBlockAnswerMessage;
import Files.DownloadTaskManager;
import Search.FileSearchResult;
import Search.WordSearchMessage;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientManager {
    private final Map<ClientThread, Boolean> clientThreads;
    private final HashMap<String, List<FileSearchResult>> FileSearchDB;
    private final Map<String, DownloadTaskManager> downloadThreads = new HashMap<>();
    private final ExecutorService threadPool = Executors.newFixedThreadPool(5);
    private final List<ClientManagerListener> listeners = new ArrayList<>();
    private CountDownLatch currentSearchLatch;
    private String currentSearchTerm;

    public ClientManager() {
        this.clientThreads = new TreeMap<>();
        this.FileSearchDB = new HashMap<>();
    }

    // Método sincronizado para definir a pesquisa atual
    public synchronized void setCurrentSearch(String term, CountDownLatch latch) {
        this.currentSearchTerm = term;
        this.currentSearchLatch = latch;
    }

    // Método sincronizado para obter o latch
    public synchronized CountDownLatch getCurrentSearchLatch() {
        return currentSearchLatch;
    }

    // Método sincronizado para obter o termo de pesquisa
    public synchronized String getCurrentSearchTerm() {
        return currentSearchTerm;
    }

    public void addClientThread(String ip, int port) {
        ClientThread newThread = new ClientThread(this, ip, port);
        synchronized (this) {
            clientThreads.put(newThread, false);
        }
    }

    public void removeClientThread(ClientThread clientThread) {
        synchronized (this) {
            clientThreads.remove(clientThread);
        }
    }

    public void sendAll(Command command, Object message) {
        synchronized (this) {
            if (!(message instanceof WordSearchMessage)) {
                return;
            }
            WordSearchMessage searched = (WordSearchMessage) message;
            // Conta apenas nós ativos (não ocupados)
            long activeNodes = clientThreads.values().stream().filter(status -> !status).count();
            setCurrentSearch(searched.getSearchTerm(), new CountDownLatch((int) activeNodes));

            for (ClientThread clientThread : clientThreads.keySet()) {
                if (!clientThreads.get(clientThread)) { // Se o nó está ativo
                    threadPool.submit(() -> {
                        try {
                            clientThreads.replace(clientThread, true); // Marca como ocupado
                            clientThread.sendObject(command, message);
                        } catch (InterruptedException | IOException e) {
                            // Se falhar, decrementa o latch
                            synchronized (ClientManager.this) {
                                if (currentSearchLatch != null) {
                                    currentSearchLatch.countDown();
                                }
                            }
                        } finally {
                            clientThreads.replace(clientThread, false); // Libera o nó
                        }
                    });
                } else {
                    // Nó inativo: decrementa o latch
                    if (currentSearchLatch != null) {
                        currentSearchLatch.countDown();
                    }
                }
            }
        }
    }

    public void receive(MessageWrapper message, ClientThread clientThread) {
        switch (message.getCommand()) {
            case Command.FileSearchResult: {
                FileSearchResult[] received = (FileSearchResult[]) message.getData();
                for (FileSearchResult file : received) {
                    addToFileSearchResult(file);
                }
                clientThreads.replace(clientThread, false);
                // ***
                if (currentSearchLatch != null) {
                    currentSearchLatch.countDown();
                }
                break;
            }
            case Command.DownloadResult: {
                FileBlockAnswerMessage received = (FileBlockAnswerMessage) message.getData();
                System.out.println("Cliente received block: " + received.getBlockId());
                downloadThreads.get(received.getDtmUID()).addFileblock(received.getBlockId(), received);
                break;
            }
            default: {
                System.out.println(message.getData().toString() + Thread.currentThread().getName());
                break;
            }
        }
    }

    public  HashMap<String, List<FileSearchResult>> getData() {
        return this.FileSearchDB;
    }

    private void addToFileSearchResult(FileSearchResult file) {
        if(this.FileSearchDB.containsKey(file.getFileInfo().filehash)){
            this.FileSearchDB.get(file.getFileInfo().filehash).add(file);
        }else{
            this.FileSearchDB.put(file.getFileInfo().filehash, new ArrayList<>() {{ add(file);}});
        }
        notifyListeners();
    }

    public void resetFileSearchDB(){
        this.FileSearchDB.clear();
    }

    public String searchFileByName(String name){
        for(List<FileSearchResult> fs   : FileSearchDB.values() ){
            if(fs.getFirst().getFileInfo().name.equals(name)){
                return fs.getFirst().getFileInfo().filehash;
            }
        }
        return null;
    }

    public DownloadTaskManager startDownloadThreads(String name) {
        List<FileSearchResult> fsr = FileSearchDB.get(searchFileByName(name));
        DownloadTaskManager dtm = new DownloadTaskManager(this, fsr.getFirst().getFileInfo(),fsr);
        this.downloadThreads.put(dtm.getUid(), dtm);
        dtm.start();
        return dtm;

    }

    public void addListener(ClientManagerListener listener) {
        listeners.add(listener);
    }

    private void notifyListeners() {
        for (ClientManagerListener listener : listeners) {
            listener.onRequestComplete();
        }
    }

}
