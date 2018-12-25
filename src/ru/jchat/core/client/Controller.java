package ru.jchat.core.client;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.util.Callback;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.URL;
import java.util.Objects;
import java.util.ResourceBundle;

public class Controller implements Initializable {
    @FXML
    TextArea textArea;
    @FXML
    TextField msgField;
    @FXML
    HBox authPanel;
    @FXML
    HBox msgPanel;
    @FXML
    TextField loginField;
    @FXML
    PasswordField passField;
    @FXML
    ListView<String> clientsListView;

    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;

    final String SERVER_IP = "localhost";
    final int SERVER_PORT = 8189;

    private boolean authorized;
    private String myNick;

    private ObservableList<String> clientsList;


    private void setAuthorized(boolean authorized) {
        this.authorized = authorized;
        if (authorized) {
            msgPanel.setVisible(true);
            msgPanel.setManaged(true);

            // Clear chat area from
            textArea.clear();
            // Welcome message
            textArea.appendText("Welcome to JChat!\n");

            authPanel.setVisible(false);
            authPanel.setManaged(false);
            clientsListView.setVisible(true);
            clientsListView.setManaged(true);
        } else {
            msgPanel.setVisible(false);
            msgPanel.setManaged(false);
            authPanel.setVisible(true);
            authPanel.setManaged(true);
            clientsListView.setVisible(false);
            clientsListView.setManaged(false);
            myNick = "";
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setAuthorized(false);
    }

    private void connect() {
        long timerStart = System.currentTimeMillis();
        try {
            socket = new Socket(SERVER_IP, SERVER_PORT);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            clientsList = FXCollections.observableArrayList();
            clientsListView.setItems(clientsList);

            clientsListView.setCellFactory(new Callback<ListView<String>, ListCell<String>>() {
                @Override
                public ListCell<String> call(ListView<String> param) {
                    return new ListCell<String>() {
                        @Override
                        protected void updateItem(String item, boolean empty) {
                            super.updateItem(item, empty);
                            if (!empty) {
                                setText(item);
                                if (item.equals(myNick)) {
                                    setStyle("-fx-font-weight: bold; -fx-background-color: lightcyan");
                                }
                            } else {
                                setGraphic(null);
                                setText(null);
                            }
                        }
                    };
                }
            });

            // Disconnect unautorized users from server
            Thread t2 = new Thread(() -> {
                while (!authorized) {
                    if ((System.currentTimeMillis() - timerStart) > 120000) {
                        textArea.appendText("Autorization timeout\n");
                        try {
                            socket.close();
                        } catch (IOException e) {
//                        e.printStackTrace();
                        }
                        break;
                    }
                }
            });


            Thread t = new Thread(() -> {
                try {
                    while (true) {
                        String s = null;
                        s = in.readUTF();
                        if (s.startsWith("/authok ")) {
                            setAuthorized(true);
                            myNick = s.split("\\s")[1];
                            break;
                        } else {
                            textArea.appendText(s + "\n");
                        }
                    }

                    while (true) {
                        String s = in.readUTF();
                        if (s.startsWith("/")) {
                            if (s.startsWith("/clientslist ")) {
                                String[] data = s.split("\\s");
                                Platform.runLater(() -> {
                                    clientsList.clear();
                                    for (int i = 1; i < data.length; i++) {
                                        clientsList.addAll(data[i]);
                                    }
                                });
                            }
                        } else {
                            textArea.appendText(s + "\n");
                        }
                    }
                } catch (IOException e) {
                    showAlert("Information", "Connection to server was lost");
                } finally {
                    setAuthorized(false);
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            t.setDaemon(true);
            t2.setDaemon(true);
            t.start();
            t2.start();
        } catch (IOException e) {
            showAlert("Warning", "Connection to server failed");
        }
    }

    public void sendAuthMsg() {

        if (loginField.getText().isEmpty() || passField.getText().isEmpty()) {
            showAlert("Error", "Incomplete authorization data");
            return;
        }
        if (socket == null || socket.isClosed()) {
            connect();
        }
        // /auth login pass
        try {
            out.writeUTF("/auth " + loginField.getText() + " " + passField.getText());
            loginField.clear();
            passField.clear();
        } catch (Exception e) {
            showAlert("Warning", "Connection to server failed");
        }
    }

    public void sendMsg() {
        try {
            out.writeUTF(msgField.getText());
            msgField.clear();
            msgField.requestFocus();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showAlert(String severity, String msg) {
        Platform.runLater(() -> {
            Alert alert = null;
            switch (severity) {
                case "Error": {
                    alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle(severity + " message");
                    break;
                }
                case "Warning": {
                    alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle(severity + " message");
                    break;
                }
                case "Information": {
                    alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle(severity + " message");
                    break;
                }
            }
            Objects.requireNonNull(alert).setHeaderText(null);
            alert.setContentText(msg);
            alert.showAndWait();
        });
    }


    public void clientsListClicked(MouseEvent mouseEvent) {
        if (mouseEvent.getClickCount() == 2) {
            msgField.setText("/w " + clientsListView.getSelectionModel().getSelectedItem() + " ");
            msgField.requestFocus();
            msgField.selectEnd();
        }
    }
}
