package com.sudhar.crazeremote.connectivity;

import android.graphics.Bitmap;

public interface TCPProtocolListener extends TCPServer.TCPConListener {
    void onDataReceived(TCPCommand tcpCommand,byte[] data);
    void onImageParsed(Bitmap bitmap);
}
