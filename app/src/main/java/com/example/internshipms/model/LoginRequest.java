package com.example.internshipms.model;

import com.google.gson.annotations.SerializedName;

public class LoginRequest {
    public final String email;
    public final String password;
    @SerializedName("device_name")
    public final String deviceName;

    public LoginRequest(String email, String password, String deviceName) {
        this.email = email;
        this.password = password;
        this.deviceName = deviceName;
    }
}

