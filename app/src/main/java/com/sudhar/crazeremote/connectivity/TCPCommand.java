package com.sudhar.crazeremote.connectivity;

public enum TCPCommand {
    HANDSHAKE(200),





    TELEMETRY_DATA(100),

    START_TELEMETRY(101),
    STOP_TELEMETRY(102),

    START_CAM_FEED (103),
    STOP_CAM_FEED(104),
    CAM_FEED_CONFIG(105),


    START_DEPTH_CAM_FEED(106),
    STOP_DEPTH_CAM_FEED(107),

    START_WAY_POINT(108),
    STOP_WAY_POINT(109),

    ROI(111),



    USB_CONNECT(151),
    USB_DISCONNECT(152),
    WRITE_MOTORS(153),






    DISCONNECT(202),
    INVALID(203);

    private int value;
    TCPCommand(int value){
        this.value=value;
    }

    public int getValue() {
        return value;
    }
}
