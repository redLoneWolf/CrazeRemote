package com.sudhar.crazeremote;

import android.content.Context;
import android.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Enumeration;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class Utils {

    public static byte[] IntArray2ByteArray(int[] values, ByteOrder byteOrder ) {
        ByteBuffer buffer = ByteBuffer.allocate(4 * values.length).order(byteOrder);


        for (int value : values) {
            buffer.putInt(value);
        }

        return buffer.array();
    }

    public static void copyFile(String assetPath, String localPath, Context context) {
        try {
            InputStream in = context.getAssets().open(assetPath);
            FileOutputStream out = new FileOutputStream(localPath);
            int read;
            byte[] buffer = new byte[4096];
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            out.close();
            in.close();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public static <T, E> T getKeyByValue(Map<T, E> map, E value) {
        for (Map.Entry<T, E> entry : map.entrySet()) {
            if (Objects.equals(value, entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static class AndroidLoggingHandler extends Handler {

        public static void reset(Handler rootHandler) {
            Logger rootLogger = LogManager.getLogManager().getLogger("");
            Handler[] handlers = rootLogger.getHandlers();
            for (Handler handler : handlers) {
                rootLogger.removeHandler(handler);
            }
            rootLogger.addHandler(rootHandler);
        }

        @Override
        public void close() {
        }

        @Override
        public void flush() {
        }

        @Override
        public void publish(LogRecord record) {
            if (!super.isLoggable(record))
                return;

            String name = record.getLoggerName();
            int maxLength = 30;
            String tag = name.length() > maxLength ? name.substring(name.length() - maxLength) : name;

            try {
                int level = getAndroidLevel(record.getLevel());
                Log.println(level, tag, record.getMessage());
                if (record.getThrown() != null) {
                    Log.println(level, tag, Log.getStackTraceString(record.getThrown()));
                }
            } catch (RuntimeException e) {
                Log.e("AndroidLoggingHandler", "Error logging message.", e);
            }
        }

        static int getAndroidLevel(Level level) {
            int value = level.intValue();

            if (value >= Level.SEVERE.intValue()) {
                return Log.ERROR;
            } else if (value >= Level.WARNING.intValue()) {
                return Log.WARN;
            } else if (value >= Level.INFO.intValue()) {
                return Log.INFO;
            } else {
                return Log.DEBUG;
            }
        }
    }
}
