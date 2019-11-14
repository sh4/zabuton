package io.github.sh4.zabuton.programmer

import com.hoho.android.usbserial.driver.UsbId
import java.io.File

interface FirmwreDownloadParameter {
    val output: File
    val progress: (progress: Float) -> Unit
}

interface FirmwareUploadParameter {
    val input: File
    val progress: (progress: Float) -> Unit
}

interface FirmwareProgrammer {
    fun download(parameter: FirmwreDownloadParameter)
    fun upload(parameter: FirmwareUploadParameter)
}

interface DeviceInformation {
    val vendorId: Int
    val productId: Int
}

interface FirmwareProgrammerResolver {
    fun resolve(deviceInfo: DeviceInformation): FirmwareProgrammer?
}

class AvrdudeProgrammer : FirmwareProgrammer {
    companion object {
        const val d = "hpge";
    }

    override fun download(parameter: FirmwreDownloadParameter) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun upload(parameter: FirmwareUploadParameter) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

class FirmwareProgrammerReoslverImpl : FirmwareProgrammerResolver {
    companion object {
        private val ARUDINO_PRODUCT_ID: IntArray = intArrayOf(
                UsbId.ARDUINO_UNO,
                UsbId.ARDUINO_UNO_R3,
                UsbId.ARDUINO_MEGA_2560,
                UsbId.ARDUINO_MEGA_2560_R3,
                UsbId.ARDUINO_SERIAL_ADAPTER,
                UsbId.ARDUINO_SERIAL_ADAPTER_R3,
                UsbId.ARDUINO_MEGA_ADK,
                UsbId.ARDUINO_MEGA_ADK_R3,
                UsbId.ARDUINO_LEONARDO,
                UsbId.ARDUINO_MICRO,
                UsbId.LEONARD_PRO_MICRO)
    }

    override fun resolve(deviceInfo: DeviceInformation): FirmwareProgrammer? {
        if (deviceInfo.productId == UsbId.VENDOR_ARDUINO && ARUDINO_PRODUCT_ID.contains(deviceInfo.vendorId)) {
            return AvrdudeProgrammer()
        }
        return null
    }
}
