package com.sudhar.crazeremote;



import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import static java.util.logging.Level.SEVERE;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.database.Observable;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.media.ExifInterface;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;



import com.sudhar.crazeremote.connectivity.ClientID;
import com.sudhar.crazeremote.connectivity.Ngrok;
import com.sudhar.crazeremote.connectivity.TCPCommand;
import com.sudhar.crazeremote.connectivity.TCPProtocolListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

import io.github.controlwear.virtual.joystick.android.JoystickView;

@RequiresApi(api = Build.VERSION_CODES.O)
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String TOKEN_KEY = "ngrok_authToken";

    JoystickView joystick;
    TextView motorVal;
    TextView tcpStat;
    Button btnConnect,camBtn,usbBtn;
    ConnectionService boundService;
    boolean isBound = false;
    Intent broadcastIntent;
    SurfaceView surfaceView;
    SurfaceHolder surfaceHolder;
    RadioButton ngrokBtn,localBtn;
    ConnectionType connectionType = ConnectionType.LOCAL;
    boolean authTokenChanged = false;
    RadioGroup radioGroup;


    @Override
    protected void onStart() {
        super.onStart();
        startAndBindService();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopAndUnbindService();
    }

    String executableFilePath;
    SharedPreferences sharedPref;
    String authToken;


    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);


//
         sharedPref = getApplicationContext().getSharedPreferences(getPackageName(), Context.MODE_PRIVATE);
            executableFilePath  = getApplicationInfo().nativeLibraryDir + "/libngrok.so";



        joystick = findViewById(R.id.joystick);
        motorVal =  findViewById(R.id.motorVal);
        tcpStat = findViewById(R.id.stat);
        btnConnect  = findViewById(R.id.startBtn);
        camBtn = findViewById(R.id.camBtn);
        surfaceView = findViewById(R.id.surfaceView);
        surfaceView.setVisibility(View.GONE);
