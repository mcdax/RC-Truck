package com.camera.simplemjpeg;

abstract public class Constants {
    /** Ports */
    public static final int PORT_MOTION = 8081;
    public static final int PORT_SOCKET = 1337;
    /** Constants */
    public static final int CLOSESOCKET = 77; // Client closed Socket-Connection
    // Motor controls (the number indicates the gear)
    // Motor 1
    public static final int GO_1 = 116;
    public static final int GO_2 = 117;
    public static final int GO_3 = 118;
    public static final int GO_4 = 119;
    public static final int GO_5 = 120;
    public static final int BACK_1 = 114;
    public static final int BACK_2 = 113;
    public static final int BACK_3 = 112;
    public static final int BACK_4 = 111;
    public static final int BACK_5 = 110;
    public static final int STOP = 115; // Stops GO + BACK
    // Motor 2
    public static final int LEFT = 11;
    public static final int RIGHT = 12;
    public static final int STRAIGHT = 10; // Lines up LEFT + RIGHT
    // Lights
    public static final int LIGHTS_OFF = 18;
    public static final int LIGHTS_ON = 19;
    public static final int LIGHTS_AUTO_OFF = 20; // automatic lights
    public static final int LIGHTS_AUTO_ON = 21;
    // Sound
    public static final int HORN = 25;
    public static final int MUSIC_ON = 30;
    public static final int SOUND_OFF = 31; // Sound off disables horn and music
}
