package com.example.internshipms.session;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.example.internshipms.model.User;

import java.io.IOException;
import java.security.GeneralSecurityException;

/** Stores the Sanctum token and basic user info in EncryptedSharedPreferences. */
public class SessionManager {
    private static final String FILE = "secure_session";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_ID = "user_id";
    private static final String KEY_NAME = "user_name";
    private static final String KEY_EMAIL = "user_email";
    private static final String KEY_ROLE = "user_role";
    private static final String KEY_AVATAR = "user_avatar";

    private static SessionManager instance;
    private final SharedPreferences prefs;

    public static synchronized SessionManager get(Context context) {
        if (instance == null) {
            instance = new SessionManager(context.getApplicationContext());
        }
        return instance;
    }

    private SessionManager(Context context) {
        try {
            MasterKey key = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            prefs = EncryptedSharedPreferences.create(
                    context, FILE, key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Unable to open secure storage", e);
        }
    }

    public void saveSession(String token, User user) {
        prefs.edit()
                .putString(KEY_TOKEN, token)
                .putInt(KEY_ID, user.id)
                .putString(KEY_NAME, user.name)
                .putString(KEY_EMAIL, user.email)
                .putString(KEY_ROLE, user.role)
                .putString(KEY_AVATAR, user.bestAvatar())
                .apply();
    }

    public void setName(String name) {
        if (name != null) prefs.edit().putString(KEY_NAME, name).apply();
    }

    public void setAvatarUrl(String url) {
        prefs.edit().putString(KEY_AVATAR, url).apply();
    }

    public String getAvatarUrl() {
        return prefs.getString(KEY_AVATAR, null);
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public int getUserId() {
        return prefs.getInt(KEY_ID, -1);
    }

    public String getName() {
        return prefs.getString(KEY_NAME, "");
    }

    public String getEmail() {
        return prefs.getString(KEY_EMAIL, "");
    }

    /** "student" or "company", or null when logged out. */
    public String getRole() {
        return prefs.getString(KEY_ROLE, null);
    }

    public boolean isLoggedIn() {
        String token = getToken();
        return token != null && !token.isEmpty();
    }

    public void logout() {
        prefs.edit().clear().apply();
    }
}

