//
// Created by user on 2019/04/04.
//

#ifndef ANDROIDAPP_NATIVE_LIB_H
#define ANDROIDAPP_NATIVE_LIB_H

#ifdef __cplusplus
extern "C"
{
#endif

typedef void* UsbSerialPortHandle;

void UsbSerialPort_Close(UsbSerialPortHandle handle);

int UsbSerialPort_Read(UsbSerialPortHandle handle, unsigned char* buf, int length, int timeoutMilliseconds);
int UsbSerialPort_Write(UsbSerialPortHandle handle, const unsigned char* buf, int length, int timeoutMilliseconds);

void UsbSerialPort_SetParameters(UsbSerialPortHandle handle, int baudRate, int dataBits, int stopBits, int parity);
void UsbSerialPort_SetDTR(UsbSerialPortHandle handle, int enabled);
void UsbSerialPort_SetRTS(UsbSerialPortHandle handle, int enabled);

int UsbSerialPort_GetCD(UsbSerialPortHandle handle);
int UsbSerialPort_GetCTS(UsbSerialPortHandle handle);
int UsbSerialPort_GetDSR(UsbSerialPortHandle handle);
int UsbSerialPort_GetDTR(UsbSerialPortHandle handle);
int UsbSerialPort_GetRI(UsbSerialPortHandle handle);
int UsbSerialPort_GetRTS(UsbSerialPortHandle handle);

void UsbSerialPort_Log(int loglevel, const char* message);

#ifdef __cplusplus
}
#endif

#endif //ANDROIDAPP_NATIVE_LIB_H
