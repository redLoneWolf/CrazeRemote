package com.sudhar.crazeremote.connectivity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ImageParser {
    public interface ImageListener{
        void onImageReady(Bitmap bitmap);
    }

    private ImageListener imageListener;
    ByteBuffer byteBuffer;
    public static final int PREAMBLE_POSITION = 0;
    public static final int SIZE_POSITION = 1;
    public static final int DATA_OFFSET = 4+1;
    public static final int PREAMBLE_SIZE = 1;
    public static final int SIZE_SIZE  = 4;
    private static final String TAG = "ImageParser";
    public static final char PREAMBLE = '$';
    ByteBuffer imageBuffer;
    private boolean handshake = false;
    private boolean gotPreamble = false;
    private boolean gotSize = false;
//    private boolean gotCommand = false;
    private int size = 0;
    private ByteArrayOutputStream byteArrayOutputStream;

    public ImageParser(ImageListener imageListener) {
        this.imageListener = imageListener;
        byteBuffer =  ByteBuffer.allocate(6*1000000).order(ByteOrder.BIG_ENDIAN);
        byteArrayOutputStream = new ByteArrayOutputStream();
    }

    void evaluateCam(byte[] in){
        byteBuffer.put(in);

        if (byteBuffer.get(PREAMBLE_POSITION) == (byte) PREAMBLE) {  //Check for Preamble
            gotPreamble = true;
            Log.d(TAG, "evaluateCam: "+gotPreamble);
        } else {
            Log.d(TAG, "onNewData: intruder");
            byteBuffer.clear();
            byteBuffer=ByteBuffer.allocate(6*1000000).order(ByteOrder.BIG_ENDIAN);
            return;
        }

        if (gotPreamble && byteBuffer.position() > SIZE_POSITION) {  // Check for size of data
            gotSize = true;
            size = byteBuffer.getInt(SIZE_POSITION);
        }

//        if(gotSize){
//            Log.d(TAG, "evaluateCam: Size :"+ byteBuffer.position() +" of "+size);
//
//        }
        if (gotSize && byteBuffer.position() >= DATA_OFFSET + size) {
            byteBuffer.position(DATA_OFFSET);
            byte[] data = new byte[size];
            byteBuffer.get(data,0,size);
            Bitmap bitmap = BitmapFactory.decodeByteArray(data , 0, size);
            imageListener.onImageReady(bitmap);
            Log.d(TAG, "evaluateCam: Got image");
//            tcpConListener.onDataReceived(currentCommand,data);

            ByteBuffer temp = byteBuffer.duplicate();

            byteBuffer =  ByteBuffer.allocate(6*1000000).order(ByteOrder.BIG_ENDIAN);

            int len = temp.position();
            int offf = DATA_OFFSET + size;
            byte b = temp.array()[offf+1];
            byte[] d = getSliceOfArray(temp.array(),offf,len);
            if(d.length>0){
                byteBuffer.put(d);
            }


//            byteBuffer.put(temp.array(),DATA_OFFSET + size,temp.position()-DATA_OFFSET + size);

            gotSize = false;
            gotPreamble = false;
            size = 0;
        }

        if (byteBuffer.position() == 6*1000000) {
            byteBuffer=ByteBuffer.allocate(6*1000000).order(ByteOrder.BIG_ENDIAN);
            gotSize = false;
            gotPreamble = false;
            size = 0;
        }
    }


    void evaluateCAM2(byte[] in){
        try {
            byteArrayOutputStream.write(in);
            process();
        } catch (IOException e) {
            e.printStackTrace();
        }



    }

    void process(){
        byte[] arr = byteArrayOutputStream.toByteArray();

        if (byteArrayOutputStream.toByteArray()[PREAMBLE_POSITION] == (byte) PREAMBLE) {  //Check for Preamble
            gotPreamble = true;
            Log.d(TAG, "evaluateCam: "+gotPreamble);
        } else {
            Log.d(TAG, "onNewData: intruder");
            byteArrayOutputStream.reset();
            return;
        }

        if ((!gotSize) && gotPreamble && byteArrayOutputStream.size() >= SIZE_POSITION) {  // Check for size of data
            gotSize = true;
            size = ByteBuffer.wrap(arr).getInt(SIZE_POSITION);
        }


        if (gotSize && byteArrayOutputStream.size() >= DATA_OFFSET + size) {

            byte[] data = getSliceOfArray(byteArrayOutputStream.toByteArray(),DATA_OFFSET,byteArrayOutputStream.size());
            Bitmap bitmap = BitmapFactory.decodeByteArray( data, 0, data.length);
            imageListener.onImageReady(bitmap);
            Log.d(TAG, "evaluateCam: Got image");


            byte[] ar2 = getSliceOfArray(byteArrayOutputStream.toByteArray(),DATA_OFFSET+size,byteArrayOutputStream.size());



            byteArrayOutputStream.reset();
            try {
                byteArrayOutputStream.write(ar2);
            } catch (IOException e) {
                e.printStackTrace();
                byteArrayOutputStream.reset();
            }

            gotSize = false;
            gotPreamble = false;
            size = 0;
        }



    }



    public static byte[] getSliceOfArray(byte[] arr,
                                        int start, int end)
    {

        // Get the slice of the Array
        byte[] slice = new byte[end - start];

        // Copy elements of arr to slice
        for (int i = 0; i < slice.length; i++) {
            slice[i] = arr[start + i];
        }

        // return the slice
        return slice;
    }
}


