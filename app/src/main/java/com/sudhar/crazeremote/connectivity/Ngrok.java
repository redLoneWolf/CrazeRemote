package com.sudhar.crazeremote.connectivity;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.sudhar.crazeremote.NgrokLog;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@RequiresApi(api = Build.VERSION_CODES.O)
public class Ngrok {
    public interface NgrokListener{
        void onLog(NgrokLog log);
        void onStart(String localUrl,String url);
        void onStop();

    }
    private static final String TAG = "Ngrok";
    NgrokListener ngrokListener;
    String executableFilePath;
    String userConfigFilePath;
    String defaultConfigFilePath;
    String authToken;
    Process process;
    NgrokRunnable ngrokRunnable;




    public Ngrok(Context context, String executablePath, String authToken,NgrokListener ngrokListener) {
        this.authToken = authToken;
        this.executableFilePath = executablePath;
        this.defaultConfigFilePath = context.getExternalFilesDir(null).getAbsolutePath();
        this.ngrokListener=ngrokListener;
        checkConfigFile();
    }

    public Ngrok(Context context, String executablePath, String authToken, String userConfigFilePath, NgrokListener ngrokListener) {
        this.authToken = authToken;
        this.executableFilePath = executablePath;
        this.defaultConfigFilePath = context.getExternalFilesDir(null).getAbsolutePath();
        this.ngrokListener=ngrokListener;
        this.userConfigFilePath = userConfigFilePath;

    }


    void checkConfigFile(){

        File f = new File(defaultConfigFilePath+"/.ngrok2/ngrok.yml");
        if(!f.exists()) {
            execute(executableFilePath,"authtoken",authToken);
        }
    }

    public void updateConfigFile(){

        File f = new File(defaultConfigFilePath+"/.ngrok2/ngrok.yml");
        if(f.exists()) {
            execute(executableFilePath,"authtoken",authToken);
        }
    }




   public void start(String proto,int port){

        File execFile = new File(executableFilePath);
        execFile.setExecutable(true);


        try {
//            ProcessBuilder processBuilder = new ProcessBuilder(executableFilePath,proto,String.valueOf(port),"--log=stdout");
            List<String> command = new ArrayList<>();
            command.add(executableFilePath);
            command.add(proto);
            command.add(String.valueOf(port));
            command.add("--authtoken="+authToken);
//            command.add(authToken);
            command.add("--region=eu");
            command.add("--log=stdout");

            if(userConfigFilePath!=null){
                command.add("--config="+userConfigFilePath);
            }

            ProcessBuilder processBuilder = new ProcessBuilder(command);

            processBuilder.redirectErrorStream(true);
            processBuilder.inheritIO().redirectOutput(ProcessBuilder.Redirect.PIPE);
            processBuilder.environment().put("USER","$(id -un)");
            processBuilder.environment().put("HOME",defaultConfigFilePath);

            process = processBuilder.start();
            new Thread(new NgrokRunnable(process)).start();

        } catch (IOException e ) {
            e.printStackTrace();
            Log.e(TAG,e.getMessage(),e);
            Log.d(TAG, "onCreate: "+e.getStackTrace().toString());
        }
    }


   public boolean isProcessRunning(){
        return process!=null && process.isAlive();
    }

   public boolean isRunnableRunning(){
        return ngrokRunnable!=null && ngrokRunnable.isKeepAlive();
    }

   public void stop(){
        if(isProcessRunning()){
            process.destroy();
            process.destroyForcibly();
            process=null;
            ngrokListener.onStop();
        }
        if(isRunnableRunning()){
            ngrokRunnable.setKeepAlive(false);
            ngrokRunnable = null;
        }
    }


    class NgrokRunnable implements Runnable {
        Process process;
        boolean keepAlive=true;
        public NgrokRunnable(Process process) {
            this.process = process;
        }

        @Override
        public void run() {

            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                String line;
                while (keepAlive &&process.isAlive() && (line = reader.readLine()) != null  ) {
                    NgrokLog ngrokLog = new NgrokLog(line);
                    ngrokListener.onLog(ngrokLog);
                    checkLog(ngrokLog);
                }
                Log.d(TAG, "run: ");


            } catch (IOException e) {
                e.printStackTrace();
                ngrokListener.onStop();
                Log.e(TAG, e.getMessage(), e);
                Log.d(TAG, "run: " + e.getStackTrace().toString());
            }
        }

        public boolean isKeepAlive() {
            return keepAlive;
        }

        public void setKeepAlive(boolean keepAlive) {
            this.keepAlive = keepAlive;
        }
    }

    void checkLog(NgrokLog ngrokLog){

        String message = ngrokLog.getMsg();
        if(message!=null && message.contains("started tunnel")){
                ngrokListener.onStart(ngrokLog.getAddr(),ngrokLog.getUrl());
        }

    }

    void execute(String... command) {
        try {

            ProcessBuilder processBuilder = new ProcessBuilder(command);

            processBuilder.environment().put("USER", "$(id -un)");
            processBuilder.environment().put("HOME", defaultConfigFilePath);

            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            int read;
            char[] buffer = new char[4096];
            StringBuffer output = new StringBuffer();
            while ((read = reader.read(buffer)) > 0) {
                output.append(buffer, 0, read);
            }
            reader.close();
            process.waitFor();
            Log.d(TAG, "output: " + output.toString());

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage(), e);
        }
    }
}
