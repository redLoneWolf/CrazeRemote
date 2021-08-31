package com.sudhar.crazeremote;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.sudhar.crazeremote.connectivity.ClientID;
import com.sudhar.crazeremote.connectivity.Ngrok;
import com.sudhar.crazeremote.connectivity.TCPCommand;
import com.sudhar.crazeremote.connectivity.TCPProtocol;
import com.sudhar.crazeremote.connectivity.TCPProtocolListener;
import com.sudhar.crazeremote.connectivity.TCPServer;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiresApi(api = Build.VERSION_CODES.O)
public class ConnectionService extends Service {
    public static final int CHANNEL_NO = 1;
    TCPProtocol tcpProtocol;
    List<TCPProtocolListener> tcpProtocolListeners = new ArrayList<>();
    List<Ngrok.NgrokListener> ngrokListeners = new ArrayList<>();
    public static final int port = 8888;
    ConnectionType connectionType = ConnectionType.LOCAL;

    public void setConnectionType(ConnectionType connectionType) {
        this.connectionType = connectionType;
    }

    private final IBinder localBinder = new ConnectionServiceBinder();

    public class ConnectionServiceBinder extends Binder {

        public ConnectionService getService() {
            return ConnectionService.this;

        }
    }


    void init(){
        usbConnected = false;
        tcpProtocol = new TCPProtocol(port,"Server",tcpProtocolConListener);
    }

    void startServer(){
        if(tcpProtocol!=null){
            tcpProtocol.start();
        }
    }


