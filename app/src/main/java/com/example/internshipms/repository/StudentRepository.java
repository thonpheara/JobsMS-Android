package com.example.internshipms.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.internshipms.network.ApiClient;
import com.example.internshipms.network.ApiError;
import com.example.internshipms.network.ApiService;
import com.google.gson.JsonObject;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Calls the Student Portal endpoints (StudentApiController). */
public class StudentRepository {

    public interface JsonResult {
        void onSuccess(@NonNull JsonObject body);

        void onError(@NonNull ApiError error);
    }

    private final ApiService api;

    public StudentRepository(Context context) {
        this.api = ApiClient.get(context);
    }

    public Call<JsonObject> dashboard(JsonResult result) {
        return enqueue(api.studentDashboard(), result);
    }

    public Call<JsonObject> profile(JsonResult result) {
        return enqueue(api.studentProfile(), result);
    }

    public Call<JsonObject> updateAvatar(byte[] imageBytes, String fileName, String mimeType, JsonResult result) {
        String safeName = (fileName == null || fileName.isEmpty()) ? "avatar.jpg" : fileName;
        String safeMime = (mimeType == null || mimeType.isEmpty()) ? "image/jpeg" : mimeType;
        RequestBody body = RequestBody.create(imageBytes, MediaType.parse(safeMime));
        MultipartBody.Part part = MultipartBody.Part.createFormData("avatar", safeName, body);
        return enqueue(api.updateAvatar(part), result);
    }

    public Call<JsonObject> updateProfile(JsonObject payload, JsonResult result) {
        return enqueue(api.updateStudentProfile(payload), result);
    }

    public Call<JsonObject> uploadResume(byte[] fileBytes, String fileName, String mimeType, JsonResult result) {
        String safeName = (fileName == null || fileName.isEmpty()) ? "resume.pdf" : fileName;
        String safeMime = (mimeType == null || mimeType.isEmpty()) ? "application/pdf" : mimeType;
        RequestBody body = RequestBody.create(fileBytes, MediaType.parse(safeMime));
        MultipartBody.Part part = MultipartBody.Part.createFormData("resume", safeName, body);
        return enqueue(api.uploadResume(part), result);
    }

    public Call<JsonObject> deleteResume(JsonResult result) {
        return enqueue(api.deleteResume(), result);
    }

    public Call<ResponseBody> downloadProfileResume() {
        return api.downloadProfileResume();
    }

    public Call<ResponseBody> downloadApplicationResume(int applicationId) {
        return api.downloadApplicationResume(applicationId);
    }

    public Call<JsonObject> posts(JsonResult result) {
        return enqueue(api.studentPosts(null, null, null, null, null, null, null, null, 50), result);
    }

    public Call<JsonObject> posts(@Nullable String search,
                                  @Nullable String category,
                                  @Nullable String type,
                                  @Nullable String location,
                                  JsonResult result) {
        return enqueue(api.studentPosts(
                emptyToNull(search),
                emptyToNull(category),
                emptyToNull(type),
                emptyToNull(location),
                null,
                null,
                null,
                null,
                50), result);
    }

    public Call<JsonObject> postsAdvanced(@Nullable String search,
                                          @Nullable String category,
                                          @Nullable String deadline,
                                          @Nullable String stipend,
                                          @Nullable Integer stipendMin,
                                          @Nullable String sort,
                                          JsonResult result) {
        return enqueue(api.studentPosts(
                emptyToNull(search),
                emptyToNull(category),
                null,
                null,
                emptyToNull(deadline),
                emptyToNull(stipend),
                stipendMin,
                emptyToNull(sort),
                50), result);
    }

    public Call<JsonObject> showPost(int id, JsonResult result) {
        return enqueue(api.showPost(id), result);
    }

    public Call<JsonObject> toggleBookmark(int postId, JsonResult result) {
        return enqueue(api.toggleBookmark(postId), result);
    }

    public Call<JsonObject> bookmarks(JsonResult result) {
        return enqueue(api.studentBookmarks(50), result);
    }

