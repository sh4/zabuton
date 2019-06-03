package io.github.sh4.zabuton.util;

import android.content.Context;
import android.content.res.AssetManager;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static android.content.Context.MODE_PRIVATE;

public final class ContextUtil {
    public static File writeToFilesDir(Context context, String relativePath) {
        File file = new File(context.getFilesDir(), relativePath.replace('/', '-'));
        if (!file.exists()) {
            try (InputStream inStream = context.getAssets().open(relativePath, AssetManager.ACCESS_BUFFER);
                 OutputStream outStream = context.openFileOutput(file.getName(), MODE_PRIVATE))
            {
                IOUtils.copy(inStream, outStream);
            } catch (IOException e) {
                return null;
            }
        }
        return file;
    }}
