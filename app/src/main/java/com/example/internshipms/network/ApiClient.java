package com.example.internshipms.network;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import com.example.internshipms.session.SessionManager;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ApiClient {
    /** Emulator. For a physical device use "http://<YOUR_LOCAL_IP>:8000/api/". */
    public static final String BASE_URL = "http://10.0.2.2:8000/api/";


    private static ApiService service;

    private ApiClient() {
    }

    public static synchronized ApiService get(Context context) {
        if (service == null) {
            Context app = context.getApplicationContext();
            boolean debuggable = (app.getApplicationInfo().flags
                    & ApplicationInfo.FLAG_DEBUGGABLE) != 0;

            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(debuggable
                    ? HttpLoggingInterceptor.Level.BODY
                    : HttpLoggingInterceptor.Level.NONE);
            logging.redactHeader("Authorization");

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(new AuthInterceptor(SessionManager.get(app)))
                    .addInterceptor(logging)
                    .build();

            service = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(ApiService.class);
        }
        return service;
    }
}

