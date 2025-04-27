package GUI;

import Client.ClientManager;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class PopUpInterface {

    private final JFrame frame;
    private DefaultListModel<String> searchResultsModel; // Modelo para a JList
    private final ClientManager clientManager;

    public PopUpInterface(ClientManager clientManage) {
        this.clientManager = clientManage;
        frame = new JFrame();
        addPopUpFrameContent();
        frame.setSize(590,90); // janela com um tamanho fixo inicial
        frame.setLocationRelativeTo(null); //para centrar a janela
    }


    public void open() {
        // para abrir a janela (torna-la visivel)
        frame.setVisible(true);
    }

    private final String addressInstructions = "Endereço: ";
    private final String portInstructions = "Porta: ";
    //ClientManager clientManager = new ClientManager();

    private void addPopUpFrameContent() {

        frame.setLayout(new FlowLayout(5));

        JPanel centralPanel = new JPanel(new FlowLayout()); //para ficar centrado como o exemplo dado pelo prof tem que se usar a GridLayout, embora também possamos usar aqui a BorderLayout e ficaria melhor visualmente

        JLabel address = new JLabel(addressInstructions);
        JTextField adressSearchWindow = new JTextField(20);

        centralPanel.add(address);
        centralPanel.add(adressSearchWindow);

        JLabel port = new JLabel(portInstructions);
        JTextField portSearchWindow = new JTextField(6);

        centralPanel.add(port);
        centralPanel.add(portSearchWindow);

        JButton buttonCancel = new JButton("Cancelar");
        JButton buttonOK = new JButton("OK");

        centralPanel.add(buttonCancel);
        centralPanel.add(buttonOK);

        frame.add(centralPanel, BorderLayout.CENTER);


        // Action Listener do Botão "Cancelar"
        buttonCancel.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.dispose();
            }
        });



        // Action Listener do Botão "OK"
        buttonOK.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String address = adressSearchWindow.getText();
                int port = Integer.parseInt(portSearchWindow.getText());
                if (validIP(address) && port >= 0 && port <= 65535) {
                    clientManager.addClientThread(address, port);
                    frame.dispose();
                    JOptionPane.showMessageDialog(frame, "A ligação foi efetuada com sucesso.");
                } else {
                    //System.out.println(address);
                    JOptionPane.showMessageDialog(frame, "Foram inseridos dados incorretos, por favor tente novamente.");
                }
            }
        });
    }

    public static boolean validIP(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        ip = ip.trim();
        if ((ip.length() < 6) || (ip.length() > 15)) {
            return false;
        }
        try {
            Pattern pattern = Pattern.compile("^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");
            Matcher matcher = pattern.matcher(ip);
            return matcher.matches();
        } catch (PatternSyntaxException ex) {
            return false;
        }
    }


}
