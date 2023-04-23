import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ChatServer {
    public List<ClientThread> clients = new ArrayList<>();
    private ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public List<Room> roomList = new ArrayList<>();

    public ChatServer() {
        executorService.scheduleAtFixedRate(this::broadcastUserList, 0, 2, TimeUnit.SECONDS);
    }

    public void start(int port) {
        try {
            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("Chat server started on port " + port);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientThread clientThread = new ClientThread(clientSocket, this);
                clients.add(clientThread);
                clientThread.start();
                System.out.println("New client connected: " + clientSocket.getInetAddress());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void broadcast(String message, ClientThread sender) throws IOException {
        for (ClientThread client : clients) {
            client.sendMessage(message);
        }
    }

    private void broadcastUserList() {
        try {
            StringBuilder userList = new StringBuilder();
            for (ClientThread client : clients) {
                userList.append(client.getClientName()).append(",");
            }
            String userListMessage = userList.toString();
            if (userListMessage.length() > 0) {
                userListMessage = userListMessage.substring(0, userListMessage.length() - 1);
            }
            broadcast("USERLIST:" + userListMessage, null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public synchronized void removeClient(ClientThread client) {
        clients.remove(client);
    }

    public void sendtoRoom(String info, String user) throws IOException {
        String[] roomInfo_messages = new String[2];
        for (int i = 0; i < info.length(); i++) {
            if (info.charAt(i) == ':') {
                roomInfo_messages[0] = info.substring(0, i);
                roomInfo_messages[1] = info.substring(i + 1);
                break;
            }
        }
        String[] roomInfo = roomInfo_messages[0].split(" ");//roomname a b ...
        for (Room r : roomList) {
            if (r.roomName.equals(roomInfo[0])) {
                //找到了创建过的room
                r.messages.add(user + ":" + roomInfo_messages[1]);
                for (ClientThread c : r.members) {
                    if (clients.contains(c)) {
                        c.sendMessage("Room:" + r.getRoomInfo());
                    }
                }
                return;
            }
        }
        //新建room
        Room room = new Room(roomInfo[0]);
        room.messages.add(user + ":" + roomInfo_messages[1]);

        for (ClientThread c : clients) {
            for (String s : roomInfo) {
                if (c.getClientName().equals(s)) {
                    room.members.add(c);
                }
            }
        }
        roomList.add(room);
        //给Room内所有人发送消息
        for (ClientThread c : room.members) {
            c.sendMessage("Room:" + room.getRoomInfo());
        }

    }


    public void findRoom(String roomName, String user) throws IOException {
        for (Room r : roomList) {
            if (r.roomName.equals(roomName)) {
                //找到了创建过的room
                for (ClientThread c : r.members) {
                    if (c.getClientName().equals(user)) {
                        c.sendMessage("Room:" + r.getRoomInfo());
                    }
                }
                return;
            }
        }

    }

    public static void main(String[] args) {
        ChatServer server = new ChatServer();
        server.start(8000);
    }
}

class ClientThread extends Thread {
    private Socket socket;
    private ChatServer server;
    private String name;

    public String getClientName() {
        if (this.name != null) {
            return this.name;
        }
        return "";
    }

    public ClientThread(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    private boolean name_dup(String name) {
        for (ClientThread namelist : server.clients) {
            if (namelist.name != null) {
                if (namelist.name.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void run() {
        try {
            sendMessage("ServerHint:Enter your name:");
            String tmpname = receiveMessage().substring(1);
            while (tmpname.contains("\n") || tmpname.contains(" ") || name_dup(tmpname)) {
                sendMessage("ServerHint:Invalid name! Enter another name:");
                tmpname = receiveMessage().substring(1);
            }
            name = tmpname;
            sendMessage("ServerHint:Welcome to the chat room, " + name + "!");
            server.broadcast("ServerHint:" + name + " has joined the chat room.", this);

            while (true) {
                String message = receiveMessage();
                if (!message.isEmpty()) {
                    System.out.println("server received:" + message);
                    String[] user_message = new String[2];
                    for (int i = 0; i < message.length(); i++) {
                        if (message.charAt(i) == ':') {
                            user_message[0] = message.substring(0, i);
                            user_message[1] = message.substring(i + 1);
                            break;
                        }
                    }
                    if (user_message[1].startsWith("Send to:")) {
                        server.sendtoRoom(user_message[1].substring(8), user_message[0]);
                    } else if (user_message[1].startsWith("Broadcast:")) {
                        server.broadcast("Broadcast:" + user_message[0] + ":"
                                + user_message[1].substring(10), this);
                    } else if (user_message[1].startsWith("Find:")) {
                        server.findRoom(user_message[1].substring(5), user_message[0]);
                    }
                } else {
                    throw new RuntimeException();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (StringIndexOutOfBoundsException e) {
            System.out.println("发生甚么事了");
        } catch (RuntimeException e) {
            System.out.println("又发生甚么事了");
        } finally {
            server.removeClient(this);
            try {
                server.broadcast("ServerHint:" + name + " has left the chat room.", this);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public void sendMessage(String message) throws IOException {
        if (!message.startsWith("USERLIST:"))
            System.out.println("server sent:" + message);
        socket.getOutputStream().write((message + "\n").getBytes());
    }


    public String receiveMessage() throws IOException {
        byte[] buffer = new byte[1024];
        if (socket != null) {
            int len = socket.getInputStream().read(buffer);
            if (len == -1) {
                return "";
            }
            return new String(buffer, 0, len).trim();
        } else {
            return "";
        }

    }

}

class Room {
    public String roomName;
    public List<ClientThread> members;
    public List<String> messages;

    public Room(String roomName) {
        this.roomName = roomName;
        this.members = new ArrayList<>();
        this.messages = new ArrayList<>();
    }

    public String getRoomInfo() {
        return roomName + " " + getRoomMembers() + "/" + getRoomMessages();
    }

    public String getRoomMessages() {
        StringBuilder out = new StringBuilder();
        for (String m : messages) {
            out.append(m).append("\n");
        }
        if (messages.size() > 0) {
            String output = out.substring(0, out.length() - 1);
            return output;
        } else {
            return "";
        }
    }

    public String getRoomMembers() {
        StringBuilder out = new StringBuilder();
        for (ClientThread m : members) {
            out.append(m.getClientName()).append(" ");
        }
        if (messages.size() > 0) {
            String output = out.substring(0, out.length() - 1);
            return output;
        } else {
            return "";
        }
    }
}