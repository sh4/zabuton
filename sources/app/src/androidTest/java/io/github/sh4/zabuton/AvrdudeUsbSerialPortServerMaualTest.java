package io.github.sh4.zabuton;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.RemoteException;
import android.system.Os;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.Semaphore;

import io.github.sh4.zabuton.util.UsbSerialPortServer;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class AvrdudeUsbSerialPortServerMaualTest {
    private final static String TAG = AvrdudeUsbSerialPortServerMaualTest.class.getSimpleName();
    private final static String ACTION = AvrdudeUsbSerialPortServerMaualTest.class.getName() + ".USB_PERMISSION";

    @Test
    public void testReadHexFile() throws Exception {
        Context context = getInstrumentation().getTargetContext();

        UsbSerialPort port = getUsbSerialPort(context);
        assertNotNull(port);

        File avrdudeFile = new File(context.getFilesDir(), "avrdude");
        File outputDir = context.getCacheDir();
        File readHexFile = new File(outputDir, "backup.hex");
        if (readHexFile.exists()) {
            readHexFile.delete();
        }

        Os.chmod(avrdudeFile.getAbsolutePath(), 0700);
        assertTrue(avrdudeFile.canExecute());
        Process avrdude = Runtime.getRuntime().exec(avrdudeFile.getAbsolutePath() +
                " -C " + avrdudeFile.getParent() + "/avrdude.conf" + // config
                " -c avr109" + // programmer-id
                " -p atmega32u4" + // partno
                " -U flash:r:" + readHexFile.getAbsolutePath() + ":i" +
                " -v"
        );

        try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(avrdude.getErrorStream())))
        {
            UsbSerialPortServer server = UsbSerialPortServer.startServer(
                    port,
                    avrdude.getInputStream(),
                    avrdude.getOutputStream());

            String line;
            while ((line = errorReader.readLine()) != null && server.isRunning()) {
                Log.d("UsbSerialPortTest", line);
            }
        }
        int exitCode = avrdude.waitFor();
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(readHexFile.exists());
        Log.d("UsbSerialPortTest", "exitCode = " + exitCode);
    }

    private UsbSerialPort getUsbSerialPort(Context context) throws IOException, InterruptedException, RemoteException {
        UiDevice device = UiDevice.getInstance(getInstrumentation());

        device.wakeUp();

        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        assertNotNull(usbManager);

        Semaphore receiverCompleteSemaphore = new Semaphore(1);
        receiverCompleteSemaphore.acquire();

        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!ACTION.equals(intent.getAction())) {
                    return;
                }
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                assertNotNull(device);
                assertTrue(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false));
                receiverCompleteSemaphore.release();
            }
        }, new IntentFilter(ACTION));

        UsbSerialDriver driver;
        while (true) {
            List<UsbSerialDriver> drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
            if (drivers.isEmpty()) {
                Thread.sleep(500);
                continue;
            }
            driver = drivers.get(0);
            assertNotNull(driver.getDevice());
            assertFalse(usbManager.hasPermission(driver.getDevice()));
            break;
        }

        usbManager.requestPermission(driver.getDevice(), PendingIntent.getBroadcast(context, 0, new Intent(ACTION), 0));

        BySelector permissionOk = By.pkg("com.android.systemui").text("OK");
        device.wait(Until.hasObject(permissionOk), -1);
        device.findObject(permissionOk).click();

        receiverCompleteSemaphore.acquire();

        UsbDeviceConnection connection = usbManager.openDevice(driver.getDevice());
        UsbSerialPort port = driver.getPorts().get(0);
        port.open(connection);

        return port;
    }
}
