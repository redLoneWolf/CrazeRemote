package com.sudhar.crazeremote.connectivity;

public enum ClientID {

    MAIN(100),
    CAM(101),
    DEPTH(102);

    private int value;
    ClientID(int value){
        this.value=value;
    }

    public int getValue() {
        return value;
    }

    public static ClientID typeOfID(int val) {
        for (ClientID e : values()) {
            if (e.getValue()== val) {
                return e;
            }
        }
        return null;
    }
}
