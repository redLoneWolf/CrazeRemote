package com.sudhar.crazeremote.connectivity;

import android.graphics.Bitmap;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

public class TCPProtocol extends TCPServer{

    public static final int PREAMBLE_POSITION = 0;
    public static final int SIZE_POSITION = 1;
    public static final int COMMAND_POSITION = SIZE_POSITION + 4; // because here size in integer
    public static final int DATA_OFFSET = COMMAND_POSITION+1;
    private static final String TAG = "TCPProtocol";
    public static final char PREAMBLE = '$';
    ByteBuffer byteBuffer;

    private boolean handshake = false;
    private boolean gotPreamble = false;
    private boolean gotSize = false;
    private boolean gotCommand = false;
    private int size = 0;
    private TCPCommand currentCommand;
    private TCPProtocolListener tcpProtocolListener;
    ImageParser imageParser = null;
    private ByteBuffer TXBuffer;

    public TCPProtocol(int mPort, String handlerName,TCPProtocolListener tcpProtocolListener) {
        super(mPort, handlerName);
        super.tcpConListener = tcpConListener;
        this.tcpProtocolListener = tcpProtocolListener;
        TXBuffer =  ByteBuffer.allocate(128).order(ByteOrder.BIG_ENDIAN);
        byteBuffer =  ByteBuffer.allocate(128).order(ByteOrder.BIG_ENDIAN);

        imageParser = new ImageParser(new ImageParser.ImageListener() {
            @Override
            public void onImageReady(Bitmap bitmap) {
                tcpProtocolListener.onImageParsed(bitmap);
            }
        });
    }


    void evaluateMain(byte[] in){
        byteBuffer.put(in);

        if (byteBuffer.get(PREAMBLE_POSITION) == (byte) PREAMBLE) {  //Check for Preamble
            gotPreamble = true;
        } else {
            Log.d(TAG, "onNewData: intruder");
            byteBuffer.clear();
            return;
        }

        if (!gotSize && gotPreamble && byteBuffer.position() > SIZE_POSITION) {  // Check for size of data
            gotSize = true;
            size = byteBuffer.getInt(SIZE_POSITION);
        }

        if (! gotCommand && gotSize && byteBuffer.position() >= COMMAND_POSITION) {  // Check for Command
            gotCommand = true;
            int commandValue = byteBuffer.get(COMMAND_POSITION) & 0xFF;
            if (byteBuffer.get(COMMAND_POSITION) == TCPCommand.HANDSHAKE.getValue()) {
                handshake = true;
                currentCommand = TCPCommand.HANDSHAKE;
            }else{
                for (TCPCommand tcpCommand: TCPCommand.values()) {
                    if(tcpCommand.getValue()==commandValue){
                        currentCommand = tcpCommand;
                        break;
                    }
                }
            }

        }

        Log.d(TAG, "evaluate: "+byteBuffer.position());
        if (gotCommand && byteBuffer.position() >= DATA_OFFSET + size) {
            byteBuffer.position(DATA_OFFSET);
            byte[] data = new byte[size];
            byteBuffer.get(data,0,size);
            onCommand(currentCommand,data);


            byteBuffer.clear();
            gotCommand = false;
            gotSize = false;
            gotPreamble = false;
        }

        if (byteBuffer.position() == 128) {
            byteBuffer.rewind();
        }
    }

    void onCommand(TCPCommand tcpCommand,byte[] data){
        switch (tcpCommand){
            case DISCONNECT:
                try {
                    Objects.requireNonNull(clients.get(ClientID.MAIN)).close();
                    clients.remove(ClientID.MAIN);
                    tcpConListener.onDisconnect(ClientID.MAIN);

                    if(clients.containsKey(ClientID.CAM)){
                        Objects.requireNonNull(clients.get(ClientID.CAM)).close();
                        clients.remove(ClientID.CAM);
                        tcpConListener.onDisconnect(ClientID.CAM);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;


        }

        tcpProtocolListener.onDataReceived(currentCommand,data);
    }

    public void sendPacket(TCPCommand tcpCommand, byte[] data){
        TXBuffer.clear();
        addPreamble();
        addSize(data.length);
        Log.d(TAG, "sendPacket: "+data.length);
        addCommand(tcpCommand);
        addData(data);
        sendBytes(ClientID.MAIN,TXBuffer.array(),0,data.length+4+1+1);

//        sendPreamble();
//        sendSize(data.length);
//        sendCommand(tcpCommand);
//        sendData(data);

    }

    public void sendPacket(TCPCommand tcpCommand){
        TXBuffer.clear();
        addPreamble();
        addSize(0);
        addCommand(tcpCommand);
        sendBytes(ClientID.MAIN,TXBuffer.array(),0, 4 + 1 + 1);
    }




    void addPreamble(){
        TXBuffer.put((byte) PREAMBLE);
    }

    void addSize(int size){
        TXBuffer.putInt(size);
    }
    void addCommand(TCPCommand tcpCommand){

        TXBuffer.put((byte) tcpCommand.getValue());
    }

    void addData(byte[] data){
        TXBuffer.put(data);
    }

    TCPServer.TCPConListener tcpConListener = new TCPServer.TCPConListener() {
        @Override
        public void onStart(String host, int port) {
            tcpProtocolListener.onStart(host,port);
        }

        @Override
        public void onConnect(String Host, int Port, ClientID clientID) {
                tcpProtocolListener.onConnect(Host,Port,clientID);
                if(clientID == ClientID.CAM){
                    Log.d(TAG, "onConnect: ");

                }else if (clientID==ClientID.MAIN){



                }

        }

        @Override
        public void onError(String error) {
                tcpProtocolListener.onError(error);
        }

        @Override
        public void onStop() {
            tcpProtocolListener.onStop();
        }

        @Override
        public void onData(ClientID clientID, byte[] bytes) {
            tcpProtocolListener.onData(clientID,bytes);
            switch (clientID){
                case MAIN:
                    evaluateMain(bytes);
                    break;
                case CAM:
                    imageParser.evaluateCAM2(bytes);
                    break;
            }



        }

        @Override
        public void onDisconnect(ClientID clientID) {
            tcpProtocolListener.onDisconnect(clientID);

        }
    };


}
