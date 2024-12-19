/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.federatedcompute.services.security;

import static com.android.federatedcompute.services.http.HttpClientUtil.ODP_AUTHENTICATION_KEY;
import static com.android.federatedcompute.services.http.HttpClientUtil.ODP_AUTHORIZATION_KEY;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.common.FederatedComputeExecutors;
import com.android.federatedcompute.services.common.FlagsFactory;
import com.android.federatedcompute.services.common.TrainingEventLogger;
import com.android.federatedcompute.services.data.FederatedComputeDbHelper;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.odp.module.common.Clock;
import com.android.odp.module.common.MonotonicClock;
import com.android.odp.module.common.data.OdpAuthorizationToken;
import com.android.odp.module.common.data.OdpAuthorizationTokenDao;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.internal.federatedcompute.v1.AuthenticationMetadata;

import org.json.JSONArray;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/** Manages the details of authenticating with remote server. */
public class AuthorizationContext {

    private static final String TAG = AuthorizationContext.class.getSimpleName();

    @NonNull private final String mOwnerId;
    @NonNull private final String mOwnerCert;

    @GuardedBy("this")
    @Nullable
    private List<String> mAttestationRecord = null;

    @GuardedBy("this")
    private int mTryCount = 1;

    private final KeyAttestation mKeyAttestation;
    private final OdpAuthorizationTokenDao mAuthorizationTokenDao;
    private final Clock mClock;
    private final TrainingEventLogger mTrainingEventLogger;

    private static final int BLOCKING_QUEUE_TIMEOUT_IN_SECONDS = 2;

    @VisibleForTesting
    public AuthorizationContext(
            @NonNull String ownerId,
            @NonNull String ownerCert,
            OdpAuthorizationTokenDao authorizationTokenDao,
            KeyAttestation keyAttestation,
            Clock clock,
            TrainingEventLogger trainingEventLogger) {
        mOwnerId = ownerId;
        mOwnerCert = ownerCert;
        mAuthorizationTokenDao = authorizationTokenDao;
        mKeyAttestation = keyAttestation;
        mClock = clock;
        mTrainingEventLogger = trainingEventLogger;
    }

    /** Creates a new {@link AuthorizationContext} used for authentication with remote server. */
    public static AuthorizationContext create(
            Context context,
            @NonNull String ownerId,
            @NonNull String ownerCert,
            TrainingEventLogger trainingEventLogger) {
        return new AuthorizationContext(
                ownerId,
                ownerCert,
                OdpAuthorizationTokenDao.getInstance(FederatedComputeDbHelper.getInstance(context)),
                KeyAttestation.getInstance(context),
                MonotonicClock.getInstance(),
                trainingEventLogger);
    }

    public synchronized boolean isFirstAuthTry() {
        return mTryCount == 1;
    }

    @NonNull
    public String getOwnerId() {
        return mOwnerId;
    }

    @NonNull
    public String getOwnerCert() {
        return mOwnerCert;
    }

    @Nullable
    public synchronized List<String> getAttestationRecord() {
        return mAttestationRecord;
    }

    /**
     * Updates authentication state e.g. update retry count, generate attestation record if needed.
     */
    public synchronized List<String> updateAuthState(
            AuthenticationMetadata authMetadata, TrainingEventLogger trainingEventLogger) {
        // TODO: introduce auth state if we plan to auth more than twice.
        // After first authentication failed, we will clean up expired token and generate
        // key attestation records using server provided challenge for second try.
        if (mTryCount == 1) {
            mTryCount++;
            mAuthorizationTokenDao.deleteAuthorizationToken(mOwnerId);
            mAttestationRecord =
                    mKeyAttestation.generateAttestationRecord(
                            authMetadata.getKeyAttestationMetadata().getChallenge().toByteArray(),
                            mOwnerId,
                            mTrainingEventLogger);
            return mAttestationRecord;
        }
        return null;
    }

