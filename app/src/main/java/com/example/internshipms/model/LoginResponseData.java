package com.example.internshipms.model;

import com.google.gson.annotations.SerializedName;

public class LoginResponseData {
    public String token;
    @SerializedName("token_type")
    public String tokenType;
    public User user;
}

