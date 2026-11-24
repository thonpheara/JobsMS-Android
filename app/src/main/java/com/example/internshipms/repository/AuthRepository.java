package com.example.internshipms.repository;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.internshipms.model.ApiResponse;
import com.example.internshipms.model.LoginRequest;
import com.example.internshipms.model.LoginResponseData;
import com.example.internshipms.model.RegisterRequest;
import com.example.internshipms.model.User;
import com.example.internshipms.network.ApiClient;
import com.example.internshipms.network.ApiError;
import com.example.internshipms.network.ApiService;
import com.example.internshipms.session.SessionManager;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthRepository {
    public static final String DEVICE_NAME = "Android Device";

    public interface Result {
        void onSuccess(User user);

        void onError(ApiError error);
    }

    private final ApiService api;
    private final SessionManager session;

    public AuthRepository(Context context) {
        this.api = ApiClient.get(context);
        this.session = SessionManager.get(context);
    }

    public Call<ApiResponse<LoginResponseData>> login(String email, String password, Result result) {
        Call<ApiResponse<LoginResponseData>> call =
                api.login(new LoginRequest(email, password, DEVICE_NAME));
        enqueue(call, result);
        return call;
    }

    public Call<ApiResponse<LoginResponseData>> register(RegisterRequest request, Result result) {
        request.deviceName = DEVICE_NAME;
        Call<ApiResponse<LoginResponseData>> call = api.register(request);
        enqueue(call, result);
        return call;
    }

    private void enqueue(Call<ApiResponse<LoginResponseData>> call, Result result) {
        call.enqueue(new Callback<ApiResponse<LoginResponseData>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<LoginResponseData>> c,
                                   @NonNull Response<ApiResponse<LoginResponseData>> r) {
                ApiResponse<LoginResponseData> body = r.body();
                if (r.isSuccessful() && body != null && body.data != null
                        && body.data.token != null && body.data.user != null) {
                    session.saveSession(body.data.token, body.data.user);
                    result.onSuccess(body.data.user);
                } else if (r.isSuccessful()) {
                    result.onError(new ApiError(r.code(), "Unexpected server response.",
                            new java.util.HashMap<>()));
                } else {
                    result.onError(ApiError.from(r));
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<LoginResponseData>> c,
                                  @NonNull Throwable t) {
                if (!c.isCanceled()) {
                    result.onError(ApiError.network(t));
                }
            }
        });
    }
}

