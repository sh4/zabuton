#include <string>
#include <cstring>
#include <mutex>
#include <queue>
#include <jni.h>
#include <libavrdude.h>
#include <unistd.h>
#include <android/log.h>
#include "native-lib.h"

namespace
{

bool g_AvrdudeConfigInitialized;
std::mutex g_AvrdudeLogMutex;
std::queue<std::string> g_AvrdudeLogQueue;

template <typename T>
class ScopeGuard
{
    T&& lambda_;
public:
    explicit ScopeGuard(T&& lambda) : lambda_(std::forward<T>(lambda)) {
    }
    ScopeGuard(ScopeGuard&& that) : lambda_(std::move(that.lambda_)) {
    }
    ~ScopeGuard() {
        lambda_();
    }
};

template <typename T>
ScopeGuard<T> MakeScopeGuard(T&& lambda) {
    return ScopeGuard<T>(std::forward<T>(lambda));
}

class UsbSerialPort
{
	JNIEnv* env_;
	jobject port_;
	jmethodID close_;
	jmethodID read_;
	jmethodID write_;
	jmethodID setParameters_;
	jmethodID getCD_;
	jmethodID getCTS_;
	jmethodID getDSR_;
	jmethodID getDTR_;
	jmethodID getRI_;
	jmethodID getRTS_;
	jmethodID setDTR_;
	jmethodID setRTS_;
public:
	UsbSerialPort(JNIEnv* env, jobject port) : env_(env), port_(port)
	{
        jclass c = env->FindClass("com/hoho/android/usbserial/driver/UsbSerialPort");
        close_ = env->GetMethodID(c, "close", "()V");
		read_ = env->GetMethodID(c, "read", "([BI)I");
		write_ = env->GetMethodID(c, "write", "([BI)I");
		setParameters_ = env->GetMethodID(c, "setParameters", "(IIII)V");
		getCD_ = env->GetMethodID(c, "getCD", "()Z");
		getCTS_ = env->GetMethodID(c, "getCTS", "()Z");
		getDSR_ = env->GetMethodID(c, "getDSR", "()Z");
		getDTR_ = env->GetMethodID(c, "getDTR", "()Z");
		getRI_ = env->GetMethodID(c, "getRI", "()Z");
		getRTS_ = env->GetMethodID(c, "getRTS", "()Z");
		setDTR_ = env->GetMethodID(c, "setDTR", "(Z)V");
		setRTS_ = env->GetMethodID(c, "setRTS", "(Z)V");
	}

	void Close() {
		env_->CallVoidMethod(port_, close_);
		if (env_->ExceptionCheck()) {
			env_->ExceptionClear();
		}
	}

	int Read(unsigned char* buf, int length, int timeoutMilliseconds) {
		jbyteArray arr = env_->NewByteArray(length);
		auto cleanup = MakeScopeGuard([&] { env_->DeleteLocalRef(arr); });
		int readBytes = env_->CallIntMethod(port_, read_, arr, timeoutMilliseconds);
		if (readBytes < 0 || env_->ExceptionCheck()) {
			env_->ExceptionClear();
			return -1;
		}
		if (readBytes == 0) {
			return 0;
		}
		env_->GetByteArrayRegion(arr, 0, readBytes, reinterpret_cast<jbyte*>(buf));
		return readBytes;
	}

	int Write(const unsigned char* buf, int length, int timeoutMilliseconds) {
		jbyteArray arr = env_->NewByteArray(length);
		env_->SetByteArrayRegion(arr, 0, length, reinterpret_cast<const jbyte*>(buf));
		int writtenBytes = env_->CallIntMethod(port_, write_, arr, timeoutMilliseconds);
		if (writtenBytes < 0 || env_->ExceptionCheck()) {
			env_->ExceptionClear();
			return -1;
		}
		return writtenBytes;
	}

