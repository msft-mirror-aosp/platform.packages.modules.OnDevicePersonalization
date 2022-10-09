/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ondevicepersonalization.services.download.mdd;

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateVoidFuture;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.google.android.libraries.mobiledatadownload.DownloadException;
import com.google.android.libraries.mobiledatadownload.downloader.DownloadRequest;
import com.google.android.libraries.mobiledatadownload.downloader.FileDownloader;
import com.google.android.libraries.mobiledatadownload.file.Opener;
import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.android.libraries.mobiledatadownload.file.openers.WriteStreamOpener;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.concurrent.Executor;

/**
 * A {@link FileDownloader} that "downloads" by copying the file from the local folder.
 *
 * <p>Note that LocalFileDownloader ignores DownloadConditions.
 */
public final class LocalFileDownloader implements FileDownloader {

    private static final String TAG = "LocalFileDownloader";

    private final Executor mBackgroundExecutor;
    private final SynchronousFileStorage mFileStorage;
    private final Context mContext;

    public LocalFileDownloader(
            SynchronousFileStorage fileStorage, ListeningExecutorService executor,
            Context context) {
        this.mFileStorage = fileStorage;
        this.mBackgroundExecutor = executor;
        this.mContext = context;
    }

    /**
     * Performs a localFile download for the given request
     */
    @Override
    public ListenableFuture<Void> startDownloading(DownloadRequest downloadRequest) {
        return Futures.submitAsync(() -> startDownloadingInternal(downloadRequest),
                mBackgroundExecutor);
    }

    private ListenableFuture<Void> startDownloadingInternal(DownloadRequest downloadRequest) {
        Uri fileUri = downloadRequest.fileUri();
        String urlToDownload = downloadRequest.urlToDownload();
        Log.d(TAG, "startDownloading; fileUri: " + fileUri + "; urlToDownload: " + urlToDownload);

        Uri uriToDownload = Uri.parse(urlToDownload);
        if (uriToDownload == null) {
            Log.e(TAG, ": Invalid urlToDownload " + urlToDownload);
            return immediateFailedFuture(new IllegalArgumentException("Invalid urlToDownload"));
        }
        String fileName = Paths.get(uriToDownload.getPath()).getFileName().toString();
        try {
            Opener<OutputStream> writeStreamOpener = WriteStreamOpener.create();
            long writtenBytes;
            try (OutputStream out = mFileStorage.open(fileUri, writeStreamOpener)) {
                InputStream in = mContext.getResources().openRawResource(
                        mContext.getResources().getIdentifier(
                                fileName.substring(0, fileName.indexOf('.')), "raw",
                                mContext.getPackageName()));
                writtenBytes = ByteStreams.copy(in, out);
            }
            Log.d(TAG,
                    "File URI " + fileUri + " download complete, writtenBytes: %d" + writtenBytes);
        } catch (IOException e) {
            Log.e(TAG, "%s: startDownloading got exception", e);
            return immediateFailedFuture(
                    DownloadException.builder()
                            .setDownloadResultCode(
                                    DownloadException.DownloadResultCode
                                            .ANDROID_DOWNLOADER_HTTP_ERROR)
                            .build());
        }

        return immediateVoidFuture();
    }
}
