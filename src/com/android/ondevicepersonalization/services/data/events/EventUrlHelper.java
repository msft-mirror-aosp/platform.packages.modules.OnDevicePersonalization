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

package com.android.ondevicepersonalization.services.data.events;

import android.annotation.NonNull;
import android.net.Uri;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SealedObject;
import javax.crypto.SecretKey;

/**
 * Helper class to manage creation of ODP event URLs.
 */
public class EventUrlHelper {
    public static final String URI_AUTHORITY = "localhost";
    public static final String URI_SCHEME = "odp";
    public static final String URL_LANDING_PAGE_EVENT_KEY = "r";

    private static final String BASE_URL = URI_SCHEME + "://" + URI_AUTHORITY;
    private static final String KEY_ALIAS = "odp_key_alias";
    private static final String PROVIDER = "AndroidKeyStore";
    private static final String URL_EVENT_KEY = "e";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private EventUrlHelper() {
    }

    private static SecretKey getSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(PROVIDER);
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            KeyStore.SecretKeyEntry secretKeyEntry = (KeyStore.SecretKeyEntry) keyStore.getEntry(
                    KEY_ALIAS, null);
            return secretKeyEntry.getSecretKey();
        } else {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,
                    PROVIDER);
            KeyGenParameterSpec keyGenParameterSpec = new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT).setBlockModes(
                    KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(
                    KeyProperties.ENCRYPTION_PADDING_NONE).build();
            keyGenerator.init(keyGenParameterSpec);
            return keyGenerator.generateKey();
        }
    }

    private static String encryptEvent(EventUrlPayload event) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey());

        SealedObject sealedEvent = new SealedObject(event, cipher);

        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             ObjectOutputStream objectOutputStream = new ObjectOutputStream(
                     byteArrayOutputStream)) {
            objectOutputStream.writeObject(sealedEvent);
            byte[] eventBytes = byteArrayOutputStream.toByteArray();
            return Base64.encodeToString(eventBytes, Base64.URL_SAFE | Base64.NO_WRAP);
        }
    }

    private static EventUrlPayload decryptEvent(String base64Event) throws Exception {
        byte[] cipherMessage = Base64.decode(base64Event, Base64.URL_SAFE | Base64.NO_WRAP);

        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(cipherMessage);
             ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream)) {
            SealedObject sealedEvent = (SealedObject) objectInputStream.readObject();

            return (EventUrlPayload) sealedEvent.getObject(getSecretKey());
        }
    }

    /**
     * Creates an encrypted ODP event URL for the given event
     *
     * @param event The event to create the URL for.
     * @return Encrypted ODP event URL
     */
    public static String getEncryptedOdpEventUrl(@NonNull EventUrlPayload event) throws Exception {
        String encryptedEvent = encryptEvent(event);
        return Uri.parse(BASE_URL).buildUpon().appendQueryParameter(URL_EVENT_KEY,
                encryptedEvent).build().toString();
    }

    /**
     * Creates an encrypted ODP event URL for the given event and landing page
     *
     * @param event The event to create the URL for.
     * @return Encrypted ODP event URL with a landingPage parameter
     */
    public static String getEncryptedClickTrackingUrl(@NonNull EventUrlPayload event,
            @NonNull String landingPage)
            throws Exception {
        return Uri.parse(getEncryptedOdpEventUrl(event)).buildUpon().appendQueryParameter(
                URL_LANDING_PAGE_EVENT_KEY, landingPage).build().toString();
    }

    /**
     * Retrieved the event from the encrypted ODP event URL
     *
     * @param url The encrypted ODP event URL
     * @return Event object retrieved from the URL
     */
    public static EventUrlPayload getEventFromOdpEventUrl(@NonNull String url) throws Exception {
        Uri uri = Uri.parse(url);
        String encryptedEvent = uri.getQueryParameter(URL_EVENT_KEY);
        if (encryptedEvent == null || !isOdpUrl(url)) {
            throw new IllegalArgumentException("Invalid url: " + url);
        }
        return decryptEvent(encryptedEvent);
    }

    /**
     * Returns whether a given URL is an ODP url
     *
     * @return true if URL is an ODP url, false otherwise
     */
    public static boolean isOdpUrl(@NonNull String url) {
        Uri uri = Uri.parse(url);
        return URI_SCHEME.equals(uri.getScheme())
                && URI_AUTHORITY.equals(uri.getAuthority());
    }
}
