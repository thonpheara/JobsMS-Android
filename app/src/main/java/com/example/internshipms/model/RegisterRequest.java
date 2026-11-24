package com.example.internshipms.model;

import com.google.gson.annotations.SerializedName;

public class RegisterRequest {
    public String name;
    public String email;
    public String password;
    @SerializedName("password_confirmation")
    public String passwordConfirmation;
    public String role;
    @SerializedName("device_name")
    public String deviceName;
}
