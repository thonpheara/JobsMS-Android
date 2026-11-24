package com.example.internshipms.network;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lightweight async avatar loader that normalizes Laravel storage URLs
 * (including localhost / 127.0.0.1 / relative storage paths) for the Android emulator.
 */
public final class AvatarLoader {

    private static String getHostOrigin() {
        String base = ApiClient.BASE_URL;
        int idx = base.indexOf("/api");
        if (idx > 0) {
            return base.substring(0, idx);
        }
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1);
        }
        return base;
    }
    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(32);
    private static final Map<String, String> COMPANY_LOGO_CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AvatarLoader() {
    }

    /** Extracts an avatar URL or path from a Laravel user or student/company JSON object. */
    @Nullable
    public static String extractUrl(@Nullable JsonObject user, @Nullable JsonObject profile) {
        JsonObject nestedUser = user != null ? Json.obj(user, "user") : null;
        if (nestedUser == null && profile != null) {
            nestedUser = Json.obj(profile, "user");
        }

        // Prefer uploaded disk path over generated ui-avatars URLs
        String[] pathKeys = {
                "avatar_path", "logo_path", "profile_photo_path",
                "photo_path", "company_logo", "logo", "image_path"
        };
        String path = Json.str(user, null, pathKeys);
        if (path == null) path = Json.str(profile, null, pathKeys);
        if (path == null) path = Json.str(nestedUser, null, pathKeys);
        if (path != null) {
            return normalizeUrl(path);
        }

        String[] urlKeys = {
                "avatar_url", "logo_url", "avatar", "profile_photo_url",
                "photo", "image", "profile_image", "company_logo_url"
        };
        String raw = firstRealUrl(user, urlKeys);
        if (raw == null) raw = firstRealUrl(profile, urlKeys);
        if (raw == null) raw = firstRealUrl(nestedUser, urlKeys);
        return normalizeUrl(raw);
    }

    /** Extracts a cover image URL or path from a company JSON object. */
    @Nullable
    public static String extractCoverUrl(@Nullable JsonObject company) {
        if (company == null) return null;
        String[] keys = {"cover_url", "cover_path", "cover_image", "cover"};
        String path = Json.str(company, null, keys);
        return normalizeUrl(path);
    }

    /**
     * Extracts a company's logo/avatar URL from a post or application JSON object,
     * falling back to any cached company avatar discovered from conversations.
     */
    @Nullable
    public static String extractCompanyLogo(@Nullable JsonObject post,
                                            @Nullable JsonObject fallback,
                                            @Nullable String companyName) {
        JsonObject company = Json.obj(post, "company_profile", "company");
        if (company == null && fallback != null) {
            company = Json.obj(fallback, "company_profile", "company");
        }
        JsonObject companyUser = company != null ? Json.obj(company, "user") : null;

        String resolved = extractUrl(companyUser != null ? companyUser : company, company);
        if (resolved == null) {
            resolved = extractUrl(post, fallback);
        }
        if (resolved != null) {
            rememberCompanyLogo(companyName, resolved);
            return resolved;
        }
        if (companyName != null && !companyName.trim().isEmpty()) {
            return COMPANY_LOGO_CACHE.get(companyName.trim().toLowerCase(Locale.ROOT));
        }
        return null;
    }

    /** Caches a company's avatar URL by company display name for cross-screen reuse. */
    public static void rememberCompanyLogo(@Nullable String companyName, @Nullable String rawUrl) {
        if (companyName == null || companyName.trim().isEmpty()) return;
        String normalized = normalizeUrl(rawUrl);
        if (normalized != null) {
            COMPANY_LOGO_CACHE.put(companyName.trim().toLowerCase(Locale.ROOT), normalized);
        }
    }

    /** Populates the company avatar cache from aGET /api/conversations array. */
    public static boolean syncCompanyLogosFromConversations(@Nullable JsonArray conversations) {
        if (conversations == null) return false;
        boolean added = false;
        for (JsonElement el : conversations) {
            if (!el.isJsonObject()) continue;
            JsonObject convo = el.getAsJsonObject();
            JsonObject recipient = Json.obj(convo, "recipient");
            if (recipient == null) continue;
            String name = Json.str(recipient, null, "display_name", "name");
            String url = extractUrl(recipient, null);
            if (name != null && url != null) {
                String key = name.trim().toLowerCase(Locale.ROOT);
                if (!url.equals(COMPANY_LOGO_CACHE.get(key))) {
                    COMPANY_LOGO_CACHE.put(key, url);
                    added = true;
                }
            }
        }
        return added;
    }

    @Nullable
    private static String firstRealUrl(@Nullable JsonObject obj, String... keys) {
        if (obj == null) return null;
        for (String key : keys) {
            String val = Json.str(obj, null, key);
            if (val != null && !val.toLowerCase(Locale.ROOT).contains("ui-avatars.com")) {
                return val;
            }
        }
        return null;
    }

    /** Rewrites localhost/127.0.0.1 or relative Laravel disk paths into emulator-accessible URLs. */
    @Nullable
    public static String normalizeUrl(@Nullable String raw) {
        if (raw == null) return null;
        String s = raw.trim().replace('\\', '/');
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return null;
        if (s.toLowerCase(Locale.ROOT).contains("ui-avatars.com")) return null;

        String origin = getHostOrigin();
        int storageIdx = s.indexOf("/storage/");
        if (storageIdx >= 0) {
            return (origin + s.substring(storageIdx)).replace(" ", "%20");
        }

        // Replace localhost / 127.0.0.1 with host origin
        s = s.replace("http://localhost:8000", origin)
             .replace("http://127.0.0.1:8000", origin)
             .replace("https://localhost:8000", origin)
             .replace("https://127.0.0.1:8000", origin)
             .replace("http://localhost", origin)
             .replace("http://127.0.0.1", origin)
             .replace("http://10.0.2.2:8000", origin);

        if (s.startsWith("http://") || s.startsWith("https://")) {
            return s.replace(" ", "%20");
        }
        if (s.startsWith("public/")) {
            s = s.substring("public/".length());
        } else if (s.startsWith("/public/")) {
            s = s.substring("/public/".length());
        }
        if (s.startsWith("/storage/")) {
            return (origin + s).replace(" ", "%20");
        }
        if (s.startsWith("storage/")) {
            return (origin + "/" + s).replace(" ", "%20");
        }
        if (s.startsWith("/")) {
            return (origin + "/storage" + s).replace(" ", "%20");
        }
        return (origin + "/storage/" + s).replace(" ", "%20");
    }

    /**
     * Loads the normalized avatar URL into {@code target} and hides {@code fallbackView}
     * (such as initials TextView or placeholder icon) once loaded.
     */
    public static void bind(@Nullable String rawUrl,
                            @Nullable ImageView target,
                            @Nullable View fallbackView) {
        if (target == null) return;
        String url = normalizeUrl(rawUrl);
        if (url == null) {
            target.setVisibility(View.GONE);
            if (fallbackView != null) fallbackView.setVisibility(View.VISIBLE);
            return;
        }

        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            target.setImageBitmap(cached);
            target.setVisibility(View.VISIBLE);
            if (fallbackView != null) fallbackView.setVisibility(View.GONE);
            return;
        }

        target.setTag(url);
        EXECUTOR.execute(() -> {
            Bitmap bmp = downloadBitmap(url);
            if (bmp != null) {
                CACHE.put(url, bmp);
            }
            MAIN.post(() -> {
                if (!url.equals(target.getTag())) return;
                if (bmp != null) {
                    target.setImageBitmap(bmp);
                    target.setVisibility(View.VISIBLE);
                    if (fallbackView != null) fallbackView.setVisibility(View.GONE);
                } else {
                    target.setVisibility(View.GONE);
                    if (fallbackView != null) fallbackView.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    public static void invalidateCache() {
        CACHE.evictAll();
    }

    @Nullable
    private static Bitmap downloadBitmap(String urlString) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                try (InputStream in = conn.getInputStream()) {
                    return BitmapFactory.decodeStream(in);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }
}
