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
import android.os.IBinder;
import android.util.Log;
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
        mRenderedView.setZOrderOnTop(true);
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
                        mOdpManager.init(
                                new Bundle(),
                                Executors.newSingleThreadExecutor(),
                                new OnDevicePersonalizationManager.InitCallback() {
                                    @Override
                                    public void onSuccess(IBinder token) {
                                        makeToast("init() success: " + token.toString());
                                    }

                                    @Override
                                    public void onError(int errorCode) {
                                        makeToast("init() error: " + errorCode);
                                    }
                                });
                    }
                });
    }

    private void makeToast(String message) {
        Log.i(TAG, message);
        runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
    }
}