	void SetParameters(int baudRate, int dataBits, int stopBits, int parity) {
		env_->CallVoidMethod(port_, setParameters_, baudRate, dataBits, stopBits, parity);
	}
	void SetDTR(int enabled) { env_->CallVoidMethod(port_, setDTR_, enabled); }
	void SetRTS(int enabled) { env_->CallVoidMethod(port_, setRTS_, enabled); }

	int GetCD() { return env_->CallBooleanMethod(port_, getCD_); }
	int GetCTS() { return env_->CallBooleanMethod(port_, getCTS_); }
	int GetDSR() { return env_->CallBooleanMethod(port_, getDSR_); }
	int GetDTR() { return env_->CallBooleanMethod(port_, getDTR_); }
	int GetRI() { return env_->CallBooleanMethod(port_, getRI_); }
	int GetRTS() { return env_->CallBooleanMethod(port_, getRTS_); }
};

void AvrdudeLog(int loglevel, const char* message)
{
    std::lock_guard<std::mutex> lock(g_AvrdudeLogMutex);
    g_AvrdudeLogQueue.push(message);
}

void UpdateProgrammingProgress(int percent, double etime, char *hdr)
{
    static int done = 0;
    static int last = 0;
    int cnt = (percent>>1)*2;

    if (hdr) {
        std::string m;
        m += "\n";
        m += hdr;
        m += " | ";
        AvrdudeLog(0, m.c_str());

        last = 0;
        done = 0;
    }
    else {
        while ((cnt > last) && (done == 0)) {
            AvrdudeLog(0, "#");
            cnt -=  2;
        }
    }

    if ((percent == 100) && (done == 0)) {
        char t[32];
        snprintf(t, sizeof(t), "%0.2fs", etime);
        std::string m;
        m += " | 100% ";
        m += t;
        m += "\n\n";
        AvrdudeLog(0, m.c_str());
        last = 0;
        done = 1;
    }
    else
        last = (percent>>1)*2;    /* Make last a multiple of 2. */
}

void ProgrammingFirmware(UsbSerialPort& port, const char* hexFilePath)
{
    int r = 0;
    PROGRAMMER* pgm = nullptr;
    auto cleanupPgm = MakeScopeGuard([&] {
        if (pgm && pgm->teardown) {
            pgm->teardown(pgm);
        }
    });

    update_progress = UpdateProgrammingProgress;

    const char* programmerName = "avr109";
    pgm = locate_programmer(programmers, programmerName);
    if (!pgm) {
        return;
    }
    if (pgm->initpgm) {
        pgm->initpgm(pgm);
    }
    if (pgm->setup) {
        pgm->setup(pgm);
    }

    char mcuName[32] = "atmega32u4";
    AVRPART* avrpart = locate_part(part_list, mcuName);

    bool safemode = true;
    if (avrpart->flags & AVRPART_AVR32) {
        safemode = false;
    }
    if (avrpart->flags & (AVRPART_HAS_PDI | AVRPART_HAS_TPI)) {
        safemode = false;
    }

    if (avr_initmem(avrpart) != 0) {
        return;
    }
    r = pgm->open(pgm, reinterpret_cast<char*>(&port));
    if (r < 0) {
        pgm->ppidata = 0;
        return;
    }

    pgm->enable(pgm);

    pgm->rdy_led(pgm, OFF);
    pgm->err_led(pgm, OFF);
    pgm->pgm_led(pgm, OFF);
    pgm->vfy_led(pgm, OFF);

    r = pgm->initialize(pgm, avrpart);
    if (r < 0) {
        return;
    }

    pgm->rdy_led(pgm, ON);

#if 0
    if (!(avrpart->flags & AVRPART_AVR32)) {
        int attempt = 0;
        int waittime = 10000;       /* 10 ms */
        usleep(waittime);
        r = avr_signature(pgm, avrpart);
        if (r != 0) {
            return;
        }
        AVRMEM* sig = avr_locate_mem(avrpart, "signature");
    }
#endif

    unsigned char safemode_lfuse = 0xff;
    unsigned char safemode_hfuse = 0xff;
    unsigned char safemode_efuse = 0xff;
    unsigned char safemode_fuse = 0xff;

    if (safemode) {
        r = safemode_readfuses(&safemode_lfuse, &safemode_hfuse,
                           &safemode_efuse, &safemode_fuse, pgm, avrpart);
        if (r != 0) {
            if (r == -5) {
                // continue
            } else {
                return;
            }
        } else {
            //Save the fuses as default
            safemode_memfuses(1, &safemode_lfuse, &safemode_hfuse, &safemode_efuse, &safemode_fuse);
        }
    }

    std::string argsBuilder;
    argsBuilder += "flash:w:";
    argsBuilder += hexFilePath;

    LISTID updates = lcreat(NULL, 0);
    char parseOptArgs[PATH_MAX];
    std::strncpy(parseOptArgs, argsBuilder.c_str(), argsBuilder.size() + 1);
    {
        UPDATE* upd = parse_op(parseOptArgs);
        ladd(updates, upd);
        if (upd->op == DEVICE_WRITE) {
            upd = dup_update(upd);
            upd->op = DEVICE_VERIFY;
            ladd(updates, upd);
        }
    }

    updateflags uflags = UF_AUTO_ERASE;
    bool erase = false;
    if ((avrpart->flags & AVRPART_HAS_PDI) && pgm->page_erase != NULL) {
        //avrdude_message(MSG_INFO, "%s: NOTE: Programmer supports page erase for Xmega devices.\n"
        //                          "%sEach page will be erased before programming it, but no chip erase is performed.\n"
        //                          "%sTo disable page erases, specify the -D option; for a chip-erase, use the -e option.\n",
        //                progname, progbuf, progbuf);
    } else {
        uflags = UF_NONE;
        for (LNODEID e = lfirst(updates); e; e = lnext(e)) {
            UPDATE* update = reinterpret_cast<UPDATE *>(ldata(e));
            AVRMEM* m = avr_locate_mem(avrpart, update->memtype);
            if (!m) {
                continue;
            }
            if (strcasecmp(m->desc, "flash") == 0 && update->op == DEVICE_WRITE) {
                erase = true;
                break;
            }
        }
    }

    if (erase) {
        r = avr_chip_erase(pgm, avrpart);
        if (r) {
            return;
        }
    }

    for (LNODEID e = lfirst(updates); e; e = lnext(e)) {
        UPDATE* update = reinterpret_cast<UPDATE*>(ldata(e));
        r = do_op(pgm, avrpart, update, uflags);
        if (r) {
            break;
        }
    }

#if 0
    if (safemode) {
        unsigned char safemodeafter_lfuse = 0xff;
        unsigned char safemodeafter_hfuse = 0xff;
        unsigned char safemodeafter_efuse = 0xff;
        unsigned char safemodeafter_fuse  = 0xff;
        unsigned char failures = 0;
    }
#endif

    pgm->powerdown(pgm);
    pgm->disable(pgm);
    pgm->rdy_led(pgm, OFF);
    pgm->close(pgm);

    ldestroy_cb(updates, reinterpret_cast<void(*)(void*)>(free_update));
    updates = nullptr;
}

}

