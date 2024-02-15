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

import com.android.federatedcompute.services.common.Clock;
import com.android.federatedcompute.services.common.FlagsFactory;
import com.android.federatedcompute.services.common.MonotonicClock;
import com.android.federatedcompute.services.data.ODPAuthorizationToken;
import com.android.federatedcompute.services.data.ODPAuthorizationTokenDao;
import com.android.internal.annotations.VisibleForTesting;

import com.google.internal.federatedcompute.v1.AuthenticationMetadata;

import org.json.JSONArray;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class AuthorizationContext {
    @NonNull private final String mOwnerId;
    @NonNull private final String mOwnerCert;

    @Nullable private List<String> mAttestationRecord = null;

    private final AtomicInteger mTryCount = new AtomicInteger(1);
    private final KeyAttestation mKeyAttestation;
    private final ODPAuthorizationTokenDao mAuthorizationTokenDao;
    private final Clock mClock;

    @VisibleForTesting
    public AuthorizationContext(
            @NonNull String ownerId,
            @NonNull String ownerCert,
            ODPAuthorizationTokenDao authorizationTokenDao,
            KeyAttestation keyAttestation,
            Clock clock) {
        mOwnerId = ownerId;
        mOwnerCert = ownerCert;
        mAuthorizationTokenDao = authorizationTokenDao;
        mKeyAttestation = keyAttestation;
        mClock = clock;
    }

    /** Creates a new {@link AuthorizationContext} used for authentication with remote server. */
    public static AuthorizationContext create(
            Context context, @NonNull String ownerId, @NonNull String ownerCert) {
        return new AuthorizationContext(
                ownerId,
                ownerCert,
                ODPAuthorizationTokenDao.getInstance(context),
                KeyAttestation.getInstance(context),
                MonotonicClock.getInstance());
    }

    public boolean isFirstAuthTry() {
        return mTryCount.get() == 1;
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
    public List<String> getAttestationRecord() {
        return mAttestationRecord;
    }

    /**
     * Updates authentication state e.g. update retry count, generate attestation record if needed.
     */
    public void updateAuthState(AuthenticationMetadata authMetadata) {
        // TODO: introduce auth state if we plan to auth more than twice.
        // After first authentication failed, we will clean up expired token and generate
        // key attestation records using server provided challenge for second try.
        if (mTryCount.get() == 1) {
            mTryCount.incrementAndGet();
            mAuthorizationTokenDao.deleteAuthorizationToken(mOwnerId);
            mAttestationRecord =
                    mKeyAttestation.generateAttestationRecord(
                            authMetadata.getKeyAttestationMetadata().getChallenge().toByteArray(),
                            mOwnerId);
        }
    }

    /** Generates authentication header used for http request. */
    public Map<String, String> generateAuthHeaders() {
        Map<String, String> headers = new HashMap<>();
        if (mAttestationRecord != null) {
            // Only when the device is solving challenge, the attestation record is not null.
            JSONArray attestationArr = new JSONArray(mAttestationRecord);
            headers.put(ODP_AUTHENTICATION_KEY, attestationArr.toString());
            // generate a UUID and the UUID would serve as the authorization token.
            String authTokenUUID = UUID.randomUUID().toString();
            headers.put(ODP_AUTHORIZATION_KEY, authTokenUUID);
            mAuthorizationTokenDao.insertAuthorizationToken(
                    new ODPAuthorizationToken.Builder()
                            .setAuthorizationToken(authTokenUUID)
                            .setOwnerIdentifier(mOwnerId)
                            .setCreationTime(mClock.currentTimeMillis())
                            .setExpiryTime(
                                    mClock.currentTimeMillis()
                                            + FlagsFactory.getFlags().getOdpAuthorizationTokenTtl())
                            .build());
        } else {
            ODPAuthorizationToken authToken =
                    mAuthorizationTokenDao.getUnexpiredAuthorizationToken(mOwnerId);
            if (authToken != null) {
                headers.put(ODP_AUTHORIZATION_KEY, authToken.getAuthorizationToken());
            }
        }
        return headers;
    }
}