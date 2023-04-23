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

import java.io.*;
import java.net.Socket;

public class ChatClient extends Application {

    public String name;
    private Socket socket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private TextArea messageArea;
    private TextArea messageInput;
    private Button sendButton;

    private TextArea groupMessages;
    private ScrollPane groupSP;
    private TextArea groupmessageInput;
    private Button groupsendButton;

    private Stage createNewRoomStage;

    public void start(Stage primaryStage) throws Exception {
        // 建立socket连接
        socket = new Socket("localhost", 8000);
        outputStream = socket.getOutputStream();
        inputStream = socket.getInputStream();

        // 创建界面
        BorderPane root = new BorderPane();
        // 创建菜单栏
        MenuBar menuBar = new MenuBar();
        root.setTop(menuBar);

        // 创建File菜单
        Menu fileMenu = new Menu("New Chat");

        // 创建一个新的场景
        Scene createNewRoom = new Scene(new VBox(), 600, 100);

        // 在VBox中添加一个Label和StackPane
        Label label = new Label("First enter the room name, then enter the members you want to add, split the items by space:");
        StackPane stackPane = new StackPane();
        VBox.setMargin(label, new Insets(10, 0, 0, 0));
        VBox.setVgrow(stackPane, Priority.ALWAYS);
        VBox rootVbox = (VBox) createNewRoom.getRoot();
        rootVbox.getChildren().addAll(label, stackPane);

        // 创建New菜单项并添加到File菜单中
        MenuItem privateChat = new MenuItem("Private Chat");
        fileMenu.getItems().add(privateChat);
        privateChat.setOnAction(event -> {
            createNewRoomStage = new Stage();
            createNewRoomStage.setScene(createNewRoom);
            createNewRoomStage.show();
        });

        // 在场景中添加一个文本输入框和一个按钮
        TextField memberlist = new TextField();
        Button submit = new Button("Submit");
        VBox vBox = (VBox) createNewRoom.getRoot();
        vBox.getChildren().addAll(memberlist, submit);
        submit.setOnAction(event -> {
            String members = memberlist.getText();
            if (!members.isEmpty()) {
                sendRoomCreation(members);
                createNewRoomStage.close();
            }
        });


        // 创建Open菜单项并添加到File菜单中
        MenuItem groupChat = new MenuItem("Group Chat");
        fileMenu.getItems().add(groupChat);
        // 将File菜单添加到菜单栏中
        menuBar.getMenus().add(fileMenu);


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
        ObservableList<String> users = FXCollections.observableArrayList("User 1", "User 2", "User 3");
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
                    if (message.startsWith("MESSAGE:")) {
                        Platform.runLater(() -> messageArea.appendText(message.substring(8)));
                    } else if (message.startsWith("ServerHint:")) {
                        Platform.runLater(() -> messageArea.appendText(message.substring(11)));
                        if (message.endsWith("!\n")) {
                            name = message.substring(37, message.length() - 2);
                        }
                    } else if (message.startsWith("USERLIST:")) {
                        String[] userlist = message.substring(9).split(",");
                        Platform.runLater(() -> {
                            ObservableList<String> userList = FXCollections.observableArrayList(userlist);
                            usersList.setItems(userList);
                        });
                    } else if (message.startsWith("NewRoomCreated:")) {
                        String[] roomInfo = message.substring(15).split(" ");

                        Platform.runLater(() -> {
                            Stage NewRoomStage = new Stage();
                            NewRoomStage.setTitle(roomInfo[0]);
                            VBox grouproot = new VBox();
                            // 创建一个新的场景
                            Scene NewRoomScene = new Scene(grouproot, 500, 500);
                            groupMessages = new TextArea();
                            groupMessages.setEditable(false);
                            groupSP = new ScrollPane();
                            groupSP.setContent(groupMessages);
                            groupSP.setFitToWidth(true);
                            groupSP.setPrefViewportHeight(400);
                            grouproot.getChildren().add(groupSP);

                            VBox groupinputBox = new VBox();
                            groupinputBox.setPadding(new Insets(10));
                            groupinputBox.setSpacing(10);
                            groupmessageInput = new TextArea();
                            groupmessageInput.setWrapText(true); // 开启自动换行
                            groupmessageInput.setEditable(true);
                            groupmessageInput.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                                if (event.getCode() == KeyCode.ENTER && event.isShiftDown()) { // 判断是否按下Shift键
                                    event.consume(); // 阻止Enter键的默认行为（即换行）
                                    groupsendMessage(groupmessageInput.getText(), roomInfo[0]); // 发送消息
                                }
                            });
                            groupsendButton = new Button("Send");
                            groupsendButton.setOnAction(event -> {
                                groupsendMessage(groupmessageInput.getText(), roomInfo[0]);
                            });
                            groupinputBox.getChildren().addAll(groupmessageInput, groupsendButton);

                            grouproot.getChildren().add(groupinputBox);

                            NewRoomStage.setScene(NewRoomScene);
                            NewRoomStage.show();

                            startGroupChatThread(socket, roomInfo[0], groupMessages, groupsendButton, groupmessageInput);
                        });


                    }

                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    socket.close();
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
        primaryStage.setTitle("Chat Client");
        primaryStage.show();
    }

    private void sendMessage() {
        String message = messageInput.getText().trim();
        if (!message.isEmpty()) {
            try {
                outputStream.write(("MESSAGE:" + message + "\n").getBytes());
                outputStream.flush();
                messageInput.setText("");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void groupsendMessage(String message, String group) {
        if (!message.isEmpty()) {

            try {
                outputStream.write(("GROUPMESSAGE:" + group + "splitcode#" + name + ":" + message + "\n").getBytes());
                outputStream.flush();
                groupmessageInput.setText("");
                System.out.printf("message %s sent to group %s\n", message, group);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void sendRoomCreation(String members) {
        try {
            outputStream.write(("NewRoom:" + members + "\n").getBytes());
            outputStream.flush();
            messageInput.setText("");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startGroupChatThread(Socket socket, String roomName, TextArea groupMessages, Button groupSendButton, TextArea groupMessageInput) {
        Thread receiveThread = new Thread(() -> {
            try {
                byte[] buffer = new byte[1024];
                while (true) {
                    int len = inputStream.read(buffer);
                    if (len == -1) {
                        break;
                    }
                    String message = new String(buffer, 0, len);
                    if (message.startsWith("GROUP:")) {
                        Platform.runLater(() -> {
                            groupMessages.appendText(message.substring(6));
                        });
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Platform.runLater(() -> {
                    groupMessages.appendText("Disconnected from server.\n");
                    groupSendButton.setDisable(true);
                    groupMessageInput.setDisable(true);
                });
            }
        });
        receiveThread.setDaemon(true);
        receiveThread.start();
    }


    public void stop() throws Exception {
        super.stop();
        socket.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