extern "C"
{

void UsbSerialPort_Close(UsbSerialPortHandle handle)
{
	UsbSerialPort* port = reinterpret_cast<UsbSerialPort*>(handle);
	return port->Close();
}

int UsbSerialPort_Read(UsbSerialPortHandle handle, unsigned char* buf, int length, int timeoutMilliseconds)
{
	UsbSerialPort* port = reinterpret_cast<UsbSerialPort*>(handle);
	return port->Read(buf, length, timeoutMilliseconds);
}

int UsbSerialPort_Write(UsbSerialPortHandle handle, const unsigned char* buf, int length, int timeoutMilliseconds)
{
	UsbSerialPort* port = reinterpret_cast<UsbSerialPort*>(handle);
	return port->Write(buf, length, timeoutMilliseconds);
}

void UsbSerialPort_SetParameters(UsbSerialPortHandle handle, int baudRate, int dataBits, int stopBits, int parity)
{
	UsbSerialPort* port = reinterpret_cast<UsbSerialPort*>(handle);
	port->SetParameters(baudRate, dataBits, stopBits, parity);
}

void UsbSerialPort_SetDTR(UsbSerialPortHandle handle, int enabled)
{
	UsbSerialPort* port = reinterpret_cast<UsbSerialPort*>(handle);
	port->SetDTR(enabled);
}

void UsbSerialPort_SetRTS(UsbSerialPortHandle handle, int enabled)
{
	UsbSerialPort* port = reinterpret_cast<UsbSerialPort*>(handle);
	port->SetDTR(enabled);
}

int UsbSerialPort_GetCD(UsbSerialPortHandle handle)
{
	UsbSerialPort* port = reinterpret_cast<UsbSerialPort*>(handle);
	return port->GetCD();
}

int UsbSerialPort_GetCTS(UsbSerialPortHandle handle)
{
	UsbSerialPort* port = reinterpret_cast<UsbSerialPort*>(handle);
	return port->GetCTS();
}

int UsbSerialPort_GetDSR(UsbSerialPortHandle handle)
{
	UsbSerialPort* port = reinterpret_cast<UsbSerialPort*>(handle);
	return port->GetDSR();
}

int UsbSerialPort_GetDTR(UsbSerialPortHandle handle)
{
	UsbSerialPort* port = reinterpret_cast<UsbSerialPort*>(handle);
	return port->GetDTR();
}

int UsbSerialPort_GetRI(UsbSerialPortHandle handle)
{
	UsbSerialPort* port = reinterpret_cast<UsbSerialPort*>(handle);
	return port->GetRI();
}

int UsbSerialPort_GetRTS(UsbSerialPortHandle handle)
{
	UsbSerialPort* port = reinterpret_cast<UsbSerialPort*>(handle);
	return port->GetRTS();
}

void UsbSerialPort_Log(int loglevel, const char* message)
{
    AvrdudeLog(loglevel, message);
}

}

