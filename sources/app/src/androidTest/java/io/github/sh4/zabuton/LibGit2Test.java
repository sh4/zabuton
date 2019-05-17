package io.github.sh4.zabuton;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.github.sh4.zabuton.git.LibGit2;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LibGit2Test {
    static {
        System.loadLibrary("native-lib");
    }

    @Test
    public void init() {
        LibGit2.init();
        assertTrue(true);
    }
}
