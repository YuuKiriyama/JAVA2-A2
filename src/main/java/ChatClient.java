import java.io.*;
import java.net.Socket;
import java.net.SocketException;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;


public class ChatClient extends Application {

    public String name = "";
    private Socket socket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private TextArea messageArea;
    private TextArea messageInput;
    private Button sendButton;

    public void start(Stage primaryStage) throws Exception {
        // 建立socket连接
        socket = new Socket("localhost", 8000);
        outputStream = socket.getOutputStream();
        inputStream = socket.getInputStream();

        // 创建界面
        BorderPane root = new BorderPane();


        //创建一个SplitPane并添加到左边
        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(Orientation.VERTICAL);
        splitPane.setPrefWidth(200);
        root.setLeft(splitPane);
        //在SplitPane中添加一个VBox来放置其他组件
        VBox leftBox = new VBox();
        leftBox.setPadding(new Insets(10));
        leftBox.setSpacing(10);
        splitPane.getItems().add(leftBox);
        //添加用户列表到leftBox中
        Label usersLabel = new Label("Online Users:");
        ListView<String> usersList = new ListView<>();
        ObservableList<String> users = FXCollections.observableArrayList("");
        usersList.setItems(users);
        leftBox.getChildren().addAll(usersLabel, usersList);


        messageArea = new TextArea();
        messageArea.setEditable(false);
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setContent(messageArea);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(400);
        root.setCenter(scrollPane);


        VBox inputBox = new VBox();
        inputBox.setPadding(new Insets(10));
        inputBox.setSpacing(10);
        messageInput = new TextArea();
        messageInput.setWrapText(true); // 开启自动换行
        messageInput.setEditable(true);
        messageInput.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER && event.isShiftDown()) { // 判断是否按下Shift键
                event.consume(); // 阻止Enter键的默认行为（即换行）
                sendMessage(); // 发送消息
            }
        });
        sendButton = new Button("Send");
        sendButton.setOnAction(event -> {
            sendMessage();
        });
        inputBox.getChildren().addAll(messageInput, sendButton);
        root.setBottom(inputBox);

        // 启动接收消息线程
        Thread receiveThread = new Thread(() -> {
            try {
                byte[] buffer = new byte[1024];
                while (true) {
                    int len = inputStream.read(buffer);
                    if (len == -1) {
                        break;
                    }
                    String message = new String(buffer, 0, len);
                    if (!message.startsWith("USERLIST:"))
                        System.out.println("client received:" + message);


                    if (message.startsWith("Broadcast:")) {
                        Platform.runLater(() -> messageArea.appendText(message.substring(10)));
                    } else if (message.startsWith("ServerHint:")) {
                        Platform.runLater(() -> {
                            messageArea.appendText(message.substring(11));
                            primaryStage.setTitle("Chat Client: " + name);
                        });
                        if (message.endsWith("!\n")) {
                            name = message.substring(37, message.length() - 2);
                        }
                    } else if (message.startsWith("USERLIST:")) {
                        String[] userlist = message.substring(9).split(",");
                        Platform.runLater(() -> {
                            ObservableList<String> userList = FXCollections.observableArrayList(userlist);
                            usersList.setItems(userList);
                        });
                    } else if (message.startsWith("Room:")) {
                        String[] members_message = new String[2];
                        String received_message = message.substring(5);
                        for (int i = 0; i < received_message.length(); i++) {
                            if (received_message.charAt(i) == '/') {
                                members_message[0] = received_message.substring(0, i);
                                members_message[1] = received_message.substring(i + 1);
                            }
                        }
                        StringBuilder members = new StringBuilder();
                        String[] mem = members_message[0].split(" ");
                        for (int i = 1; i < mem.length; i++) {
                            members.append(mem[i]).append(",");
                        }
                        String out = members.toString();
                        String finalOut = out.substring(0, out.length() - 1);
                        Platform.runLater(() -> {
                            messageArea.setText("");
                            messageArea.appendText("Room: " + mem[0] + "\nMembers:" + finalOut + "\n" + members_message[1]);
                        });
                    }

                }
            } catch (IOException e) {
                if (socket != null && !socket.isClosed()) {
                    e.printStackTrace();
                }
            } finally {
                try {
                    if (socket != null && !socket.isClosed()) {
                        socket.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Platform.runLater(() -> {
                    messageArea.appendText("Disconnected from server.\n");
                    sendButton.setDisable(true);
                    messageInput.setDisable(true);
                });
            }
        });
        receiveThread.setDaemon(true);
        receiveThread.start();


        // 显示界面
        primaryStage.setScene(new Scene(root, 600, 500));
        primaryStage.show();
    }

    private void sendMessage() {
        String message = messageInput.getText().trim();
        if (!message.isEmpty()) {
            try {
                System.out.println("client sent:" + (name + ":" + message + "\n"));
                outputStream.write((name + ":" + message + "\n").getBytes());
                outputStream.flush();
                messageInput.setText("");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void stop() throws Exception {
        super.stop();
        socket.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
