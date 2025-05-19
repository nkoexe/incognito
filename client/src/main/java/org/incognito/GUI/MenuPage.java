package org.incognito.GUI;

import org.incognito.crypto.CryptoManager;
import org.incognito.crypto.QRUtil;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Base64;
import java.util.logging.Logger;

public class MenuPage extends JFrame {
    private static final Logger logger = Logger.getLogger(MenuPage.class.getName());

    private CryptoManager cryptoManager;
    private String myPublicKeyString;
    private MenuListener menuListener;
    private boolean isKeyInitiator = false; // Flag to indicate if this user is the key initiator

    private JLabel qrCodeLabel;
    private JButton scanQRButton;
    private JButton saveQRButton;
    private JButton proceedButton;
    private JButton generateAndShareKeyButton;

    public interface MenuListener {
        void onKeysExchangedAndProceed(CryptoManager readyCryptoManager, MenuPage menuPageInstance);
        void onCancel(MenuPage menuPageInstance);
    }

    public MenuPage(CryptoManager cryptoManager, MenuListener listener) {
        this.cryptoManager = cryptoManager;
        this.myPublicKeyString = this.cryptoManager.getPublicKeyBase64();
        this.menuListener = listener;

        setTitle("QR Key Exchange");
        setSize(400, 550);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // Handled by WindowListener
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (menuListener != null) {
                    menuListener.onCancel(MenuPage.this);
                } else {
                    dispose();
                    System.exit(0);
                }
            }
        });

        initComponents();
        updateQRCodeImage(myPublicKeyString);
    }

    private void initComponents() {
        // Choice Panel Host/client
        JPanel choicePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        choicePanel.setBorder(BorderFactory.createTitledBorder("Choose role"));
        JRadioButton hostButton = new JRadioButton("Host");
        JRadioButton guestButton = new JRadioButton("Client");
        ButtonGroup group = new ButtonGroup();
        group.add(hostButton);
        group.add(guestButton);
        choicePanel.add(hostButton);
        choicePanel.add(guestButton);

        // QR Code Panel
        JPanel qrPanel = new JPanel(new BorderLayout());
        qrPanel.setBorder(BorderFactory.createTitledBorder("Your public key QR Code"));
        qrCodeLabel = new JLabel();
        qrCodeLabel.setHorizontalAlignment(SwingConstants.CENTER);
        qrCodeLabel.setPreferredSize(new Dimension(200, 200));
        qrPanel.add(qrCodeLabel, BorderLayout.CENTER);

        // Button Panel
        JPanel buttonPanel = new JPanel(new GridLayout(5, 1, 10, 10));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        saveQRButton = new JButton("Save QR Code");
        saveQRButton.addActionListener(e -> saveMyQRCodeToFile());
        buttonPanel.add(saveQRButton);

        scanQRButton = new JButton("Scan Contact QR Code");
        scanQRButton.setEnabled(false);
        scanQRButton.addActionListener(e -> scanContactQRCodeFromFile());
        buttonPanel.add(scanQRButton);

        generateAndShareKeyButton = new JButton("Generate and Share AES Key");
        generateAndShareKeyButton.setEnabled(false);
        generateAndShareKeyButton.addActionListener(e -> generateAndShareAESKey());
        buttonPanel.add(generateAndShareKeyButton);

        JButton importAESKeyButton = new JButton("Import Encrypted AES Key");
        importAESKeyButton.setEnabled(false);
        importAESKeyButton.addActionListener(e -> importEncryptedAESKey());
        buttonPanel.add(importAESKeyButton);

        proceedButton = new JButton("Join Chat");
        proceedButton.setEnabled(false);
        proceedButton.addActionListener(e -> {
            if (menuListener != null) {
                menuListener.onKeysExchangedAndProceed(this.cryptoManager, this);
            }
        });
        buttonPanel.add(proceedButton);

        // Actions for Host/Client selection
        hostButton.addActionListener(e -> {
            isKeyInitiator = true;
            scanQRButton.setEnabled(true);
            generateAndShareKeyButton.setEnabled(false);
            importAESKeyButton.setEnabled(false);
        });

        guestButton.addActionListener(e -> {
            isKeyInitiator = false;
            scanQRButton.setEnabled(true);
            generateAndShareKeyButton.setEnabled(false);
            importAESKeyButton.setEnabled(true);
        });

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.add(choicePanel, BorderLayout.NORTH);
        mainPanel.add(qrPanel, BorderLayout.CENTER);

        add(mainPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void updateQRCodeImage(String data) {
        try {
            if (data == null || data.isEmpty()) {
                qrCodeLabel.setIcon(null);
                qrCodeLabel.setText("Public key not available.");
                return;
            }
            BufferedImage qrBufferedImage = QRUtil.generateQRCodeImage(data, 200, 200);
            ImageIcon qrIcon = new ImageIcon(qrBufferedImage);
            qrCodeLabel.setIcon(qrIcon);
            qrCodeLabel.setText(null);
        } catch (Exception e) {
            logger.severe("Error during QR Code generation: " + e.getMessage());
            e.printStackTrace();
            qrCodeLabel.setIcon(null);
            qrCodeLabel.setText("QR Code Error.");
            JOptionPane.showMessageDialog(this, "Unable to generate QR Code.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveMyQRCodeToFile() {
        if (myPublicKeyString == null || myPublicKeyString.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No public key available to save.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save QR Code");
        fileChooser.setSelectedFile(new File("my_public_key_qr.png"));
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PNG Images", "png"));

        int option = fileChooser.showSaveDialog(this);
        if (option == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            // Ensure .png extension
            String filePath = file.getAbsolutePath();
            if (!filePath.toLowerCase().endsWith(".png")) {
                file = new File(filePath + ".png");
            }
            try {
                QRUtil.generateQRCode(myPublicKeyString, file.getAbsolutePath(), 200, 200);
                JOptionPane.showMessageDialog(this, "QR Code successfully saved as " + file.getName(), "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                logger.severe("Error while saving QR Code: " + e.getMessage());
                JOptionPane.showMessageDialog(this,
                        "Error saving QR Code: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void scanContactQRCodeFromFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Contact's QR Code Image");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Images (*.png, *.jpg, *.jpeg, *.gif)", "png", "jpg", "jpeg", "gif"));

        int option = fileChooser.showOpenDialog(this);
        if (option == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                String scannedKey = QRUtil.decodeQRCode(file);
                if (scannedKey != null && !scannedKey.isEmpty()) {
                    cryptoManager.setOtherUserPublicKey(scannedKey); // Set the other user's public key
                    JOptionPane.showMessageDialog(this,
                            "Contact's public key successfully imported.",
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                    if (isKeyInitiator) generateAndShareKeyButton.setEnabled(true);
                } else {
                    JOptionPane.showMessageDialog(this,
                            "No valid QR code found in the image or key is empty.",
                            "Warning", JOptionPane.WARNING_MESSAGE);
                }
            } catch (Exception e) {
                logger.severe("Error while scanning QR Code: " + e.getMessage());
                JOptionPane.showMessageDialog(this,
                        "Error decoding QR Code: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void generateAndShareAESKey() {

        try {
            if (isKeyInitiator) {
                SecretKey aesKey = cryptoManager.generateAESKey();
                cryptoManager.setAesSessionKey(aesKey);

                byte[] aesKeyBytes = aesKey.getEncoded();
                String aesKeyBase64 = Base64.getEncoder().encodeToString(aesKeyBytes);

                String encryptedAESKey = cryptoManager.encryptWithOtherUserPublicKey(aesKeyBase64);

                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setDialogTitle("Save Encrypted AES Key");
                fileChooser.setSelectedFile(new File("encrypted_aes_key.txt"));

                int option = fileChooser.showSaveDialog(this);
                if (option == JFileChooser.APPROVE_OPTION) {
                    File file = fileChooser.getSelectedFile();
                    try (FileWriter writer = new FileWriter(file)) {
                        writer.write(encryptedAESKey);
                        JOptionPane.showMessageDialog(this,
                                "Encrypted AES key successfully saved to " + file.getName(),
                                "Success", JOptionPane.INFORMATION_MESSAGE);
                        proceedButton.setEnabled(true); // Enable proceed button after key exchange
                    } catch (Exception e) {
                        logger.severe("Error while saving encrypted AES key: " + e.getMessage());
                        JOptionPane.showMessageDialog(this,
                                "Error saving encrypted AES key: " + e.getMessage(),
                                "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            } else {
                importEncryptedAESKey();
            }
        } catch (Exception e) {
            logger.severe("Error during AES key generation: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Error generating AES key: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void importEncryptedAESKey() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Encrypted AES Key File");

        int option = fileChooser.showOpenDialog(this);
        if (option == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                StringBuilder content = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        content.append(line);
                    }
                }

                String encryptedAESKey = content.toString();
                String decryptedAESKeyBase64 = cryptoManager.decryptWithPrivateKey(encryptedAESKey);

                byte[] aesKeyBytes = Base64.getDecoder().decode(decryptedAESKeyBase64);
                SecretKey aesKey = new SecretKeySpec(aesKeyBytes, "AES");

                cryptoManager.setAesSessionKey(aesKey);

                JOptionPane.showMessageDialog(this,
                        "Encrypted AES key successfully imported and decrypted.",
                        "Success", JOptionPane.INFORMATION_MESSAGE);
                proceedButton.setEnabled(true); // Enable proceed button after key exchange
            } catch (Exception e) {
                logger.severe("Error while importing encrypted AES key: " + e.getMessage());
                JOptionPane.showMessageDialog(this,
                        "Error importing encrypted AES key: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        try {
            String testMessage = "Test Message";
            String encrypted = cryptoManager.encrypt(testMessage);
            String decrypted = cryptoManager.decrypt(encrypted);

            if (testMessage.equals(decrypted)) {
                logger.info("AES encryption/decryption test succeeded!");
            } else {
                logger.severe("AES encryption/decryption test failed!");
            }
        } catch (Exception e) {
            logger.severe("Error while testing the encryption/decryption " + e.getMessage());
        }
    }
}