    /**
     * Generates authentication headers used for http request.
     *
     * <p>Returns empty headers if the call to get {@link OdpAuthorizationToken} from the {@link
     * OdpAuthorizationTokenDao} fails or times out.
     */
    public Map<String, String> generateAuthHeaders() {
        Map<String, String> headers = new HashMap<>();
        synchronized (this) {
            if (mAttestationRecord != null && !mAttestationRecord.isEmpty()) {
                // Only when the device is solving challenge, the attestation record is not null.
                JSONArray attestationArr = new JSONArray(mAttestationRecord);
                headers.put(ODP_AUTHENTICATION_KEY, attestationArr.toString());
                // Generate a UUID that will serve as the authorization token.
                String authTokenUUID = UUID.randomUUID().toString();
                headers.put(ODP_AUTHORIZATION_KEY, authTokenUUID);
                OdpAuthorizationToken authToken =
                        new OdpAuthorizationToken.Builder()
                                .setAuthorizationToken(authTokenUUID)
                                .setOwnerIdentifier(mOwnerId)
                                .setCreationTime(mClock.currentTimeMillis())
                                .setExpiryTime(
                                        mClock.currentTimeMillis()
                                                + FlagsFactory.getFlags()
                                                        .getOdpAuthorizationTokenTtl())
                                .build();
                var unused =
                        Futures.submit(
                                () -> mAuthorizationTokenDao.insertAuthorizationToken(authToken),
                                FederatedComputeExecutors.getBackgroundExecutor());
                return headers;
            }
        }

        // Get existing OdpAuthorizationToken from the Dao.
        try {
            BlockingQueue<AuthTokenCallbackResult> authTokenBlockingQueue =
                    new ArrayBlockingQueue<>(1);
            ListenableFuture<AuthTokenCallbackResult> authTokenFuture =
                    Futures.submit(
                            () ->
                                    convertODPAuthToken(
                                            mAuthorizationTokenDao.getUnexpiredAuthorizationToken(
                                                    mOwnerId)),
                            FederatedComputeExecutors.getBackgroundExecutor());
            Futures.addCallback(
                    authTokenFuture,
                    createCallbackForBlockingQueue(authTokenBlockingQueue),
                    FederatedComputeExecutors.getLightweightExecutor());
            AuthTokenCallbackResult callbackResult =
                    authTokenBlockingQueue.poll(
                            BLOCKING_QUEUE_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
            if (callbackResult.isEmpty()) {
                LogUtil.e(TAG, "Timed out waiting for  blocking queue.");
            } else {
                headers.put(
                        ODP_AUTHORIZATION_KEY,
                        callbackResult.getAuthToken().getAuthorizationToken());
            }
        } catch (InterruptedException exception) {
            LogUtil.e(
                    TAG,
                    "Exception encountered when when reading auth token: "
                            + exception.getMessage());
        }
        return headers;
    }

    private static FutureCallback<AuthTokenCallbackResult> createCallbackForBlockingQueue(
            BlockingQueue<AuthTokenCallbackResult> authorizationTokenBlockingQueue) {
        return new FutureCallback<>() {
            @Override
            public void onSuccess(AuthTokenCallbackResult result) {
                authorizationTokenBlockingQueue.add(result);
            }

            @Override
            public void onFailure(Throwable t) {
                LogUtil.e(TAG, "Exception encountered when reading auth token: " + t.getMessage());
            }
        };
    }

    private static AuthTokenCallbackResult convertODPAuthToken(OdpAuthorizationToken authToken) {
        return new AuthTokenCallbackResult(authToken, authToken == null);
    }

    private static class AuthTokenCallbackResult {
        final OdpAuthorizationToken mAuthToken;

        final boolean mIsEmpty;

        AuthTokenCallbackResult(OdpAuthorizationToken authToken, boolean isEmpty) {
            mAuthToken = authToken;
            mIsEmpty = isEmpty;
        }

        OdpAuthorizationToken getAuthToken() {
            return mAuthToken;
        }

        boolean isEmpty() {
            return mIsEmpty;
        }
    }
}
