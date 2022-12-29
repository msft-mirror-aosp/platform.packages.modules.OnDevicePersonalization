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

package com.android.odpclient;

import android.app.Activity;
import android.content.Context;
import android.ondevicepersonalization.OnDevicePersonalizationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.OutcomeReceiver;
import android.util.Log;
import android.view.SurfaceControlViewHost.SurfacePackage;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String TAG = "OdpClient";
    private OnDevicePersonalizationManager mOdpManager = null;

    private Button mGetVersionButton;
    private Button mBindButton;
    private SurfaceView mRenderedView;

    private Context mContext;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = getApplicationContext();
        if (mOdpManager == null) {
            mOdpManager = mContext.getSystemService(OnDevicePersonalizationManager.class);
        }
        mRenderedView = findViewById(R.id.rendered_view);
        mRenderedView.setVisibility(View.INVISIBLE);
        mGetVersionButton = findViewById(R.id.get_version_button);
        mBindButton = findViewById(R.id.bind_service_button);
        registerGetVersionButton();
        registerBindServiceButton();
    }

    private void registerGetVersionButton() {
        mGetVersionButton.setOnClickListener(v -> {
            if (mOdpManager == null) {
                makeToast("OnDevicePersonalizationManager is null");
            } else {
                makeToast(mOdpManager.getVersion());
            }
        });
    }
    private void registerBindServiceButton() {
        mBindButton.setOnClickListener(
                v -> {
                    if (mOdpManager == null) {
                        makeToast("OnDevicePersonalizationManager is null");
                    } else {
                        Bundle params = new Bundle();
                        params.putInt(
                                OnDevicePersonalizationManager.EXTRA_DISPLAY_ID,
                                getDisplay().getDisplayId());
                        params.putInt(
                                OnDevicePersonalizationManager.EXTRA_WIDTH_IN_PIXELS,
                                mRenderedView.getWidth());
                        params.putInt(
                                OnDevicePersonalizationManager.EXTRA_HEIGHT_IN_PIXELS,
                                mRenderedView.getHeight());
                        params.putBinder(
                                OnDevicePersonalizationManager.EXTRA_HOST_TOKEN,
                                mRenderedView.getHostToken());
                        mOdpManager.requestSurfacePackage(
                                "com.android.odpsamplenetwork",
                                params,
                                Executors.newSingleThreadExecutor(),
                                new OutcomeReceiver<Bundle, Exception>() {
                                    @Override
                                    public void onResult(Bundle bundle) {
                                        makeToast(
                                                "requestSurfacePackage() success: "
                                                + bundle.toString());
                                        SurfacePackage surfacePackage = bundle.getParcelable(
                                                OnDevicePersonalizationManager
                                                        .EXTRA_SURFACE_PACKAGE,
                                                SurfacePackage.class);
                                        new Handler(Looper.getMainLooper()).post(() -> {
                                            if (surfacePackage != null) {
                                                mRenderedView.setChildSurfacePackage(
                                                        surfacePackage);
                                            }
                                            mRenderedView.setZOrderOnTop(true);
                                            mRenderedView.setVisibility(View.VISIBLE);
                                        });
                                    }

                                    @Override
                                    public void onError(Exception e) {
                                        makeToast("requestSurfacePackage() error: " + e.toString());
                                    }
                                }
                        );
                    }
                });
    }

    private void makeToast(String message) {
        Log.i(TAG, message);
        runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
    }
}
