/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.content.Context;
import android.content.pm.PackageManager;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.security.keystore.StrongBoxUnavailableException;
import android.util.Base64;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.internal.annotations.VisibleForTesting;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.List;

public class KeyAttestation {

    private static final String TAG = "KeyAttestation";
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";

    private static final String ODP_KEY_ALIAS = "ODPKeyAttestation";

    private static volatile KeyAttestation sSingletonInstance;

    private final boolean mUseStrongBox;

    static class Injector {
        KeyStore getKeyStore() throws KeyStoreException {
            return KeyStore.getInstance(ANDROID_KEY_STORE);
        }

        KeyPairGenerator getKeyPairGenerator()
                throws NoSuchAlgorithmException, NoSuchProviderException {
            return KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEY_STORE);
        }
    }

    private final Injector mInjector;

    KeyAttestation(boolean useStrongBox, Injector injector) {
        this.mUseStrongBox = useStrongBox;
        this.mInjector = injector;
    }

    /**
     * @return a singleton instance for KeyAttestation.
     */
    public static KeyAttestation getInstance(Context context) {
        if (sSingletonInstance == null) {
            synchronized (KeyAttestation.class) {
                if (sSingletonInstance == null) {
                    boolean useStrongBox = context.getPackageManager()
                            .hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE);
                    sSingletonInstance = new KeyAttestation(useStrongBox, new Injector());
                }
            }
        }
        return sSingletonInstance;
    }

    /**
     * For test only, return an singleton instance given the context and an injector for keyStore nd
     * key pair generator.
     */
    @VisibleForTesting
    static KeyAttestation getInstanceForTest(Context context, Injector injector) {
        if (sSingletonInstance == null) {
            synchronized (KeyAttestation.class) {
                if (sSingletonInstance == null) {
                    boolean useStrongBox = context.getPackageManager()
                            .hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE);
                    return new KeyAttestation(useStrongBox, injector);
                }
            }
        }
        return sSingletonInstance;
    }

    /**
     * Given a challenge, return a list of base64 encoded strings as the attestation record. The
     * attestation is performed using a 256-bit Elliptical Curve (EC) key-pair generated by the
     * secure keymaster.
     */
    public List<String> generateAttestationRecord(
            final byte[] challenge, final String callingPackage) {
        final String keyAlias = callingPackage + "-" + ODP_KEY_ALIAS;
        KeyPair kp = generateHybridKey(challenge, keyAlias);
        if (kp == null) {
            return new ArrayList<>();
        }
        return getAttestationRecordFromKeyAlias(keyAlias);
    }

    @VisibleForTesting
    KeyPair generateHybridKey(final byte[] challenge, final String keyAlias) {
        try {
            KeyPairGenerator keyPairGenerator = mInjector.getKeyPairGenerator();
            keyPairGenerator.initialize(
                    new KeyGenParameterSpec.Builder(
                                    /* keystoreAlias= */ keyAlias,
                                    /* purposes= */ KeyProperties.PURPOSE_SIGN)
                            .setDigests(KeyProperties.DIGEST_SHA256)
                            .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                            .setAttestationChallenge(challenge)
                            // device properties are not specified when acquiring the challenge
                            .setDevicePropertiesAttestationIncluded(false)
                            .setIsStrongBoxBacked(mUseStrongBox)
                            .build());
            return keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException
                | NoSuchProviderException
                | InvalidAlgorithmParameterException e) {
            LogUtil.e(TAG, "Failed to generate EC key for attestation: " + e.getMessage());
        } catch (StrongBoxUnavailableException e) {
            LogUtil.e(
                    TAG,
                    "Strong box not available on device but isStrongBox is set to true: "
                            + e.getMessage());
        }
        return null;
    }

    @VisibleForTesting
    List<String> getAttestationRecordFromKeyAlias(String keyAlias) {
        try {
            KeyStore keyStore = mInjector.getKeyStore();
            keyStore.load(null);
            Certificate[] certificateChain = keyStore.getCertificateChain(keyAlias);
            if (certificateChain == null) {
                return new ArrayList<>();
            }
            ArrayList<String> attestationRecord = new ArrayList<>();
            for (Certificate certificate : certificateChain) {
                attestationRecord.add(
                        Base64.encodeToString(certificate.getEncoded(), Base64.NO_WRAP));
            }
            return attestationRecord;
        } catch (CertificateException e) {
            LogUtil.e(
                    TAG,
                    "CertificateException when"
                            + "generate certs for attestation: " + e.getMessage());
        } catch (IOException e) {
            LogUtil.e(
                    TAG, "IOException when "
                            + "generate certs for attestation: " + e.getMessage());
        } catch (KeyStoreException e) {
            LogUtil.e(
                    TAG,
                    "KeystoreException when "
                            + "generate certs for attestation: " + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            LogUtil.e(
                    TAG,
                    "NoSuchAlgorithmException when"
                            + "generate certs for attestation: " + e.getMessage());
        }
        return new ArrayList<>();
    }
}
