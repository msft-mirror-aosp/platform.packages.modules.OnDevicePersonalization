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

package com.example.odpclient;

import android.adservices.ondevicepersonalization.OnDevicePersonalizationConfigManager;
import android.adservices.ondevicepersonalization.OnDevicePersonalizationManager;
import android.adservices.ondevicepersonalization.SurfacePackageToken;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.OutcomeReceiver;
import android.os.PersistableBundle;
import android.util.Log;
import android.view.SurfaceControlViewHost.SurfacePackage;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class MainActivity extends Activity {
    private static final String TAG = "OdpClient";
    private OnDevicePersonalizationManager mOdpManager = null;
    private OnDevicePersonalizationConfigManager mOdpConfigManager = null;

    private EditText mTextBox;
    private Button mGetAdButton;
    private EditText mScheduleTrainingTextBox;
    private Button mScheduleTrainingButton;
    private Button mSetStatusButton;
    private EditText mReportConversionTextBox;
    private Button mReportConversionButton;
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
        if (mOdpConfigManager == null) {
            mOdpConfigManager = mContext.getSystemService(
                            OnDevicePersonalizationConfigManager.class);
        }
        mRenderedView = findViewById(R.id.rendered_view);
        mRenderedView.setVisibility(View.INVISIBLE);
        mGetAdButton = findViewById(R.id.get_ad_button);
        mScheduleTrainingButton = findViewById(R.id.schedule_training_button);
        mSetStatusButton = findViewById(R.id.set_status_button);
        mReportConversionButton = findViewById(R.id.report_conversion_button);
        mTextBox = findViewById(R.id.text_box);
        mScheduleTrainingTextBox = findViewById(R.id.schedule_training_text_box);
        mReportConversionTextBox = findViewById(R.id.report_conversion_text_box);
        registerGetAdButton();
        registerScheduleTrainingButton();
        registerSetStatusButton();
        registerReportConversionButton();
    }

    private void registerGetAdButton() {
        mGetAdButton.setOnClickListener(
                v -> makeRequest());
    }

    private void registerSetStatusButton() {
        mSetStatusButton.setOnClickListener(v -> setPersonalizationStatus());
    }

    private void registerReportConversionButton() {
        mReportConversionButton.setOnClickListener(v -> reportConversion());
    }

    private void makeRequest() {
        try {
            if (mOdpManager == null) {
                makeToast("OnDevicePersonalizationManager is null");
                return;
            }
            CountDownLatch latch = new CountDownLatch(1);
            Log.i(TAG, "Starting execute()");
            AtomicReference<SurfacePackageToken> slotResultHandle = new AtomicReference<>();
            PersistableBundle appParams = new PersistableBundle();
            appParams.putString("keyword", mTextBox.getText().toString());
            mOdpManager.execute(
                    ComponentName.createRelative(
                        "com.example.odpsamplenetwork",
                        "com.example.odpsamplenetwork.SampleService"),
                    appParams,
                    Executors.newSingleThreadExecutor(),
                    new OutcomeReceiver<List<SurfacePackageToken>, Exception>() {
                        @Override
                        public void onResult(List<SurfacePackageToken> result) {
                            Log.i(TAG, "execute() success: " + result.size());
                            if (result.size() > 0) {
                                slotResultHandle.set(result.get(0));
                            } else {
                                Log.e(TAG, "No results!");
                            }
                            latch.countDown();
                        }

                        @Override
                        public void onError(Exception e) {
                            makeToast("execute() error: " + e.toString());
                            latch.countDown();
                        }
                    });
            latch.await();
            Log.d(TAG, "wait success");
            mOdpManager.requestSurfacePackage(
                    slotResultHandle.get(),
                    mRenderedView.getHostToken(),
                    getDisplay().getDisplayId(),
                    mRenderedView.getWidth(),
                    mRenderedView.getHeight(),
                    Executors.newSingleThreadExecutor(),
                    new OutcomeReceiver<SurfacePackage, Exception>() {
                        @Override
                        public void onResult(SurfacePackage surfacePackage) {
                            Log.i(TAG,
                                    "requestSurfacePackage() success: "
                                    + surfacePackage.toString());
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
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error", e);
        }
    }

    private void registerScheduleTrainingButton() {
        mScheduleTrainingButton.setOnClickListener(
                v -> scheduleTraining());
    }

    private void scheduleTraining() {
        try {
            if (mOdpManager == null) {
                makeToast("OnDevicePersonalizationManager is null");
                return;
            }
            CountDownLatch latch = new CountDownLatch(1);
            Log.i(TAG, "Starting execute()");
            PersistableBundle appParams = new PersistableBundle();
            appParams.putString("schedule_training", mScheduleTrainingTextBox.getText().toString());
            mOdpManager.execute(
                    ComponentName.createRelative(
                            "com.example.odpsamplenetwork",
                            "com.example.odpsamplenetwork.SampleService"),
                    appParams,
                    Executors.newSingleThreadExecutor(),
                    new OutcomeReceiver<List<SurfacePackageToken>, Exception>() {
                        @Override
                        public void onResult(List<SurfacePackageToken> result) {
                            Log.i(TAG, "execute() success: " + result.size());
                            latch.countDown();
                        }

                        @Override
                        public void onError(Exception e) {
                            makeToast("execute() error: " + e.toString());
                            latch.countDown();
                        }
                    });
            latch.await();
        } catch (Exception e) {
            Log.e(TAG, "Error", e);
        }
    }

    private void reportConversion() {
        try {
            if (mOdpManager == null) {
                makeToast("OnDevicePersonalizationManager is null");
                return;
            }
            CountDownLatch latch = new CountDownLatch(1);
            Log.i(TAG, "Starting execute()");
            PersistableBundle appParams = new PersistableBundle();
            appParams.putString("conversion_ad_id", mReportConversionTextBox.getText().toString());
            mOdpManager.execute(
                    ComponentName.createRelative(
                            "com.example.odpsamplenetwork",
                            "com.example.odpsamplenetwork.SampleService"),
                    appParams,
                    Executors.newSingleThreadExecutor(),
                    new OutcomeReceiver<List<SurfacePackageToken>, Exception>() {
                        @Override
                        public void onResult(List<SurfacePackageToken> result) {
                            Log.i(TAG, "execute() success: " + result.size());
                            latch.countDown();
                        }

                        @Override
                        public void onError(Exception e) {
                            makeToast("execute() error: " + e.toString());
                            latch.countDown();
                        }
                    });
            latch.await();
        } catch (Exception e) {
            Log.e(TAG, "Error", e);
        }
    }

    private void setPersonalizationStatus() {
        if (mOdpConfigManager == null) {
            makeToast("OnDevicePersonalizationConfigManager is null");
        }
        boolean enabled = true;
        mOdpConfigManager.setPersonalizationEnabled(enabled,
                Executors.newSingleThreadExecutor(),
                new OutcomeReceiver<Void, Exception>() {
                    @Override
                    public void onResult(Void result) {
                        makeToast("Personalization status is set to " + enabled);
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        makeToast(error.getMessage());
                    }
                });
    }

    private void makeToast(String message) {
        Log.i(TAG, message);
        runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
    }
}
