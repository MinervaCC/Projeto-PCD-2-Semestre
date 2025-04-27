package Files;

import Client.ClientManager;
import Client.ClientThread;
import Communication.Command;
import Download.FileBlockAnswerMessage;
import Download.FileBlockRequestMessage;
import Search.FileSearchResult;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class DownloadTaskManager extends Thread {
    private final ClientManager clientManager;
    private final FileInfo fileInfo;
    private final List<FileSearchResult> availableNodes;

    private final String uid = UUID.randomUUID().toString();

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition blocksAvailable = lock.newCondition();
    private final Condition downloadComplete = lock.newCondition();

    // Estruturas para controle de blocos
    private final Queue<Integer> pendingBlocks = new ConcurrentLinkedQueue<>();
    private final Map<Integer, Boolean> inProgressBlocks = new ConcurrentHashMap<>();
    private final Map<Integer, FileBlockAnswerMessage> completedBlocks = new ConcurrentHashMap<>();

    // Estatísticas e listeners
    private final Map<String, Integer> blocksPerNode = new ConcurrentHashMap<>();
    private final List<DownloadTaskManagerListener> listeners = new ArrayList<>();
    private long totalTime;


    public DownloadTaskManager(ClientManager clientManager, FileInfo fileInfo, List<FileSearchResult> nodes) {
        this.clientManager = clientManager;
        this.fileInfo = fileInfo;
        this.availableNodes = nodes;
    }

    @Override
    public void run() {
        System.out.println("Download iniciado para: " + fileInfo.name);
        totalTime = System.currentTimeMillis();
        for (int i = 0; i < fileInfo.blockNumber; i++) {        // Inicializa a queue de blocos
            pendingBlocks.add(i);
        }
        List<Thread> downloadThreads = getThreads();
        downloadThreads.forEach(thread -> {        // Aguarda o término das threads
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        new Thread(this::writeFileWithLock).start();        // **Escrita com Condition**
    }

    private List<Thread> getThreads() {
        List<Thread> downloadThreads = new ArrayList<>();
        int MAX_CONCURRENT_DOWNLOADS = 5;
        for (int i = 0; i < MAX_CONCURRENT_DOWNLOADS; i++) {
            Thread thread = new Thread(() -> {
                while (!pendingBlocks.isEmpty()) { // Processa até a fila esvaziar
                    Integer blockId = pendingBlocks.poll();
                    if (blockId != null) {
                        processSingleBlock(blockId);
                    }
                }
            });
            thread.start();
            downloadThreads.add(thread);
        }
        return downloadThreads;
    }

    private void processSingleBlock(Integer blockId) {
        try {
            FileSearchResult node = availableNodes.get(new Random().nextInt(availableNodes.size()));
            ClientThread thread = new ClientThread(clientManager, node.getIp(), node.getPort());
            // CORREÇÃO: Enviar FileBlockRequestMessage, não AnswerMessage!
            thread.sendObject(
                    Command.DownloadMessage,
                    new FileBlockRequestMessage( // Alterado para Request
                            fileInfo.fileBlockManagers.get(blockId),
                            fileInfo.filehash,
                            uid,
                            blockId
                    )
            );
        } catch (IOException | InterruptedException e) {
            lock.lock();
            try {
                pendingBlocks.add(blockId); // Recoloca o bloco na fila
                blocksAvailable.signal();  // Notifica outras threads
            } finally {
                lock.unlock();
            }
        } finally {
            inProgressBlocks.remove(blockId);
        }
    }

    public void addFileblock(int blockId, FileBlockAnswerMessage fileBlock) {
        lock.lock();
        try {
            completedBlocks.put(blockId, fileBlock);
            // Atualiza as estatísticas por nó
            String nodeKey = fileBlock.getSenderIP() + ":" + fileBlock.getSenderPort();
            blocksPerNode.merge(nodeKey, 1, Integer::sum);

            notifyListeners(completedBlocks.size());     // Notificar  a GUI do progresso atual
            if (completedBlocks.size() == fileInfo.blockNumber) {   // Sinaliza a Condition de conclusão apenas quando todos os blocos estiverem prontos
                downloadComplete.signal();
            }
        } finally {
            lock.unlock();
        }
    }

    private void writeFileWithLock() {
        lock.lock();
        try {
            while (completedBlocks.size() < fileInfo.blockNumber) {
                downloadComplete.await(); // Aguarda sinal
                // Verificação adicional para evitar loop infinito
                if (completedBlocks.size() == fileInfo.blockNumber) {
                    break;
                }
            }
            fileInfo.writeFile(new TreeMap<>(completedBlocks));
            System.out.println("Download concluído: " + fileInfo.name);
            totalTime = System.currentTimeMillis() - totalTime;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
    }

    private void notifyListeners(int blocksDownloaded) {
        float progress = (float) blocksDownloaded / fileInfo.blockNumber * 100;
        listeners.forEach(listener ->
                listener.onRequestComplete(fileInfo.name, (int) progress) // Já corrigido
        );
    }

    // Métodos auxiliares
    public Map<String, Integer> getBlocksPerNodeStats() {
        return new HashMap<>(blocksPerNode);
    }

    public float getTotalTime() {
        return (float) totalTime / 1000;
    }

    public String getUid() {
        return uid;
    }

    // ***
    public int getTotalBlocks() {
        return fileInfo.blockNumber;
    }

    public void addListener(DownloadTaskManagerListener listener) {
        listeners.add(listener);
    }
}

