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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.ProviderException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class KeyAttestationTest {
    private static final byte[] CHALLENGE =
            ("AHXUDhoSEFikqOefmo8xE7kGp/xjVMRDYBecBiHGxCN8rTv9W0Z4L/14d0OLB"
                            + "vC1VVzXBAnjgHoKLZzuJifTOaBJwGNIQ2ejnx3n6ayoRchDNCgpK29T+EAhBWzH")
                    .getBytes();

    private static final String CALLING_APP = "sampleApp1";

    private static final String KEY_ALIAS = KeyAttestation.getKeyAlias(CALLING_APP);

    private KeyAttestation mKeyAttestation;

    @Mock private KeyStore mMockKeyStore;

    @Mock private KeyPairGenerator mMockKeyPairGenerator;

    @Mock private Certificate mMockCert;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mKeyAttestation =
                KeyAttestation.getInstanceForTest(
                        ApplicationProvider.getApplicationContext(), new TestInjector());
    }

    @Test
    public void testGenerateAttestationRecord_nullKey() {
        doReturn(null).when(mMockKeyPairGenerator).generateKeyPair();

        List<String> record = mKeyAttestation.generateAttestationRecord(CHALLENGE, CALLING_APP);

        assertThat(record).isEmpty();
    }

    @Test
    public void testGenerateHybridKey_initFailure() throws Exception {
        doThrow(new InvalidAlgorithmParameterException("Invalid Parameters"))
                .when(mMockKeyPairGenerator)
                .initialize(any());

        KeyPair keyPair = mKeyAttestation.generateHybridKey(CHALLENGE, KEY_ALIAS);

        assertThat(keyPair).isNull();
    }

    @Test
    public void testGetAttestationRecordFromKeyAlias_noKey() {
        String keyAlias2 = CALLING_APP + "-ODPKeyAttestation2";

        KeyPair unused = mKeyAttestation.generateHybridKey(CHALLENGE, KEY_ALIAS);
        List<String> record = mKeyAttestation.getAttestationRecordFromKeyAlias(keyAlias2);

        assertThat(record).isEmpty();
    }

    @Test
    public void testGetAttestationRecordFromKeyAlias_certFailure() throws Exception {
        doThrow(new CertificateException("Cert Exception")).when(mMockKeyStore).load(any());

        List<String> record = mKeyAttestation.getAttestationRecordFromKeyAlias(KEY_ALIAS);

        assertThat(record).isEmpty();
    }

    @Test
    public void testGetAttestationRecordFromKeyAlias_keyStoreFailure() throws Exception {
        doThrow(new KeyStoreException("Key Store Exception"))
                .when(mMockKeyStore)
                .getCertificateChain(any());

        List<String> record = mKeyAttestation.getAttestationRecordFromKeyAlias(KEY_ALIAS);

        assertThat(record).isEmpty();
    }

    @Test
    public void testGetAttestationRecordFromKeyAlias_nullCertificate() throws Exception {
        when(mMockKeyStore.getCertificateChain(any())).thenReturn(null);

        List<String> record = mKeyAttestation.getAttestationRecordFromKeyAlias(KEY_ALIAS);

        assertThat(record).isEmpty();
    }

    @Test
    public void testGetAttestationRecordFromKeyAlias_Certificate() throws Exception {
        when(mMockKeyStore.getCertificateChain(any())).thenReturn(new Certificate[] {mMockCert});
        when(mMockCert.getEncoded()).thenReturn(new byte[] {20});

        List<String> record = mKeyAttestation.getAttestationRecordFromKeyAlias(KEY_ALIAS);

        assertThat(record).hasSize(1);
    }

    @Test
    public void testGetAttestationRecord_securityProviderException() throws Exception {
        doThrow(new ProviderException("Failed to generate key pair."))
                .when(mMockKeyPairGenerator)
                .generateKeyPair();

        KeyPair keyPair = mKeyAttestation.generateHybridKey(CHALLENGE, KEY_ALIAS);

        assertThat(keyPair).isNull();
    }

    private class TestInjector extends KeyAttestation.Injector {
        @Override
        KeyStore getKeyStore() {
            return mMockKeyStore;
        }

        @Override
        KeyPairGenerator getKeyPairGenerator() {
            return mMockKeyPairGenerator;
        }
    }
}
