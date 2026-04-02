package buet.com.demo3;

import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public class HelloController {
    private static final String LANG_EN = "English";
    private static final String LANG_BN = "\u09AC\u09BE\u0982\u09B2\u09BE";
    private static final List<String> DOCUMENT_TYPES = List.of("PDF", "Image", "Text Type Authenticity", "ID Card");

    @FXML private ComboBox<String> languageCombo;
    @FXML private Button signInButton;
    @FXML private Label heroTitleLabel;
    @FXML private Label heroSubtitleLabel;
    @FXML private VBox choicePanel;
    @FXML private Label choiceTitleLabel;
    @FXML private Label choiceSubtitleLabel;
    @FXML private Button chooseUserButton;
    @FXML private Button chooseAdminButton;

    @FXML private VBox userPanel;
    @FXML private Button userBackButton;
    @FXML private Label cardTitleLabel;
    @FXML private Label cardSubtitleLabel;
    @FXML private Label emailLabel;
    @FXML private TextField filePathField;
    @FXML private Label docTypeLabel;
    @FXML private ComboBox<String> docTypeCombo;
    @FXML private Label filesLabel;
    @FXML private Label filesHintLabel;
    @FXML private Label documentFileLabel;
    @FXML private Label documentDropLabel;
    @FXML private Button selectDocumentButton;
    @FXML private Label documentHintLabel;
    @FXML private Label certificateFileLabel;
    @FXML private Label certificateDropLabel;
    @FXML private Button selectCertificateButton;
    @FXML private Label certificateHintLabel;
    @FXML private TextArea statusArea;
    @FXML private Button continueButton;

    @FXML private VBox adminPanel;
    @FXML private Button adminBackButton;
    @FXML private VBox adminLoginPanel;
    @FXML private Label adminSectionTitleLabel;
    @FXML private Label adminSectionSubtitleLabel;
    @FXML private Label adminUsernameLabel;
    @FXML private TextField adminUsernameField;
    @FXML private Label adminPasswordLabel;
    @FXML private PasswordField adminPasswordField;
    @FXML private TextArea adminStatusArea;
    @FXML private Button adminLoginButton;
    @FXML private VBox adminUploadPanel;
    @FXML private Label adminUploadTitleLabel;
    @FXML private Label adminUploadSubtitleLabel;
    @FXML private Label totalDocumentsLabel;
    @FXML private Label totalDocumentsValueLabel;
    @FXML private Label verifiedUsersLabel;
    @FXML private Label verifiedUsersValueLabel;
    @FXML private Label adminDocTypeLabel;
    @FXML private ComboBox<String> adminDocTypeCombo;
    @FXML private Label adminDocumentFileLabel;
    @FXML private Label adminDocumentDropLabel;
    @FXML private Button selectAdminDocumentButton;
    @FXML private Label adminCertificateFileLabel;
    @FXML private Label adminCertificateDropLabel;
    @FXML private Button selectAdminCertificateButton;
    @FXML private TextArea adminUploadStatusArea;
    @FXML private Button adminUploadButton;
    @FXML private Button deleteDocumentButton;
    @FXML private Button adminLogoutButton;
    @FXML private Label documentsListLabel;
    @FXML private ListView<String> documentsListView;

    private File selectedDocumentFile;
    private File selectedCertificateFile;
    private File selectedAdminDocumentFile;
    private File selectedAdminCertificateFile;
    private String authenticatedAdminUsername;
    private Database.GoogleUserSession googleUserSession;
    private List<Database.ReferenceDocument> currentReferenceDocuments = List.of();

    @FXML
    private void initialize() {
        languageCombo.getItems().setAll(LANG_EN, LANG_BN);
        languageCombo.setValue(LANG_EN);
        docTypeCombo.getItems().setAll(DOCUMENT_TYPES);
        docTypeCombo.setValue(DOCUMENT_TYPES.get(0));
        adminDocTypeCombo.getItems().setAll(DOCUMENT_TYPES);
        adminDocTypeCombo.setValue(DOCUMENT_TYPES.get(0));

        statusArea.setEditable(false);
        adminStatusArea.setEditable(false);
        adminUploadStatusArea.setEditable(false);

        applyEnglishTexts();
        showChoicePanel();
        showAdminLoginStep();
        disableGoogleSignIn();
        updateUserStatus("SQLite connected. User uploads will be compared against the latest admin reference.");
        updateAdminLoginStatus("Admin login ready. Enter your assigned username and password.");
        updateAdminUploadStatus("Upload page will appear after admin login.");
        refreshAdminDashboard();
    }

    @FXML
    private void handleLanguageChange() {
        if (Objects.equals(languageCombo.getValue(), LANG_BN)) {
            applyBanglaTexts();
        } else {
            applyEnglishTexts();
        }
    }

    @FXML
    private void handleChooseUser() {
        setPanelVisibility(false, true, false);
    }

    @FXML
    private void handleChooseAdmin() {
        setPanelVisibility(false, false, true);
        showAdminLoginStep();
    }

    @FXML
    private void handleBackToChoice() {
        authenticatedAdminUsername = null;
        showAdminLoginStep();
        setPanelVisibility(true, false, false);
    }

    @FXML
    private void handleAdminLogin() {
        String username = adminUsernameField.getText();
        String password = adminPasswordField.getText();

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            updateAdminLoginStatus("Enter both admin username and password.");
            return;
        }

        try {
            boolean authenticated = Database.authenticateAdmin(username.trim(), password);
            if (!authenticated) {
                updateAdminLoginStatus("Login failed.");
                showAdminLoginErrorWindow();
                return;
            }

            authenticatedAdminUsername = username.trim();
            showAdminUploadStep();
            updateAdminUploadStatus("Welcome, " + authenticatedAdminUsername + ". You have logged in successfully. Upload trusted reference files.");
            refreshAdminDashboard();
        } catch (SQLException exception) {
            updateAdminLoginStatus("Failed to authenticate admin.");
        }
    }

    @FXML
    private void handleAdminLogout() {
        authenticatedAdminUsername = null;
        adminPasswordField.clear();
        selectedAdminDocumentFile = null;
        selectedAdminCertificateFile = null;
        showAdminLoginStep();
        updateAdminLoginStatus("Logged out successfully.");
    }

    @FXML
    private void handleBrowse(ActionEvent event) {
        File file = selectFile("Select File");
        if (file == null) {
            return;
        }

        Object source = event.getSource();
        if (source == selectCertificateButton) {
            selectedCertificateFile = file;
            updateUserStatus("User selection\nDocument: " + fileName(selectedDocumentFile)
                    + "\nReference attachment: " + fileName(selectedCertificateFile));
        } else if (source == selectDocumentButton) {
            selectedDocumentFile = file;
            updateUserStatus("User selection\nDocument: " + fileName(selectedDocumentFile)
                    + "\nReference attachment: " + fileName(selectedCertificateFile));
        } else if (source == selectAdminCertificateButton) {
            selectedAdminCertificateFile = file;
            updateAdminUploadStatus("Admin selection\nDocument: " + fileName(selectedAdminDocumentFile)
                    + "\nReference attachment: " + fileName(selectedAdminCertificateFile));
        } else if (source == selectAdminDocumentButton) {
            selectedAdminDocumentFile = file;
            updateAdminUploadStatus("Admin selection\nDocument: " + fileName(selectedAdminDocumentFile)
                    + "\nReference attachment: " + fileName(selectedAdminCertificateFile));
        }
    }

    @FXML
    private void handleVerify() {
        String email = filePathField.getText();
        String docType = docTypeCombo.getValue();

        if (email == null || email.isBlank()) {
            updateUserStatus("Please provide your email.");
            return;
        }
        if (docType == null || docType.isBlank()) {
            updateUserStatus("Please choose a document type.");
            return;
        }
        if (selectedDocumentFile == null) {
            updateUserStatus("Please upload the main document.");
            return;
        }

        try {
            String documentHash = calculateFileHash(selectedDocumentFile);
            String attachmentHash = selectedCertificateFile == null ? null : calculateFileHash(selectedCertificateFile);
            Database.ReferenceDocument referenceDocument = Database.findLatestReferenceDocument(docType);

            String comparisonResult;
            Integer referenceDocumentId = null;
            boolean matched = false;

            if (referenceDocument == null) {
                comparisonResult = "No admin reference uploaded yet for " + docType + ".";
            } else {
                referenceDocumentId = referenceDocument.id();
                boolean documentMatches = documentHash.equals(referenceDocument.documentHash());
                String referenceAttachmentHash = referenceDocument.certificateHash();
                boolean hasUserAttachment = attachmentHash != null && !attachmentHash.isBlank();
                boolean hasReferenceAttachment = referenceAttachmentHash != null && !referenceAttachmentHash.isBlank();

                matched = documentMatches;
                if (documentMatches && hasUserAttachment && hasReferenceAttachment) {
                    matched = attachmentHash.equals(referenceAttachmentHash);
                }

                if (!documentMatches) {
                    comparisonResult = "Document did not match the trusted reference.";
                } else if (hasUserAttachment && hasReferenceAttachment) {
                    comparisonResult = matched
                            ? "Document and attachment matched the trusted reference."
                            : "Attachment did not match the trusted reference.";
                } else if (hasReferenceAttachment) {
                    comparisonResult = "Document matched the trusted reference. An admin attachment exists but was not submitted for comparison.";
                } else if (hasUserAttachment) {
                    comparisonResult = "Document matched the trusted reference. Submitted attachment was stored but no admin attachment exists for comparison.";
                } else {
                    comparisonResult = "Document matched the trusted reference.";
                }
            }

            Database.saveVerificationRequest(email.trim(), docType, selectedDocumentFile.getAbsolutePath(),
                    selectedCertificateFile == null ? null : selectedCertificateFile.getAbsolutePath(),
                    documentHash, attachmentHash,
                    comparisonResult, referenceDocumentId);

            updateUserStatus("Verification stored in SQLite."
                    + "\nEmail: " + email.trim()
                    + "\nType: " + docType
                    + "\nResult: " + comparisonResult);

            if (referenceDocument == null) {
                showResultWindow("Reference Missing", "", "No admin reference found for " + docType + ".", "#92400e", "#fff7ed");
            } else if (matched) {
                showResultWindow("Document Matched", "", comparisonResult, "#166534", "#f0fdf4");
            } else {
                showResultWindow("Document Mismatched", "X", comparisonResult, "#b91c1c", "#fff7f7");
            }
        } catch (IOException exception) {
            updateUserStatus("Failed to read one of the user files.");
        } catch (SQLException exception) {
            updateUserStatus("Failed to compare or save the verification request.");
        }
    }

    @FXML
    private void handleAdminUpload() {
        String docType = adminDocTypeCombo.getValue();

        if (authenticatedAdminUsername == null || authenticatedAdminUsername.isBlank()) {
            updateAdminUploadStatus("Log in first.");
            showAdminLoginStep();
            return;
        }
        if (docType == null || docType.isBlank()) {
            updateAdminUploadStatus("Choose a document type for the admin reference.");
            return;
        }
        if (selectedAdminDocumentFile == null) {
            updateAdminUploadStatus("Upload the admin reference document.");
            return;
        }

        try {
            String documentHash = calculateFileHash(selectedAdminDocumentFile);
            String attachmentHash = selectedAdminCertificateFile == null ? null : calculateFileHash(selectedAdminCertificateFile);

            Database.saveReferenceDocument(authenticatedAdminUsername, docType,
                    selectedAdminDocumentFile.getAbsolutePath(),
                    selectedAdminCertificateFile == null ? null : selectedAdminCertificateFile.getAbsolutePath(),
                    documentHash, attachmentHash);

            updateAdminUploadStatus("Admin reference saved to SQLite."
                    + "\nAdmin: " + authenticatedAdminUsername
                    + "\nType: " + docType
                    + "\nDocument: " + selectedAdminDocumentFile.getName()
                    + "\nReference attachment: " + fileName(selectedAdminCertificateFile)
                    + "\nSHA-256: " + documentHash);
            refreshAdminDashboard();
        } catch (IOException exception) {
            updateAdminUploadStatus("Failed to read one of the admin files.");
        } catch (SQLException exception) {
            updateAdminUploadStatus("Failed to save the admin reference.");
        }
    }

    @FXML
    private void handleDeleteSelectedDocument() {
        if (authenticatedAdminUsername == null || authenticatedAdminUsername.isBlank()) {
            updateAdminUploadStatus("Log in first.");
            return;
        }

        int selectedIndex = documentsListView.getSelectionModel().getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= currentReferenceDocuments.size()) {
            updateAdminUploadStatus("Select a document from the list to delete it.");
            return;
        }

        Database.ReferenceDocument selectedDocument = currentReferenceDocuments.get(selectedIndex);
        Alert confirmAlert = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Delete " + selectedDocument.fileName() + " from the database?",
                ButtonType.YES,
                ButtonType.NO
        );
        confirmAlert.setTitle("Delete Document");
        confirmAlert.setHeaderText(null);

        if (confirmAlert.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
            return;
        }

        try {
            Database.deleteReferenceDocument(selectedDocument.id());
            updateAdminUploadStatus("Deleted document from SQLite.\nName: " + selectedDocument.fileName());
            refreshAdminDashboard();
        } catch (SQLException exception) {
            updateAdminUploadStatus("Failed to delete the selected document.");
        }
    }

    @FXML
    private void handleGoogleSignIn() {
        updateUserStatus("Enter your email manually in the field provided.");
    }

    private void refreshAdminDashboard() {
        try {
            totalDocumentsValueLabel.setText(String.valueOf(Database.countReferenceDocuments()));
            verifiedUsersValueLabel.setText(String.valueOf(Database.countVerifiedUsers()));

            currentReferenceDocuments = Database.getAllReferenceDocuments();
            List<String> rows = currentReferenceDocuments.stream()
                    .map(document -> "Name: " + document.fileName()
                            + " | Type: " + document.docType()
                            + " | Uploaded: " + document.createdAt()
                            + " | SHA-256: " + document.documentHash())
                    .toList();
            documentsListView.setItems(FXCollections.observableArrayList(rows));
        } catch (SQLException exception) {
            totalDocumentsValueLabel.setText("-");
            verifiedUsersValueLabel.setText("-");
            documentsListView.setItems(FXCollections.observableArrayList("Failed to load documents from SQLite."));
        }
    }

    private void loadGoogleUserSession() {
        try {
            googleUserSession = Database.getGoogleUserSession();
            applyGoogleUserSession();
        } catch (SQLException exception) {
            restoreSignInButtonText();
        }
    }

    private void applyGoogleUserSession() {
        showEmailInput();
        restoreSignInButtonText();
    }

    private ImageView createUserAvatar(String pictureUrl) {
        if (pictureUrl == null || pictureUrl.isBlank()) {
            return null;
        }

        Image image = new Image(pictureUrl, true);
        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(24);
        imageView.setFitHeight(24);
        Rectangle clip = new Rectangle(24, 24);
        clip.setArcWidth(24);
        clip.setArcHeight(24);
        imageView.setClip(clip);
        return imageView;
    }

    private void restoreSignInButtonText() {
        signInButton.setVisible(false);
        signInButton.setManaged(false);
    }

    private void showChoicePanel() {
        setPanelVisibility(true, false, false);
    }

    private void setPanelVisibility(boolean showChoice, boolean showUser, boolean showAdmin) {
        choicePanel.setVisible(showChoice);
        choicePanel.setManaged(showChoice);
        userPanel.setVisible(showUser);
        userPanel.setManaged(showUser);
        adminPanel.setVisible(showAdmin);
        adminPanel.setManaged(showAdmin);
    }

    private void showUserPanelAfterGoogleLogin() {
        setPanelVisibility(false, true, false);
    }

    private void disableGoogleSignIn() {
        googleUserSession = null;
        signInButton.setVisible(false);
        signInButton.setManaged(false);
    }

    private void showAdminLoginStep() {
        adminLoginPanel.setVisible(true);
        adminLoginPanel.setManaged(true);
        adminUploadPanel.setVisible(false);
        adminUploadPanel.setManaged(false);
    }

    private void showAdminUploadStep() {
        adminLoginPanel.setVisible(false);
        adminLoginPanel.setManaged(false);
        adminUploadPanel.setVisible(true);
        adminUploadPanel.setManaged(true);
    }

    private File selectFile(String title) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        return fileChooser.showOpenDialog(new Stage());
    }

    private String calculateFileHash(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] content = Files.readAllBytes(file.toPath());
            byte[] hashed = digest.digest(content);
            StringBuilder builder = new StringBuilder();
            for (byte item : hashed) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private String fileName(File file) {
        return file == null ? "Not selected" : file.getName();
    }

    private void showAdminLoginErrorWindow() {
        showResultWindow("Login Error", "X", "Incorrect username or password.", "#b91c1c", "#fff7f7");
    }

    private void showResultWindow(String title, String mark, String message, String color, String backgroundColor) {
        Alert alert = new Alert(Alert.AlertType.NONE, "", ButtonType.OK);
        alert.initModality(Modality.APPLICATION_MODAL);
        alert.setTitle(title);
        alert.setHeaderText(null);

        Label markLabel = new Label(mark);
        markLabel.setStyle("-fx-font-size: 34px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");

        Label messageLabel = new Label(message);
        messageLabel.setWrapText(true);
        messageLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox content = new HBox(14, markLabel, messageLabel, spacer);

        alert.getDialogPane().setContent(content);
        alert.getDialogPane().setMinWidth(460);
        alert.getDialogPane().setStyle("-fx-background-color: " + backgroundColor + ";");
        alert.showAndWait();
    }

    private void updateUserStatus(String message) { statusArea.setText(message); }
    private void updateAdminLoginStatus(String message) { adminStatusArea.setText(message); }
    private void updateAdminUploadStatus(String message) { adminUploadStatusArea.setText(message); }

    private void showEmailInput() {
        emailLabel.setText("Work email *");
        emailLabel.setVisible(true);
        emailLabel.setManaged(true);
        filePathField.setVisible(true);
        filePathField.setManaged(true);
        filePathField.setDisable(false);
        filePathField.setEditable(true);
        filePathField.setPromptText("you@company.com");
        filePathField.clear();
    }

    private void applyEnglishTexts() {
        emailLabel.setText("Work email *");
        filePathField.setPromptText("you@company.com");
        heroTitleLabel.setText("Verify document authenticity");
        heroSubtitleLabel.setText("Choose a role first. Visitors can either continue as a user to verify documents or as an admin to upload trusted reference files.");
        choiceTitleLabel.setText("Choose how to enter");
        choiceSubtitleLabel.setText("Start by selecting whether you want the user verification path or the admin login path.");
        chooseUserButton.setText("User log in");
        chooseAdminButton.setText("Admin log in");

        userBackButton.setText("Back");
        cardTitleLabel.setText("User verification");
        cardSubtitleLabel.setText("Upload a main document and an optional reference attachment. The app always hashes the main document and compares it with the latest admin reference for the selected document type.");
        docTypeLabel.setText("Document type *");
        filesLabel.setText("Files for verification");
        filesHintLabel.setText("Choose the main document and, if available, the optional reference attachment.");
        documentFileLabel.setText("Document file *");
        documentDropLabel.setText("Select the user document");
        selectDocumentButton.setText("Select document");
        documentHintLabel.setText("This file will be hashed and compared against the admin reference.");
        certificateFileLabel.setText("Reference attachment");
        certificateDropLabel.setText("Select the optional attachment");
        selectCertificateButton.setText("Select attachment");
        certificateHintLabel.setText("If submitted, this file hash is also stored and compared when an admin attachment exists.");
        continueButton.setText("Compare and save");

        adminBackButton.setText("Back");
        adminSectionTitleLabel.setText("Admin log in");
        adminSectionSubtitleLabel.setText("Enter your admin credentials first. After a successful login, the upload page will open.");
        adminUsernameLabel.setText("Admin username *");
        adminUsernameField.setPromptText("Arman");
        adminPasswordLabel.setText("Password *");
        adminPasswordField.setPromptText("Enter password");
        adminLoginButton.setText("Log in");
        adminUploadTitleLabel.setText("Upload trusted reference files");
        adminUploadSubtitleLabel.setText("You are logged in. Every uploaded main document is saved in SQLite with SHA-256. The reference attachment is optional.");
        totalDocumentsLabel.setText("Total documents");
        verifiedUsersLabel.setText("Verified users");
        documentsListLabel.setText("Uploaded document list");
        adminDocTypeLabel.setText("Reference type *");
        adminDocumentFileLabel.setText("Reference document *");
        adminDocumentDropLabel.setText("Select the trusted document");
        selectAdminDocumentButton.setText("Select document");
        adminCertificateFileLabel.setText("Reference attachment");
        adminCertificateDropLabel.setText("Select the optional trusted attachment");
        selectAdminCertificateButton.setText("Select attachment");
        adminUploadButton.setText("Upload reference");
        deleteDocumentButton.setText("Delete selected");
        adminLogoutButton.setText("Log out");
    }

    private void applyBanglaTexts() {
        applyEnglishTexts();
        heroTitleLabel.setText("\u09A1\u0995\u09C1\u09AE\u09C7\u09A8\u09CD\u099F \u09AF\u09BE\u099A\u09BE\u0987 \u0993 \u09B0\u09CB\u09B2 \u09A8\u09BF\u09B0\u09CD\u09AC\u09BE\u099A\u09A8");
        choiceTitleLabel.setText("\u09AA\u09CD\u09B0\u09A5\u09AE\u09C7 \u09AA\u09A5 \u09A8\u09BF\u09B0\u09CD\u09AC\u09BE\u099A\u09A8 \u0995\u09B0\u09C1\u09A8");
        chooseUserButton.setText("\u0987\u0989\u099C\u09BE\u09B0 \u09B2\u0997 \u0987\u09A8");
        chooseAdminButton.setText("\u098F\u09A1\u09AE\u09BF\u09A8 \u09B2\u0997 \u0987\u09A8");
        cardTitleLabel.setText("\u0987\u0989\u099C\u09BE\u09B0 \u09AF\u09BE\u099A\u09BE\u0987");
        adminSectionTitleLabel.setText("\u098F\u09A1\u09AE\u09BF\u09A8 \u09B2\u0997 \u0987\u09A8");
        adminUploadTitleLabel.setText("\u09AC\u09BF\u09B6\u09CD\u09AC\u09B8\u09CD\u09A4 \u09B0\u09C7\u09AB\u09BE\u09B0\u09C7\u09A8\u09CD\u09B8 \u0986\u09AA\u09B2\u09CB\u09A1");
        continueButton.setText("\u09A4\u09C1\u09B2\u09A8\u09BE \u0993 \u09B8\u09C7\u09AD");
        adminLoginButton.setText("\u09B2\u0997 \u0987\u09A8");
        adminUploadButton.setText("\u09B0\u09C7\u09AB\u09BE\u09B0\u09C7\u09A8\u09CD\u09B8 \u0986\u09AA\u09B2\u09CB\u09A1");
        adminLogoutButton.setText("\u09B2\u0997 \u0986\u0989\u099F");
        userBackButton.setText("\u09AB\u09BF\u09B0\u09C7 \u09AF\u09BE\u09A8");
        adminBackButton.setText("\u09AB\u09BF\u09B0\u09C7 \u09AF\u09BE\u09A8");
    }
}
