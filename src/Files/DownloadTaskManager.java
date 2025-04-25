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

public class DownloadTaskManager extends Thread {
    private final ClientManager clientManager;
    private final FileInfo fileInfo;
    private final List<FileSearchResult> availableNodes;

    private final String uid = UUID.randomUUID().toString();
    private final int MAX_CONCURRENT_DOWNLOADS = 5;

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

        // Inicializar fila de blocos
        for (int i = 0; i < fileInfo.blockNumber; i++) {
            pendingBlocks.add(i);
        }

        // Criar threads de download
        List<Thread> downloadThreads = new ArrayList<>();
        for (int i = 0; i < MAX_CONCURRENT_DOWNLOADS; i++) {
            Thread thread = new Thread(this::processBlocks);
            downloadThreads.add(thread);
            thread.start();
        }

        // Aguardar término das threads
        downloadThreads.forEach(thread -> {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Iniciar escrita em disco
        new Thread(this::writeFile).start();
    }

    private void processBlocks() {
        while (!pendingBlocks.isEmpty()) {
            Integer blockId = pendingBlocks.poll();
            if (blockId == null) return;

            inProgressBlocks.put(blockId, true);

            try {
                FileSearchResult node = availableNodes.get(
                        new Random().nextInt(availableNodes.size())
                );

                ClientThread thread = new ClientThread(clientManager, node.getIp(), node.getPort());
                thread.sendObject(
                        Command.DownloadMessage,
                        new FileBlockRequestMessage(
                                fileInfo.fileBlockManagers.get(blockId),
                                fileInfo.filehash,
                                uid,
                                blockId
                        )
                );
            } catch (IOException | InterruptedException e) {
                pendingBlocks.add(blockId); // Reenfileira em caso de falha
            } finally {
                inProgressBlocks.remove(blockId);
            }
        }
    }

    public void addFileblock(int blockId, FileBlockAnswerMessage fileBlock) {
        completedBlocks.put(blockId, fileBlock);

        // Atualizar estatísticas
        String nodeKey = fileBlock.getSenderIP() + ":" + fileBlock.getSenderPort();
        blocksPerNode.merge(nodeKey, 1, Integer::sum);

        // Notificar GUI
        notifyListeners(completedBlocks.size());
    }

    private void writeFile() {
        // Aguardar conclusão de todos os blocos
        while (completedBlocks.size() < fileInfo.blockNumber) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Escrever em disco
        fileInfo.writeFile(new TreeMap<>(completedBlocks));
        System.out.println("Download concluído: " + fileInfo.name);

        // Calcular tempo total
        totalTime = System.currentTimeMillis() - totalTime;
    }

    private void notifyListeners(int blocksDownloaded) {
        float progress = (float) blocksDownloaded / fileInfo.blockNumber * 100;
        listeners.forEach(listener ->
                listener.onRequestComplete(fileInfo.name, (int) progress)
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