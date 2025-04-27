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
import java.util.LinkedList;
import java.util.Queue;


public class ClientManager {
    // Campos declarados corretamente
    private final Map<ClientThread, Boolean> clientThreads;
    private final HashMap<String, List<FileSearchResult>> FileSearchDB;
    private final Map<String, DownloadTaskManager> downloadThreads = new HashMap<>();
    private final List<ClientManagerListener> listeners = new ArrayList<>();
    private CountDownLatch currentSearchLatch;
    private String currentSearchTerm;
    private final Queue<Runnable> queue = new LinkedList<>();
    private final List<Thread> workerThreads = new ArrayList<>();
    private static ClientManager instance; // Campo estático

    public ClientManager() {
        this.clientThreads = new TreeMap<>();
        this.FileSearchDB = new HashMap<>();

        // Inicializar threads manuais
        for (int i = 0; i < 5; i++) {
            Thread worker = new Thread(() -> {
                while (true) {
                    Runnable task;
                    synchronized (queue) {
                        while (queue.isEmpty()) {
                            try {
                                queue.wait();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                        task = queue.poll();
                    }
                    task.run();
                }
            });
            worker.start();
            workerThreads.add(worker);
        }
    }

    // Método sincronizado para definir a pesquisa atual
    public synchronized void setCurrentSearch(String term, CountDownLatch latch) {
        this.currentSearchTerm = term;
        this.currentSearchLatch = latch;
    }

    public synchronized CountDownLatch getCurrentSearchLatch() {
        return currentSearchLatch;
    }

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
            long activeNodes = clientThreads.values().stream().filter(status -> !status).count();
            setCurrentSearch(searched.getSearchTerm(), new CountDownLatch((int) activeNodes));

            for (ClientThread clientThread : clientThreads.keySet()) {
                Boolean status = clientThreads.get(clientThread);
                if (status != null && !status) {
                    synchronized (queue
                    ) { // Bloco sincronizado na taskQueue
                        queue.add(() -> {
                            synchronized (clientThreads) { // Sincronização no acesso às clientThreads
                                try {
                                    clientThreads.replace(clientThread, true);
                                    clientThread.sendObject(command, message);
                                } catch (IOException | InterruptedException e) {
                                    synchronized (ClientManager.this) {
                                        if (currentSearchLatch != null) {
                                            currentSearchLatch.countDown();
                                        }
                                    }
                                } finally {
                                    clientThreads.replace(clientThread, false);
                                }
                            }
                        });
                        queue.notify();
                    }
                } else {
                    if (currentSearchLatch != null) {
                        currentSearchLatch.countDown();
                    }
                }
            }
        }
    }

    public void receive(MessageWrapper message, ClientThread clientThread) {
        switch (message.getCommand()) {
            case FileSearchResult: {
                FileSearchResult[] received = (FileSearchResult[]) message.getData();
                for (FileSearchResult file : received) {
                    addToFileSearchResult(file);
                }
                clientThreads.replace(clientThread, false);
                if (currentSearchLatch != null) {
                    currentSearchLatch.countDown();
                }
                break;
            }
            case DownloadResult: {
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

    public HashMap<String, List<FileSearchResult>> getData() {
        return this.FileSearchDB;
    }

    private void addToFileSearchResult(FileSearchResult file) {
        String fileHash = file.getFileInfo().filehash;
        if (this.FileSearchDB.containsKey(fileHash)) {
            this.FileSearchDB.get(fileHash).add(file);
        } else {
            List<FileSearchResult> newList = new ArrayList<>();
            newList.add(file);
            this.FileSearchDB.put(fileHash, newList);
        }
        notifyListeners();
    }

    public void resetFileSearchDB() {
        this.FileSearchDB.clear();
    }

    public String searchFileByName(String name) {
        for (List<FileSearchResult> fs : FileSearchDB.values()) {
            if (!fs.isEmpty() && fs.get(0).getFileInfo().name.equals(name)) {
                return fs.get(0).getFileInfo().filehash;
            }
        }
        return null;
    }

    public DownloadTaskManager startDownloadThreads(String name) {
        String fileHash = searchFileByName(name);
        if (fileHash == null) return null;
        List<FileSearchResult> fsr = FileSearchDB.get(fileHash);
        if (fsr == null || fsr.isEmpty()) return null;

        DownloadTaskManager dtm = new DownloadTaskManager(this, fsr.get(0).getFileInfo(), fsr);
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

    // singleton
    public static synchronized ClientManager getInstance() {
        if (instance == null) {
            instance = new ClientManager();
        }
        return instance;
    }

}