    void stopServer(){
        if(tcpProtocol!=null){
            toggleUSB();
            usbConnected = false;
            tcpProtocol.sendPacket(TCPCommand.DISCONNECT);

            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {

                    if (tcpProtocol != null && tcpProtocol.isListening()) {
                        tcpProtocol.stop();

                    }


                }
            }, 1000);
        }
    }
    Ngrok ngrok;


    void startNgrok(String authToken,String executableFilePath,boolean authTokenChanged){
        tcpProtocol.setUseLocal(false);
        startServer();


        ngrok = new Ngrok(getApplicationContext(),executableFilePath , authToken, new Ngrok.NgrokListener() {
            @Override
            public void onLog(NgrokLog log) {

                for(Ngrok.NgrokListener listener:ngrokListeners){
                    listener.onLog(log);
                }
            }

            @Override
            public void onStart(String localUrl, String url) {

                for(Ngrok.NgrokListener listener:ngrokListeners){
                    listener.onStart(localUrl,url);
                }
            }

            @Override
            public void onStop() {
                for(Ngrok.NgrokListener listener:ngrokListeners){
                    listener.onStop();
                }
            }
        });
        if(authTokenChanged){
            ngrok.updateConfigFile();
        }




        ngrok.start("tcp",port);

    }

    boolean usbConnected = false;

    public void setUsbConnected(boolean usbConnected) {
        this.usbConnected = usbConnected;
    }

    void toggleUSB(){

        if(tcpProtocol.isListening()){

            if(usbConnected){
                tcpProtocol.sendPacket(TCPCommand.USB_DISCONNECT);
                usbConnected = false;
            }else {
                tcpProtocol.sendPacket(TCPCommand.USB_CONNECT);
                usbConnected=true;
            }

        }
    }

    void sendRCdata(int[] raw){
        if(tcpProtocol.isListening()){
            tcpProtocol.sendPacket(TCPCommand.WRITE_MOTORS,Utils.IntArray2ByteArray(raw, ByteOrder.BIG_ENDIAN));
        }
    }

    void stopNgrok(){
        stopServer();
        if(ngrok!=null){
            ngrok.stop();
        }
    }

    boolean isListening(){
        if(tcpProtocol!=null){
            return tcpProtocol.isListening();
        }
        return false;
    }

    Map<ClientID, Socket> getClients(){
            return tcpProtocol.getClients();
    }

    TCPProtocolListener tcpProtocolConListener = new TCPProtocolListener() {
        @Override
        public void onDataReceived(TCPCommand tcpCommand, byte[] data) {
            for (TCPProtocolListener listener : tcpProtocolListeners) {
                listener.onDataReceived(tcpCommand,data);
            }
        }

        @Override
        public void onImageParsed(Bitmap bitmap) {

            for (TCPProtocolListener listener : tcpProtocolListeners) {
                listener.onImageParsed(bitmap);
            }
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void onStart(String host, int port) {
            for (TCPProtocolListener listener : tcpProtocolListeners) {
                listener.onStart(host,port);
            }
        }

        @Override
        public void onConnect(String Host, int Port, ClientID clientID) {


            updateNotification("Connected to " + Host + ":" + Port);
            for (TCPProtocolListener listener : tcpProtocolListeners) {
                listener.onConnect(Host, Port,clientID);
            }
        }

        @Override
        public void onDisconnect( ClientID clientID) {

            for (TCPProtocolListener listener : tcpProtocolListeners) {
                listener.onDisconnect(clientID);
            }
        }

        @Override
        public void onError(String error) {
            updateNotification("Error :" + error);
            for (TCPProtocolListener listener : tcpProtocolListeners) {
                listener.onError(error);
            }
        }

        @Override
        public void onStop() {
            updateNotification("Stopped");
            for (TCPProtocolListener listener : tcpProtocolListeners) {
                listener.onStop();
            }
        }

        @Override
        public void onData(ClientID clientID, byte[] bytes) {


            for (TCPProtocolListener listener : tcpProtocolListeners) {
                listener.onData(clientID, bytes);
            }

        }

    };

    void startCam(){
        tcpProtocol.sendPacket(TCPCommand.START_CAM_FEED, ByteBuffer.allocate(8).putInt(1080).putInt(1080).array());
    }

    void stopCam(){
        tcpProtocol.sendPacket(TCPCommand.STOP_CAM_FEED);
        Socket socket = tcpProtocol.getClients().get(ClientID.CAM);
        tcpProtocol.getClients().remove(ClientID.CAM);
        for (TCPProtocolListener listener:tcpProtocolListeners) {
            listener.onDisconnect(ClientID.CAM);

        }
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        init();
        startNotification("Not Connected");

        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return localBinder;
    }
    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }


    private Notification getNotification(String status) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            return new NotificationCompat.Builder(this, App.CHANNEL_1_ID)
                    .setContentTitle("TCP service")
                    .setContentText(status)
                    .setSmallIcon(R.drawable.ic_launcher_background)
                    .setContentIntent(pendingIntent)
                    .build();
        } else {
            return new NotificationCompat.Builder(this, App.CHANNEL_1_ID)
                    .setContentTitle("TCP service")
                    .setContentText(status)
                    .setSmallIcon(R.drawable.ic_launcher_background)
                    .setContentIntent(pendingIntent)
                    .build();
        }

    }

    private void startNotification(String status) {
        startForeground(1, getNotification(status));
    }

    private void updateNotification(String status) {
        Notification notification = getNotification(status);

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(CHANNEL_NO, notification);
    }

    void addTCPListener(TCPProtocolListener tcpConListener) {
        tcpProtocolListeners.add(tcpConListener);
    }

    void removeTCPListener(TCPProtocolListener tcpConListener) {
        tcpProtocolListeners.remove(tcpConListener);
    }

    void removeAllTCPListeners() {
        tcpProtocolListeners.clear();
    }

    private void notifyAllTCPListeners(TCPCommand tcpCommand, byte[] bytes) {
        for (TCPProtocolListener tcpConListener : tcpProtocolListeners) {
            tcpConListener.onDataReceived(tcpCommand, bytes);
        }
    }

    void addNgrokListener(Ngrok.NgrokListener ngrokListener) {
        ngrokListeners.add(ngrokListener);
    }

    void removeNgrokListener(Ngrok.NgrokListener ngrokListener) {
        ngrokListeners.remove(ngrokListener);
    }

    void removeAllNgrokListeners() {
        ngrokListeners.clear();
    }


}
