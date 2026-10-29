package com.mihirkumar.dayflow;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Keeps the user's Gemini API key encrypted at rest with an AES key held by Android Keystore.
 * The API key is never written to logs, backups, or source code.
 */
public final class GeminiKeyStore {
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "DayFlow.GeminiApiKey.v1";
    private static final String PREFS = "dayflow_gemini_secrets";
    private static final String VALUE_CIPHER_TEXT = "api_key_cipher_text";
    private static final String VALUE_IV = "api_key_iv";

    private GeminiKeyStore() { }

    public static boolean isConfigured(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.contains(VALUE_CIPHER_TEXT) && prefs.contains(VALUE_IV);
    }

    public static void save(Context context, String apiKey) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter a Gemini API key first.");
        }
        String trimmed = apiKey.trim();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateEncryptionKey());
        byte[] encrypted = cipher.doFinal(trimmed.getBytes(StandardCharsets.UTF_8));

        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(VALUE_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .putString(VALUE_CIPHER_TEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .apply();
    }

    public static String read(Context context) throws Exception {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String cipherText = prefs.getString(VALUE_CIPHER_TEXT, null);
        String ivText = prefs.getString(VALUE_IV, null);
        if (cipherText == null || ivText == null) return null;

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateEncryptionKey(),
                new GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP)));
        byte[] decrypted = cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP));
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    public static void delete(Context context) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(VALUE_CIPHER_TEXT).remove(VALUE_IV).apply();
    }

    private static SecretKey getOrCreateEncryptionKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
        keyStore.load(null);
        java.security.Key existing = keyStore.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;

        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .build();
        generator.init(spec);
        return generator.generateKey();
    }
}
