package com.tapflow.core.service.shizuku;

import android.os.ParcelFileDescriptor;

interface IShizukuShellService {
    int execCommand(String command);
    String execCommandOutput(String command);
    ParcelFileDescriptor captureScreencapPipe();
    void destroy();
}