    public Call<JsonObject> applyPost(int postId, String coverLetter,
                                      @Nullable byte[] pdfBytes,
                                      @Nullable String fileName,
                                      JsonResult result) {
        if (pdfBytes != null && pdfBytes.length > 0) {
            String safeName = (fileName == null || fileName.isEmpty()) ? "resume.pdf" : fileName;
            RequestBody letterBody = RequestBody.create(coverLetter, MediaType.parse("text/plain"));
            RequestBody fileBody = RequestBody.create(pdfBytes, MediaType.parse("application/pdf"));
            MultipartBody.Part part = MultipartBody.Part.createFormData("resume", safeName, fileBody);
            return enqueue(api.applyPostMultipart(postId, letterBody, part), result);
        }
        JsonObject body = new JsonObject();
        body.addProperty("cover_letter", coverLetter);
        return enqueue(api.applyPost(postId, body), result);
    }

    public Call<JsonObject> applications(JsonResult result) {
        return enqueue(api.studentApplications(null, 50), result);
    }

    public Call<JsonObject> applications(@Nullable String status, JsonResult result) {
        return enqueue(api.studentApplications(emptyToNull(status), 50), result);
    }

    public Call<JsonObject> showApplication(int id, JsonResult result) {
        return enqueue(api.showApplication(id), result);
    }

    public Call<JsonObject> withdrawApplication(int id, JsonResult result) {
        return enqueue(api.withdrawApplication(id), result);
    }

    public Call<JsonObject> showCompany(int companyId, JsonResult result) {
        return enqueue(api.showCompany(companyId), result);
    }

    public Call<JsonObject> confirmInterview(int interviewId, JsonResult result) {
        return enqueue(api.confirmInterview(interviewId), result);
    }

    public Call<JsonObject> rescheduleInterview(int interviewId, String reason, JsonResult result) {
        JsonObject body = new JsonObject();
        body.addProperty("reschedule_reason", reason);
        return enqueue(api.rescheduleInterview(interviewId, body), result);
    }

    public Call<JsonObject> unreadNotificationCount(JsonResult result) {
        return enqueue(api.unreadNotificationCount(), result);
    }

    public Call<JsonObject> notifications(JsonResult result) {
        return enqueue(api.notifications(30), result);
    }

    public Call<JsonObject> markNotificationRead(String id, JsonResult result) {
        return enqueue(api.markNotificationRead(id), result);
    }

    public Call<JsonObject> markAllNotificationsRead(JsonResult result) {
        return enqueue(api.markAllNotificationsRead(), result);
    }

    public Call<JsonObject> deleteNotification(String id, JsonResult result) {
        return enqueue(api.deleteNotification(id), result);
    }

    public Call<JsonObject> clearAllNotifications(JsonResult result) {
        return enqueue(api.clearAllNotifications(), result);
    }

    // ---------- Auth Session & Password ----------

    public Call<JsonObject> logout(JsonResult result) {
        return enqueue(api.logout(), result);
    }

    public Call<JsonObject> updatePassword(String currentPassword,
                                           String newPassword,
                                           String passwordConfirmation,
                                           JsonResult result) {
        JsonObject body = new JsonObject();
        body.addProperty("current_password", currentPassword);
        body.addProperty("password", newPassword);
        body.addProperty("password_confirmation", passwordConfirmation);
        return enqueue(api.updatePassword(body), result);
    }

    // ---------- Conversations & Chat ----------

    public Call<JsonObject> conversations(JsonResult result) {
        return enqueue(api.conversations(), result);
    }

    public Call<JsonObject> showConversation(int id, JsonResult result) {
        return enqueue(api.showConversation(id), result);
    }

    public Call<JsonObject> sendMessage(int conversationId, String text, JsonResult result) {
        JsonObject body = new JsonObject();
        body.addProperty("body", text);
        return enqueue(api.sendMessage(conversationId, body), result);
    }

    public Call<JsonObject> startConversation(int applicationId, JsonResult result) {
        return enqueue(api.startConversation(applicationId), result);
    }

    @Nullable
    private static String emptyToNull(@Nullable String s) {
        return (s == null || s.trim().isEmpty()) ? null : s.trim();
    }

    private static Call<JsonObject> enqueue(Call<JsonObject> call, JsonResult result) {
        call.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> c, @NonNull Response<JsonObject> r) {
                if (r.isSuccessful() && r.body() != null) {
                    result.onSuccess(r.body());
                } else {
                    result.onError(ApiError.from(r));
                }
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> c, @NonNull Throwable t) {
                if (!c.isCanceled()) result.onError(ApiError.network(t));
            }
        });
        return call;
    }
}
