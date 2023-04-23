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
        executorService.scheduleAtFixedRate(this::broadcastUserList, 0, 1, TimeUnit.SECONDS);
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

    public void createRoom(String members) throws IOException {
        String[] roomInfo = members.split(" ");
        for (Room r : roomList) {
            if (r.roomName.equals(roomInfo[0])) {
                //找到了创建过的room
                int j = 1;
                for (ClientThread c : clients) {
                    if (c.getClientName().equals(roomInfo[j])) {
                        j++;
                        c.sendMessage("NewRoomCreated:" + members + " " + r.getRoomMessages());
                        if (j>roomInfo.length-1){
                            break;
                        }
                    }
                }
                return;
            }
        }

        Room room = new Room(roomInfo[0]);
        int i = 1;
        for (ClientThread c : clients) {
            if (c.getClientName().equals(roomInfo[i])) {
                i++;
                room.members.add(c);
            }
            if (i>roomInfo.length-1) {
                break;
            }
        }
        int k = 1;
        for (ClientThread c : clients) {
            if (c.getClientName().equals(roomInfo[k])) {
                k++;
                c.sendMessage("NewRoomCreated:" + members + " " + room.getRoomMessages());
                System.out.println("NewRoomCreated:" + members + " " + room.getRoomMessages());
                if (k>roomInfo.length-1){
                    break;
                }
            }
        }
        roomList.add(room);

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
            String tmpname = receiveMessage().substring(8);
            while (tmpname.contains("\n") || tmpname.contains(" ") || name_dup(tmpname)) {
                sendMessage("ServerHint:Invalid name! Enter another name:");
                tmpname = receiveMessage().substring(8);
            }
            name = tmpname;
            sendMessage("ServerHint:Welcome to the chat room, " + name + "!");
            server.broadcast(name + " has joined the chat room.", this);

            while (true) {
                String message = receiveMessage();
                if (message == null) {
                    break;
                } else if (message.startsWith("MESSAGE:")) {
                    server.broadcast("MESSAGE:" + name + ": " + message.substring(8), this);
                } else if (message.startsWith("NewRoom:")) {
                    server.createRoom(message.substring(8));
                } else if (message.startsWith("GROUPMESSAGE:")) {
                    System.out.printf("Group message %s received\n", message);
                    String[] groupMessageInfo = message.substring(13).split("splitcode#");
                    System.out.printf("Group:%s,message:%s\n", groupMessageInfo[0], groupMessageInfo[1]);
                    groupbroadcast(groupMessageInfo[1], groupMessageInfo[0]);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            server.removeClient(this);
            try {
                server.broadcast(name + " has left the chat room.", this);
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

    public void groupbroadcast(String message, String group) throws IOException {
        for (Room r : server.roomList) {
            if (r.roomName.equals(group)) {
                r.messages.add(message);
                for (ClientThread c : r.members) {
                    c.sendMessage("GROUP:" + group + ":" + message);
                }

            }
        }
    }

    public void sendMessage(String message) throws IOException {
        socket.getOutputStream().write((message + "\n").getBytes());
    }


    public String receiveMessage() throws IOException {
        byte[] buffer = new byte[1024];
        int len = socket.getInputStream().read(buffer);
        if (len == -1) {
            return null;
        }
        return new String(buffer, 0, len).trim();
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

    public String getRoomMessages() {
        StringBuilder out = new StringBuilder();
        for (String m : messages) {
            out.append(m).append(",");
        }
        if (messages.size() > 0) {
            String output = out.substring(0, out.length() - 1);
            return output;
        } else return "";

    }
}