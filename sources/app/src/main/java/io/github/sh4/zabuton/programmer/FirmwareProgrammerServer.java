package io.github.sh4.zabuton.programmer;

import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public class FirmwareProgrammerServer {
    private static final String TAG = FirmwareProgrammerServer.class.getSimpleName();
    private static final int MAX_BUFFER_SIZE = 1024 * 128;
    private static final int DEFAULT_TIMEOUT_MILLISECONDS = 1000 * 5;

    private class Error {
        static final int GENERIC_ERROR = -1024;
        static final int LAST_ERROR_IS_EMPTY = GENERIC_ERROR - 1;
        static final int INEFFICIENT_BUFFER = GENERIC_ERROR - 2;
        static final int BUFFER_NULL = GENERIC_ERROR - 3;
    }

    private class Command {
        static final int NOOP = 0;
        static final int CLOSE = 1;
        static final int READ = 2;
        static final int WRITE = 3;
        static final int SET_PARAMETERS = 4;
        static final int GET_CD = 5;
        static final int GET_CTS = 6;
        static final int GET_DSR = 7;
        static final int GET_DTR = 8;
        static final int GET_RI = 9;
        static final int GET_RTS = 10;
        static final int SET_DTR = 11;
        static final int SET_RTS = 12;
        static final int GET_LAST_ERROR = 13;
        static final int SET_PROGRESS = 14;
    }

    private final UsbSerialPort usbSerialPort;
    private final DataInputStream inputStream;
    private final DataOutputStream outputStream;
    private final AtomicInteger percentProgress;
    private final AtomicInteger elapsedTimeMilliseconds;
    private Throwable lastException;
    private Thread serverThread;
    private boolean serverClosed;


    private FirmwareProgrammerServer(UsbSerialPort usbSerialPort, InputStream input, OutputStream output) {
        this.usbSerialPort = usbSerialPort;
        this.inputStream = new DataInputStream(input);
        this.outputStream = new DataOutputStream(output);
        this.percentProgress = new AtomicInteger();
        this.elapsedTimeMilliseconds = new AtomicInteger();
    }

    public int getPercentProgress() {
        return percentProgress.get();
    }

    public int getElapsedTimeMilliseconds() {
        return elapsedTimeMilliseconds.get();
    }

    public boolean isRunning() {
        return !serverClosed;
    }

    public void close() throws IOException, InterruptedException {
        inputStream.close();
        outputStream.close();
        serverClosed = true;
        serverThread.wait();
        serverThread = null;
        serverClosed = false;
    }

    void start() {
        if (serverThread != null) {
            return;
        }
        serverThread = new Thread(() ->
        {
            try {
                percentProgress.set(0);
                elapsedTimeMilliseconds.set(0);
                while (!serverClosed) {
                    if (!executeCommand(inputStream, outputStream)) {
                        break;
                    }
                    outputStream.flush();
                }
            } catch (IOException e) {
                Log.d(TAG, "Server socket error occurred: " + e.toString());
            }
        });
        serverThread.start();
    }

    private boolean executeCommand(DataInputStream inputStream, DataOutputStream outputStream) throws IOException {
        switch (inputStream.readByte()) {
            // NOOP C:[Command 0 (1byte)] S:[Response 0 (4byte)]
            case Command.NOOP:
                outputStream.writeInt(0);
                break;
            // CLOSE C:[Command 1 (1byte)] S:[Response 0 or Error.GENERIC_ERROR (4byte)]
            case Command.CLOSE:
                commandClose(inputStream, outputStream);
                return false;
            // READ C:[Command 2 (1byte)][Buffer Length (4byte)][TimeoutMilliseconds (4byte)]
            // S:[Response Error.GENERIC_ERROR or ReadByteLength (4byte)][ReadBytes..(ReadByteLength bytes)]
            case Command.READ:
                commandRead(inputStream, outputStream);
                break;
            // WRITE C:[Command 3 (1byte)][WriteByteLength (4byte)][WriteBytes... (WriteByteLength)]
            // S:[Response Error.GENERIC_ERROR or WrittenByteLength]
            case Command.WRITE:
                commandWrite(inputStream, outputStream);
                break;
            // SET_PARAMETERS C:[Command 4 (1byte)][baudRate (4byte)][dataBits (4byte)][stopBits (4byte)][parity (4byte)] S:[Response 0 or Error.GENERIC_ERROR]
            case Command.SET_PARAMETERS:
                commandSetParameters(inputStream, outputStream);
                break;
            // GET_CD C:[Command 5 (1byte)] S:[Response 1 or 0 or Error.GENERIC_ERROR (4byte)]
            case Command.GET_CD:
                commandGetCD(outputStream);
                break;
            // GET_CTS C:[Command 6 (1byte)] S:[Response 1 or 0 or Error.GENERIC_ERROR (4byte)]
            case Command.GET_CTS:
                commandGetCTS(outputStream);
                break;
            // GET_DSR C:[Command 7 (1byte)] S:[Response 1 or 0 or Error.GENERIC_ERROR (4byte)]
            case Command.GET_DSR:
                commandGetDSR(outputStream);
                break;
            // GET_DTR C:[Command 8 (1byte)] S:[Response 1 or 0 or Error.GENERIC_ERROR (4byte)]
            case Command.GET_DTR:
                commandGetDTR(outputStream);
                break;
            // GET_RI C:[Command 9 (1byte)] S:[Response 1 or 0 or Error.GENERIC_ERROR (4byte)]
            case Command.GET_RI:
                commandGetRI(outputStream);
                break;
            // GET_RTS C:[Command 10 (1byte)] S:[Response 1 or 0 or Error.GENERIC_ERROR (4byte)]
            case Command.GET_RTS:
                commandGetRTS(outputStream);
                break;
            // SET_DTR C:[Command 11 (1byte)][DTR value 0 or 1 (1byte)] S:[Response 0 or Error.GENERIC_ERROR]
            case Command.SET_DTR:
                commandSetDTR(inputStream, outputStream);
                break;
            // SET_RTS C:[Command 12 (1byte)][RTS value 0 or 1 (1byte)] S:[Response 0 or Error.GENERIC_ERROR]
            case Command.SET_RTS:
                commandSetRTS(inputStream, outputStream);
                break;
            // GET_LAST_ERROR C:[Command 13 (1byte)][client buffer size (4byte)]
            // * Client buffer size is good
            // S:[Response 0][ErrorStringLength (4byte)][ErrorString... (ErrorStringBytes, not terminated null-char)]
            // * Last Exception is null
            // S:[Response Error.GENERIC_ERROR]
            // * Client buffer size is inefficient
            // S:[Response -2][ErrorStringLength (4byte)] (inefficient buffer size)
            case Command.GET_LAST_ERROR:
                commandGetLastError(inputStream, outputStream);
                break;
            // SET_PROGRESS C:[Command 13(1byte)][progressPercent (1byte)][elapsedTimeMilliseconds (4byte)]
            // S:[Response 0 or Error.GENERIC_ERROR (4byte)]
            case Command.SET_PROGRESS:
                commandSetProgress(inputStream, outputStream);
                break;
            default:
                break;
        }
        return true;
    }

    private void commandSetProgress(DataInputStream inputStream, DataOutputStream outputStream) throws IOException {
        try {
            int progress = inputStream.readByte(); // 0-100
            int elapsed = inputStream.readInt();
            Log.d(TAG, "commandSetProgress: percentProgress=" + progress + ", elapsed=" + elapsed + "ms");
            percentProgress.set(progress);
            elapsedTimeMilliseconds.set(elapsed);
            outputStream.writeInt(0);
        } catch (IOException e) {
            setLastException(outputStream, e);
        }
    }


    private void commandGetLastError(DataInputStream inputStream, DataOutputStream outputStream) throws IOException {
        Log.d(TAG, "commandGetLastError");
        if (lastException == null) {
            outputStream.writeInt(Error.LAST_ERROR_IS_EMPTY);
            return;
        }
        String exceptionMessage = lastException.toString();
        byte[] bytes = exceptionMessage.getBytes(StandardCharsets.UTF_8);
        int clientBufferSize = inputStream.readInt();
        if (bytes.length > clientBufferSize) {
            outputStream.writeInt(Error.INEFFICIENT_BUFFER);
            outputStream.writeInt(bytes.length);
            return;
        }
        outputStream.writeInt(bytes.length);
        outputStream.write(bytes);
    }

    private void commandSetRTS(DataInputStream inputStream, DataOutputStream outputStream) throws IOException {
        Log.d(TAG, "commandSetRTS");
        try {
            usbSerialPort.setRTS(inputStream.readByte() == 1);
            outputStream.writeInt(0);
        } catch (IOException e) {
            setLastException(outputStream, e);
        }
    }

    private void commandSetDTR(DataInputStream inputStream, DataOutputStream outputStream) throws IOException {
        Log.d(TAG, "commandSetDTR");
        try {
            usbSerialPort.setDTR(inputStream.readByte() == 1);
            outputStream.writeInt(0);
        } catch (IOException e) {
            setLastException(outputStream, e);
        }
    }

    private void commandGetRTS(DataOutputStream outputStream) throws IOException {
        Log.d(TAG, "commandGetRTS");
        try {
            outputStream.writeInt(usbSerialPort.getRTS() ? 1 : 0);
        } catch (IOException e) {
            setLastException(outputStream, e);
        }
    }

    private void commandGetRI(DataOutputStream outputStream) throws IOException {
        Log.d(TAG, "commandGetRI");
        try {
            outputStream.writeInt(usbSerialPort.getRI() ? 1 : 0);
        } catch (IOException e) {
            setLastException(outputStream, e);
        }
    }

    private void commandGetDTR(DataOutputStream outputStream) throws IOException {
        Log.d(TAG, "commandGetDTR");
        try {
            outputStream.writeInt(usbSerialPort.getDTR() ? 1 : 0);
        } catch (IOException e) {
            setLastException(outputStream, e);
        }
    }

    private void commandGetDSR(DataOutputStream outputStream) throws IOException {
        Log.d(TAG, "commandGetDSR");
        try {
            outputStream.writeInt(usbSerialPort.getDSR() ? 1 : 0);
        } catch (IOException e) {
            setLastException(outputStream, e);
        }
    }

    private void commandGetCTS(DataOutputStream outputStream) throws IOException {
        Log.d(TAG, "commandGetCTS");
        try {
            outputStream.writeInt(usbSerialPort.getCTS() ? 1 : 0);
        } catch (IOException e) {
            setLastException(outputStream, e);
        }
    }

    private void commandGetCD(DataOutputStream outputStream) throws IOException {
        Log.d(TAG, "commandGetCD");
        try {
            outputStream.writeInt(usbSerialPort.getCD() ? 1 : 0);
        } catch (IOException e) {
            setLastException(outputStream, e);
        }
    }

    private void commandSetParameters(DataInputStream inputStream, DataOutputStream outputStream) throws IOException {
        try {
            int baudRate = inputStream.readInt();
            int dataBits = inputStream.readInt();
            int stopBits = inputStream.readInt();
            int parity = inputStream.readInt();
            Log.d(TAG, "commandSetParameters: " +
                    "baudRate=" + baudRate
                    + ", dataBits=" + dataBits
                    + ", stopBits=" + stopBits
                    + ", parity=" + parity);
            usbSerialPort.setParameters(baudRate, dataBits, stopBits, parity);
            outputStream.writeInt(0);
        } catch (IOException e) {
            setLastException(outputStream, e);
        }
    }

    private void commandWrite(DataInputStream inputStream, DataOutputStream outputStream) throws IOException {
        try {
            int writeBytesLength = inputStream.readInt();
            int writtenBytes = 0;
            if (writeBytesLength > 0) {
                byte[] buffer = new byte[writeBytesLength];
                int readBytes = 0;
                while (readBytes < writeBytesLength) {
                    readBytes += inputStream.read(buffer, readBytes, writeBytesLength - readBytes);
                }
                writtenBytes = usbSerialPort.write(buffer, DEFAULT_TIMEOUT_MILLISECONDS);
            }
            Log.d(TAG, "commandWrite bufferLength=" + writeBytesLength + ", writtenBytes=" + writtenBytes);
            outputStream.writeInt(writtenBytes);
        } catch (IOException e) {
            setLastException(outputStream, e);
        }
    }

    private void commandRead(DataInputStream inputStream, DataOutputStream outputStream) throws IOException {
        try {
            int bufferLength = inputStream.readInt();
            int timeoutMilliseconds = inputStream.readInt();

            byte[] buffer = new byte[Math.min(bufferLength, MAX_BUFFER_SIZE)];
            int readBytes = usbSerialPort.read(buffer, timeoutMilliseconds);
            Log.d(TAG, "commandRead: bufferLength=" + bufferLength + ", readBytes=" + readBytes + ", timeout=" + timeoutMilliseconds);
            outputStream.writeInt(readBytes);
            if (readBytes > 0) {
                outputStream.write(buffer, 0, readBytes);
            }
        } catch (IOException e) {
            setLastException(outputStream, e);
        }
    }

    private void commandClose(DataInputStream inputStream, DataOutputStream outputStream) throws IOException {
        Log.d(TAG, "commandClose");
        try {
            usbSerialPort.close();
            outputStream.writeInt(0);
            outputStream.close();
            inputStream.close();
            serverClosed = true;
        } catch (IOException e) {
            setLastException(outputStream, e);
        }
    }

    private void setLastException(DataOutputStream outputStream, IOException e) throws IOException {
        Log.d(TAG, "setLastException: " + e.toString());
        lastException = e;
        outputStream.writeInt(Error.GENERIC_ERROR);
    }

    public static FirmwareProgrammerServer startServer(UsbSerialPort openedUsbSerialPort, InputStream input, OutputStream output) throws IOException {
        FirmwareProgrammerServer server = new FirmwareProgrammerServer(openedUsbSerialPort, input, output);
        server.start();
        Log.d(TAG, "server started.");
        return server;
    }
}
