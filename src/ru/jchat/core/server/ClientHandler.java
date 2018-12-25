package ru.jchat.core.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

class ClientHandler {
    private Server server;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private String nick;

    String getNick() {
        return nick;
    }

    ClientHandler(Server server, Socket socket) {
        try {
            this.socket = socket;
            this.in = new DataInputStream(socket.getInputStream());
            this.out = new DataOutputStream(socket.getOutputStream());
            new Thread(() -> {
                try {
                    while (true) {
                        String msg = in.readUTF();
                        if (msg.startsWith("/auth ")) {
                            String[] data = msg.split("\\s");
                            if (data.length == 3) {
                                String newNick = server.getAuthService().getNickByLoginAndPass(data[1], data[2]);
                                if (newNick != null) {
                                    if (!server.isNickBusy(newNick)) {
                                        nick = newNick;
                                        sendMsg("/authok " + newNick);
                                        server.subscribe(this);
                                        break;
                                    } else {
                                        sendMsg(newNick + " already in chat");
                                    }
                                } else {
                                    sendMsg("Incorrect login/password");
                                }
                            }
                        }
                    }
                    while (true) {
                        String msg = in.readUTF();
                        System.out.println(nick + ": " + msg);
                        if (msg.startsWith("/")){
                            if (msg.equals("/end")) break;
                            if (msg.startsWith("/w ")){
                                String[] data = msg.split("\\s", 3);
                                server.sendPrivateMsg(this, data[1], data[2]);
                            }
                        } else {
                            server.broadcastMsg(nick + ": " + msg);
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Connection reset by user");
//                    e.printStackTrace();
                } finally {
                    server.unsubscribe(this);
//                    server.broadcastMsg(nick + " left chat");
                    nick = null;
                    try {
                        socket.close();
                    } catch (IOException e) {
                        System.out.println("Trying to close socket...");
                    }
                }
            }).start();

        } catch (IOException e) {
            System.out.println("ClientHandler IOException");
        }
    }

    void sendMsg(String msg) {
        try {
            out.writeUTF(msg);
        } catch (IOException e) {
            System.out.println("Failed to send message");
        }
    }
}