//        surfaceView.setZOrderMediaOverlay(true);

        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.setFormat(PixelFormat.TRANSPARENT);

        localBtn = findViewById(R.id.localBtn);
        localBtn.setChecked(true);
        ngrokBtn = findViewById(R.id.ngrokBtn);
        ngrokBtn.setChecked(false);

        radioGroup = findViewById(R.id.radioGrp);
        localBtn.setOnClickListener(radioBtnListener);
        ngrokBtn.setOnClickListener(radioBtnListener);
        usbBtn = findViewById(R.id.usbBtn);

        usbBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isMyServiceRunning(ConnectionService.class) && isBound){
                    boundService.toggleUSB();
                }
            }
        });


        ngrokBtn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                alertDialogDemo();
                return false;
            }
        });


        int[] d = new int[]{0,0,0,0};


        String[] dir = {"Right", "Top-Right", "Top", "Top-Left", "Left", "Bottom-Left", "Bottom", "Bottom-Right"};

        joystick.setOnMoveListener(new JoystickView.OnMoveListener() {
            @Override
            public void onMove(int angle, int strength) {





//                String str = String.format(Locale.ENGLISH,"Angle : %d° Strength: %d%%", angle, strength);



                float t = ((angle %= 360) < 0 ? angle + 360 : angle);

                int index = Math.round(t/45) % 8;


                switch (JoystickDirection.values()[index]){
                    case Top:
                        Arrays.fill(d, strength);
                        break;
                    case Bottom:
                        Arrays.fill(d, -strength);
                        break;
                    case Left:
                        d[0] = strength;
                        d[1] = strength;

                        d[2] = -strength;
                        d[3] = -strength;
                        break;

                    case Right:
                        d[0] = -strength;
                        d[1] = -strength;

                        d[2] = strength;
                        d[3] = strength;
                        break;
                    case Top_Left:
                        d[0] = strength;
                        d[1] = strength;

                        d[2] = -(int)  (strength*0.5) + strength;
                        d[3] = -(int)  (strength*0.5) + strength;
                        break;
                    case Top_Right:
                        d[0] =  -(int)  (strength*0.5) + strength;
                        d[1] =  -(int)  (strength*0.5) + strength;

                        d[2] = strength;
                        d[3] = strength;
                        break;

                    case Bottom_Left:
                        d[0] = -strength;
                        d[1] = -strength;

                        d[2] = (int)  (strength*0.5) + strength;
                        d[3] = (int)  (strength*0.5) + strength;
                        break;

                    case Bottom_Right:
                        d[0] =  (int)  (strength*0.5) + strength;
                        d[1] =  (int)  (strength*0.5) + strength;

                        d[2] = -strength;
                        d[3] = -strength;

                }

                if (isMyServiceRunning(ConnectionService.class) && isBound){
                    boundService.sendRCdata(d);
                }

                motorVal.setText(Arrays.toString(d));

                motorVal.append(" Dir : "+JoystickDirection.values()[index]);

            }
        });


        if (!isMyServiceRunning(ConnectionService.class)) {
            btnConnect.setEnabled(false);
            camBtn.setEnabled(false);
            usbBtn.setEnabled(false);
        }

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isMyServiceRunning(ConnectionService.class) && isBound) {
                    if (boundService.isListening()) {


                        switch (connectionType){
                            case LOCAL:
                                boundService.stopServer();
                                break;
                            case NGROK:
                                boundService.stopNgrok();
                                break;
                        }

                    } else {
                        btnConnect.setText("Starting...");


                        switch (connectionType){
                            case LOCAL:
                                boundService.startServer();
                                break;
                            case NGROK:
                                if(checkToken()){
                                    authToken = sharedPref.getString(TOKEN_KEY,"");
                                    boundService.startNgrok(authToken,executableFilePath, authTokenChanged);
                                }
                                break;
                        }
                    }
                }

            }
        });


        camBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(boundService.isListening()){

                    if(!boundService.getClients().containsKey(ClientID.CAM)){
                        boundService.startCam();
                    }else {
                        boundService.stopCam();
                    }

                }
            }
        });
    }


    View.OnClickListener radioBtnListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            switch (view.getId()){
                case R.id.localBtn:
                    connectionType = ConnectionType.LOCAL;
                    break;
                case R.id.ngrokBtn:
                    connectionType = ConnectionType.NGROK;
                    checkToken();
                    break;
            }
        }
    };

    boolean checkToken(){
        if(!sharedPref.contains(TOKEN_KEY)){
            alertDialogDemo();
            return false;
        }
        return true;
    }


    void alertDialogDemo() {
        // get alert_dialog.xml view
        LayoutInflater li = LayoutInflater.from(MainActivity.this);
        View promptsView = li.inflate(R.layout.alert_dialog, null);

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                MainActivity.this);

        // set alert_dialog.xml to alertdialog builder
        alertDialogBuilder.setView(promptsView);
        alertDialogBuilder.setTitle("Enter AuthToken");

        final EditText userInput = (EditText) promptsView.findViewById(R.id.etUserInput);

        if(sharedPref.contains(TOKEN_KEY)){
            userInput.setText(sharedPref.getString(TOKEN_KEY,""));
        }

        // set dialog message
        alertDialogBuilder

                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // get user input and set it to result
                        // edit text
                        String token = userInput.getText().toString().trim();
                        if(token.isEmpty()){
                            Toast.makeText(getApplicationContext(), "Empty", Toast.LENGTH_LONG).show();
                        }else {
                            sharedPref.edit().putString(TOKEN_KEY,token).apply();
                            authToken = token;
                            authTokenChanged=true;
                            Toast.makeText(getApplicationContext(), "Token saved : "+ token, Toast.LENGTH_LONG).show();
                        }

                    }
                })
                .setNegativeButton("Cancel",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        });

        // create alert dialog
        AlertDialog alertDialog = alertDialogBuilder.create();

        // show it
        alertDialog.show();


    }

    TCPProtocolListener tcpProtocolListener = new TCPProtocolListener() {
        @Override
        public void onDataReceived(TCPCommand tcpCommand, byte[] data) {

        }

        @Override
        public void onImageParsed(Bitmap bitmap) {

                Canvas canvas = surfaceHolder.lockCanvas();
                if (canvas != null)
                {
                    if(bitmap != null)
                    {
                        Paint paint = new Paint();
                        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap,
                                surfaceView.getWidth(),
                                surfaceView.getHeight(),
                                true);
                        canvas.drawBitmap(scaledBitmap, 0, 0, paint);
                        bitmap.recycle();
                    }
                    surfaceHolder.unlockCanvasAndPost(canvas);
                }

        }

        @Override
        public void onStart(String host, int port) {

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    tcpStat.setText("Listening on tcp://"+host+":"+port);
                    btnConnect.setText("Stop");

                    localBtn.setEnabled(false);
                    ngrokBtn.setEnabled(false);

                }
            });
        }

        @Override
        public void onConnect(String host, int port, ClientID clientID) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    switch (clientID){
                        case MAIN:
                            camBtn.setEnabled(true);
                            usbBtn.setEnabled(true);
                            break;
                        case CAM:
                            camBtn.setText("Stop Cam");
                            surfaceView.setVisibility(View.VISIBLE);
                            break;
                    }

                    Toast.makeText(getApplicationContext(), "Client ID :"+ clientID.getValue(), Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void onDisconnect( ClientID clientID) {

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    switch (clientID){
                        case CAM:
                            camBtn.setText("Start Cam");
                            surfaceView.setVisibility(View.GONE);
                            break;
                        case MAIN:
                            Toast.makeText(MainActivity.this, "Main Client Disconnected", Toast.LENGTH_SHORT).show();
                            camBtn.setEnabled(false);
                            boundService.setUsbConnected(false);
                            usbBtn.setEnabled(false);
                            break;

                    }
                }
            });


        }

        @Override
        public void onData(ClientID clientID, byte[] data) {

        }

        @Override
        public void onError(String error) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "Error :"+error, Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void onStop() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    btnConnect.setText("start");
                    tcpStat.setText("Not running");
                    localBtn.setEnabled(true);
                    ngrokBtn.setEnabled(true);
                    usbBtn.setEnabled(false);
                    camBtn.setEnabled(false);
                }
            });
        }
    };

    Ngrok.NgrokListener ngrokListener = new Ngrok.NgrokListener() {
        @Override
        public void onLog(NgrokLog log) {
            Log.d(TAG, "onLog: "+log.getLine());

            if(log.getLvl().equals(SEVERE.getName())){
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), log.getErr(), Toast.LENGTH_LONG).show();
                    }
                });

            }
        }

        @Override
        public void onStart(String localUrl, String url) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    tcpStat.setText("Ngrok "+url);

                }
            });
        }

        @Override
        public void onStop() {

        }
    };


    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ConnectionService.ConnectionServiceBinder binderBridge = (ConnectionService.ConnectionServiceBinder) service;
            boundService = binderBridge.getService();
            boundService.addTCPListener(tcpProtocolListener);
            boundService.addNgrokListener(ngrokListener);

            isBound = true;
            btnConnect.setEnabled(true);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            boundService = null;
        }
    };

    private void startAndBindService() {
        broadcastIntent = new Intent(getApplicationContext(), ConnectionService.class);
        startService(broadcastIntent);
        bindService(broadcastIntent, serviceConnection, BIND_AUTO_CREATE);

    }

    private void stopAndUnbindService() {
        if (isMyServiceRunning(ConnectionService.class)) {
            unbindService(serviceConnection);
            stopService(broadcastIntent);
        }
    }
    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }


}