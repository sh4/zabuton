package io.github.sh4.zabuton

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.RemoteException
import android.system.Os
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import io.github.sh4.zabuton.programmer.FirmwareProgrammerServer
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.Semaphore

private val TAG = AvrdudeProgrammerManualTest::class.java.simpleName
private val ACTION = AvrdudeProgrammerManualTest::class.java.name + ".USB_PERMISSION"

@RunWith(AndroidJUnit4::class)
class AvrdudeProgrammerManualTest {
    @Test
    @Throws(Exception::class)
    fun testReadHexFile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val port = getUsbSerialPort(context)
        Assert.assertNotNull(port)
        val avrdudeFile = File(context.filesDir, "avrdude")
        val outputDir = context.cacheDir
        val readHexFile = File(outputDir, "backup.hex")
        if (readHexFile.exists()) {
            readHexFile.delete()
        }
        Os.chmod(avrdudeFile.absolutePath, 448)
        Assert.assertTrue(avrdudeFile.canExecute())
        val avrdude = Runtime.getRuntime().exec(avrdudeFile.absolutePath +
                " -C " + avrdudeFile.parent + "/avrdude.conf" +  // config
                " -c avr109" +  // programmer-id
                " -p atmega32u4" +  // partno
                " -U flash:r:" + readHexFile.absolutePath + ":i" +
                " -v"
        )
        BufferedReader(InputStreamReader(avrdude.errorStream)).use { errorReader ->
            val server = FirmwareProgrammerServer.startServer(
                    port,
                    avrdude.inputStream,
                    avrdude.outputStream)
            var line: String?
            while (errorReader.readLine().also { line = it } != null && server.isRunning) {
                Log.d("UsbSerialPortTest", line)
            }
        }
        val exitCode = avrdude.waitFor()
        Assert.assertEquals(0, exitCode.toLong())
        Assert.assertTrue(readHexFile.exists())
        Log.d("UsbSerialPortTest", "exitCode = $exitCode")
    }

    @Throws(IOException::class, InterruptedException::class, RemoteException::class)
    private fun getUsbSerialPort(context: Context): UsbSerialPort {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.wakeUp()

        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        Assert.assertNotNull(usbManager)

        val receiverCompleteSemaphore = Semaphore(1)
        receiverCompleteSemaphore.acquire()
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (ACTION != intent.action) {
                    return
                }
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                Assert.assertNotNull(device)
                Assert.assertTrue(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                receiverCompleteSemaphore.release()
            }
        }, IntentFilter(ACTION))

        val driver: UsbSerialDriver
        while (true) {
            val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            if (drivers.isEmpty()) {
                Thread.sleep(500)
                continue
            }
            driver = drivers[0]
            Assert.assertNotNull(driver.device)
            Assert.assertFalse(usbManager.hasPermission(driver.device))
            break
        }

        usbManager.requestPermission(driver.device, PendingIntent.getBroadcast(context, 0, Intent(ACTION), 0))

        val permissionOk = By.pkg("com.android.systemui").text("OK")
        device.wait(Until.hasObject(permissionOk), -1)
        device.findObject(permissionOk).click()
        receiverCompleteSemaphore.acquire()

        val connection = usbManager.openDevice(driver.device)
        val port = driver.ports[0]
        port.open(connection)

        return port
    }
}