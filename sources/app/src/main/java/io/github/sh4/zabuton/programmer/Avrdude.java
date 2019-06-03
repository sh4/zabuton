package io.github.sh4.zabuton.programmer;

import com.hoho.android.usbserial.driver.UsbSerialPort;

public class Avrdude {
    public static native void programming(UsbSerialPort port, String configFilePath, String hexFilePath);
    public static native String dequeueMessage();
}