////////////////////////////////////////////////////////////////////////////////////////////////////

extern "C"
JNIEXPORT void JNICALL
Java_io_github_sh4_zabuton_programmer_Avrdude_programming(
        JNIEnv *env,
        jclass /*type*/,
        jobject serialPort,
        jstring configFilePath_,
        jstring hexFilePath_)
{
    const char *configFilePath = env->GetStringUTFChars(configFilePath_, 0);
    const char *hexFilePath = env->GetStringUTFChars(hexFilePath_, 0);
	auto cleanup = MakeScopeGuard([&] {
        env->ReleaseStringUTFChars(hexFilePath_, hexFilePath);
	    env->ReleaseStringUTFChars(configFilePath_, configFilePath);
	});

	if (!g_AvrdudeConfigInitialized) {
		init_config();
		if (read_config(configFilePath)) {
			return;
		}
		g_AvrdudeConfigInitialized = true;
	}

    AvrdudeLog(0, "\n");

	UsbSerialPort port(env, serialPort);
    ProgrammingFirmware(port, hexFilePath);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_io_github_sh4_zabuton_programmer_Avrdude_dequeueMessage(JNIEnv *env,
        jclass /*type*/)
{
    std::lock_guard<std::mutex> lock(g_AvrdudeLogMutex);
    if (g_AvrdudeLogQueue.empty()) {
        return nullptr;
    }
    jstring message = env->NewStringUTF(g_AvrdudeLogQueue.front().c_str());
    g_AvrdudeLogQueue.pop();
    return message;
}
