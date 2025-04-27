package GUI;

import Communication.Command;
import Files.DownloadTaskManager;
import Search.FileSearchResult;
import Search.WordSearchMessage;
import Client.ClientManager;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.Timer;


public class MainInterface {

    private final JFrame frame;
    private DefaultListModel<String> searchResultsModel; // Modelo para a JList
    private DefaultListModel<String> downloadResultsModel;
    private final Map<String,Integer> donwloadResults = new TreeMap<>();
    private final Map<String, DownloadTaskManager> dtmmap = new TreeMap<>();
    ClientManager clientManager;

    public MainInterface(ClientManager clientManage, String ip, int port) {
        this.clientManager = clientManage;

        frame = new JFrame();
        frame.setTitle(ip + "/" + port) ;
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE); // para que o botao de fechar a janela termine a aplicacao
        addFrameContent();
        frame.pack(); // para que a janela se redimensione de forma a ter tod o seu conteudo visivel
        frame.setSize(500,300); //para iniciar a janela com um tamanho apropriado (pederá ser redimensionável)
        frame.setLocationRelativeTo(null); //para centrar a janela
    }

    public void open() {
        // para abrir a janela (torna-la visivel)
        frame.setVisible(true);
    }

    private void addFrameContent() {

        frame.setLayout(new BorderLayout());

        // Layout do Painel superior
        JPanel topPanel = new JPanel(new GridLayout(1,3)); //para ficar centrado como o exemplo dado pelo prof tem que se usar a GridLayout, embora também possamos usar aqui a BorderLayout e ficaria melhor visualmente
        JPanel bottomPanel = new JPanel(new GridLayout(1,3));
        JLabel instructionsSearchWindow = new JLabel("Texto a procurar: ");
        JTextField message = new JTextField("");
        JButton buttonSearch = new JButton("Procurar");

        topPanel.add(instructionsSearchWindow, BorderLayout.WEST);
        topPanel.add(message, BorderLayout.CENTER);
        topPanel.add(buttonSearch, BorderLayout.EAST);

        frame.add(topPanel, BorderLayout.NORTH);


        // Layout do Painel lateral esquerdo e central
        JPanel leftPanel = new JPanel(new BorderLayout());
        searchResultsModel = new DefaultListModel<>();
        JList<String> searchResultsList = new JList<>(searchResultsModel);
        searchResultsList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane scrollPane = new JScrollPane(searchResultsList);
        leftPanel.add(scrollPane, BorderLayout.CENTER);
        frame.add(scrollPane, BorderLayout.CENTER);
        downloadResultsModel = new DefaultListModel<>();
        JList<String> downloadResultsList = new JList<>(downloadResultsModel);
        bottomPanel.add(downloadResultsList, BorderLayout.SOUTH);

        //leftPanel.add(leftPanel, BorderLayout.SOUTH);
        frame.add(downloadResultsList, BorderLayout.SOUTH);

        // Layout do Painel lateral direito
        JPanel rightPanel = new JPanel(new GridLayout(2,1));
        JButton buttonDownload = new JButton("Descarregar");
        JButton buttonNode = new JButton("Ligar a Nó");
        rightPanel.add(buttonDownload, BorderLayout.NORTH);
        rightPanel.add(buttonNode, BorderLayout.SOUTH);
        frame.add(rightPanel, BorderLayout.EAST);

        buttonSearch.addActionListener(e -> {
            String searchTerm = message.getText().trim();
            if (searchTerm.isEmpty()) {
                return;
            }
            searchResultsModel.clear();
            clientManager.resetFileSearchDB();
            clientManager.sendAll(Command.WordSearchMessage, new WordSearchMessage(searchTerm));
            buttonSearch.setEnabled(false);

            Timer timeoutTimer = new Timer(3000, event -> {
                searchResultsModel.addElement("Ficheiro não encontrado");
                    buttonSearch.setEnabled(true);
            });
            timeoutTimer.setRepeats(false);
            timeoutTimer.start();

            clientManager.addListener(() -> {
                timeoutTimer.stop();
                SwingUtilities.invokeLater(() -> {
                    searchResultsModel.clear();
                    HashMap<String, List<FileSearchResult>> data = clientManager.getData();
                    for (List<FileSearchResult> file : data.values()) {
                        searchResultsModel.addElement(file.getFirst().toString() + "<" + file.size() + ">");
                    }
                    buttonSearch.setEnabled(true);
                });
            });
        });


        buttonDownload.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                List<String> selectedFiles = searchResultsList.getSelectedValuesList();

                if (!selectedFiles.isEmpty()) {     // Troca de verificação de null para uma verificação de lista vazia
                    for (String selectedFile : selectedFiles) {      // Adicionado loop para descarregar simlultaneamente múltiplos ficheiros
                        String modifiedString = selectedFile.substring(0, selectedFile.length() - 3);
                        DownloadTaskManager dtm = clientManager.startDownloadThreads(modifiedString);
                        dtmmap.remove(modifiedString);
                        dtmmap.put(modifiedString, dtm);

                        dtm.addListener((filename, fileblock) -> {
                            System.out.println("received update");
                            SwingUtilities.invokeLater(() -> {
                                if (!donwloadResults.containsValue(filename)) {
                                    donwloadResults.put(filename, fileblock);
                                } else {
                                    donwloadResults.replace(filename, fileblock);
                                }
                                downloadResultsModel.clear();
                                donwloadResults.forEach((key, value) -> {
                                    downloadResultsModel.addElement(key + " " + value + "%");
                                });
                            });
                        });
                    }
                    // Ajuste da mensagem para o plural
                    JOptionPane.showMessageDialog(frame, selectedFiles.size() + " download(s) iniciado(s) com sucesso.");
                } else {
                    JOptionPane.showMessageDialog(frame, "Selecione pelo menos um ficheiro.");
                }
            }
        });

        downloadResultsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String raw = downloadResultsList.getSelectedValue();
                    String treated = raw.substring(0, raw.length() - 5);
                    DownloadTaskManager dtm = dtmmap.get(treated);

                    if (dtm != null) {
                        StringBuilder message = new StringBuilder();

                        // Blocos por nó
                        Map<String, Integer> blocksPerNode = dtm.getBlocksPerNodeStats();
                        message.append("=== Download concluído com sucesso! ===");
                        blocksPerNode.forEach((node, count) -> {
                            String[] parts = node.split(":");
                            String ip = parts[0];
                            String port = parts[1];
                            message.append(String.format("\n- %s [%s]: %d blocos (%.1f%%)",
                                    ip, port, count, (count * 100.0) / dtm.getTotalBlocks()));
                        });

                        // Tempo total
                        message.append(String.format("\nTempo decorrido: %.3f segundos", dtm.getTotalTime()));

                        JOptionPane.showMessageDialog(
                                frame,
                                message.toString(),
                                "Stats: " + treated,
                                JOptionPane.INFORMATION_MESSAGE
                        );
                    }
                }
            }
        });

        // Action Listener do Botão "Ligar a Nó"
        buttonNode.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                PopUpInterface guiPopUp = new PopUpInterface(clientManager);
                guiPopUp.open();
            }
        });
    }

}
