package com.sudhar.crazeremote.connectivity;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.sudhar.crazeremote.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class TCPServer {

    public interface TCPConListener {
        void onStart(String host, int port);

        void onConnect(String host, int port, ClientID clientID);

        void onDisconnect( ClientID clientID);

        void onData(ClientID clientID, byte[] data);

        void onError(String error);
        void onStop();
    }

    TCPConListener tcpConListener;

    private static final String TAG = "TCPServer";


    int mPort;
    ServerSocket serverSocket;

    public static final char ID_REQ_PREAMBLE = '?';

    private HandlerThread handlerThread;
    private Handler threadHandler;
    private ClientID clientID;
    String handlerName = "Main";
    boolean isListening = false;
    Map<ClientID, Socket> clients = new HashMap<ClientID, Socket>();
    boolean useLocal=true;
    public Map<ClientID, Socket> getClients() {
        return clients;
    }

    public TCPServer(int mPort, String handlerName) {

        this.mPort = mPort;
        this.handlerName = handlerName;

    }


    public boolean isListening() {
        return isListening;
    }

    public void setListening(boolean listening) {
        isListening = listening;
    }


    public void setUseLocal(boolean useLocal) {
        this.useLocal = useLocal;
    }

    public void start() {
        setListening(true);
        handlerThread = new HandlerThread(handlerName);
        handlerThread.start();
        threadHandler = new Handler(handlerThread.getLooper());
        threadHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket();
                    if(useLocal){
                        serverSocket.bind(new InetSocketAddress(Utils.getLocalIpAddress(), mPort));
                        Log.d(TAG, "run: " + serverSocket.getInetAddress().toString());
                        tcpConListener.onStart(serverSocket.getInetAddress().toString(), serverSocket.getLocalPort());
                    }else {
                        serverSocket.bind(new InetSocketAddress( mPort));
                        tcpConListener.onStart(serverSocket.getLocalSocketAddress().toString(), serverSocket.getLocalPort());
                    }



                    listen();

                } catch (IOException e) {
                    e.printStackTrace();
                    tcpConListener.onError(e.getMessage());
                }

            }
        });

    }

    void listen() {

        Runnable listenerRunnable = new Runnable() {
            @Override
            public void run() {
                while (isListening && !serverSocket.isClosed()) {
                    try {
                        Socket socket = serverSocket.accept();
                        if (socket != null) {
                            newClient(socket);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();


                    }
                }

            }
        };
        Thread list = new Thread(listenerRunnable);
        list.start();
    }

    public void newClient(Socket socket) {
        Thread receiverThread = new Thread(new ReceiverRunnable(socket));
        receiverThread.start();
        try {
            socket.getOutputStream().write((byte) ID_REQ_PREAMBLE);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "run: " + socket.getInetAddress().getHostAddress());
    }


    public void stop() {

        setListening(false);
        for (Map.Entry<ClientID, Socket> entrySet : clients.entrySet()) {
            Socket socket = entrySet.getValue();
            ClientID clientID = entrySet.getKey();
            if (socket.isConnected() && !socket.isClosed()) {
                try {
                    socket.close();
                    tcpConListener.onDisconnect(clientID);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                tcpConListener.onStop();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        clients.clear();

    }


    public void sendBytes(ClientID clientID, byte[] myByteArray) {
        sendBytes(clientID, myByteArray, 0, myByteArray.length);
    }

    public void sendBytes(ClientID clientID, final byte[] myByteArray, final int start, final int len) {

        threadHandler.post(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "run: hello");
                try {
                    Socket socket = clients.get(clientID);
                    if(socket!=null){
                        OutputStream outputStream = socket.getOutputStream();
                        outputStream.write(myByteArray, start, len);
                    }



                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }


    public class ReceiverRunnable implements Runnable {

        Socket socket;
        InputStream inputStream;


        public ReceiverRunnable(Socket socket) {
            this.socket = socket;
            try {
                this.inputStream = socket.getInputStream();
            } catch (IOException e) {
                e.printStackTrace();
                tcpConListener.onError(e.getMessage());
            }
        }

        @Override
        public void run() {
            byte[] buffer;
            while (socket.isConnected()&& !socket.isClosed()) {
                        try {
                            if (inputStream.available() > 0) {
                                if (clients.containsValue(socket)) {
                                    buffer = new byte[inputStream.available()];
                                    inputStream.read(buffer);

                                    tcpConListener.onData(Utils.getKeyByValue(clients, socket), buffer);
                                } else {
                                    buffer = new byte[inputStream.available()];
                                    int readLen = inputStream.read(buffer);
                                    if (buffer[0] == (byte) ID_REQ_PREAMBLE) {
                                        ClientID clientID = ClientID.typeOfID((int) buffer[1]);
                                        clients.put(clientID, socket);
                                        tcpConListener.onConnect(socket.getInetAddress().getHostAddress(), socket.getPort(), clientID);
                                    } else {
                                        socket.getOutputStream().write((byte) ID_REQ_PREAMBLE);
                                    }
                                }
                            }

                        } catch (IOException e) {
                            e.printStackTrace();
                        }


            }



        }
    }


}
