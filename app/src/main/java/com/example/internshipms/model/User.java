package com.example.internshipms.model;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

public class User {
    public int id;
    public String name;
    public String email;
    /** "student" or "company". */
    public String role;
    public String status;
    public String avatar;
    @SerializedName("avatar_path")
    public String avatarPath;
    @SerializedName("avatar_url")
    public String avatarUrl;
    @SerializedName("profile_photo_url")
    public String profilePhotoUrl;

    public String bestAvatar() {
        if (avatarPath != null && !avatarPath.isEmpty()) return avatarPath;
        if (avatarUrl != null && !avatarUrl.isEmpty()) return avatarUrl;
        if (profilePhotoUrl != null && !profilePhotoUrl.isEmpty()) return profilePhotoUrl;
        return avatar;
    }
    @SerializedName("student_profile")
    public JsonObject studentProfile;
    @SerializedName("company_profile")
    public JsonObject companyProfile;

    public boolean isStudent() {
        return "student".equalsIgnoreCase(role);
    }

    public boolean isCompany() {
        return "company".equalsIgnoreCase(role);
    }
}

