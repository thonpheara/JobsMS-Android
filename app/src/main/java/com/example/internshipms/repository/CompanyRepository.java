package com.example.internshipms.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.internshipms.network.ApiClient;
import com.example.internshipms.network.ApiError;
import com.example.internshipms.network.ApiService;
import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Calls the Company Portal endpoints (CompanyApiController). */
public class CompanyRepository {

    public interface JsonResult {
        void onSuccess(@NonNull JsonObject body);
        void onError(@NonNull ApiError error);
    }

    private final ApiService api;

    public CompanyRepository(Context context) {
        this.api = ApiClient.get(context);
    }

    /** GET /api/company/dashboard — stats + recent applicants + profile info. */
    public Call<JsonObject> dashboard(JsonResult result) {
        return enqueue(api.companyDashboard(), result);
    }

    /** GET /api/company/profile — company profile details. */
    public Call<JsonObject> profile(JsonResult result) {
        return enqueue(api.companyProfile(), result);
    }

    /** GET /api/auth/me — current authenticated user + company_profile + unread counts. */
    public Call<JsonObject> me(JsonResult result) {
        return enqueue(api.me(), result);
    }

    /** POST /api/company/profile — update company profile fields (JSON). */
    public Call<JsonObject> updateProfile(@NonNull JsonObject payload, JsonResult result) {
        return enqueue(api.updateCompanyProfile(payload), result);
    }

    /** POST /api/company/profile — upload new company logo image (Multipart). */
    public Call<JsonObject> uploadLogo(String companyName,
                                       byte[] imageBytes,
                                       String fileName,
                                       String mimeType,
                                       JsonResult result) {
        String safeCompany = (companyName == null || companyName.trim().isEmpty())
                ? "Company" : companyName.trim();
        String safeName = (fileName == null || fileName.isEmpty()) ? "logo.jpg" : fileName;
        String safeMime = (mimeType == null || mimeType.isEmpty()) ? "image/jpeg" : mimeType;
        okhttp3.RequestBody namePart = okhttp3.RequestBody.create(
                safeCompany, okhttp3.MediaType.parse("text/plain"));
        okhttp3.RequestBody fileBody = okhttp3.RequestBody.create(
                imageBytes, okhttp3.MediaType.parse(safeMime));
        okhttp3.MultipartBody.Part logoPart = okhttp3.MultipartBody.Part.createFormData(
                "logo", safeName, fileBody);
        return enqueue(api.updateCompanyProfileLogo(namePart, logoPart), result);
    }

    /** POST /api/company/profile — upload new company cover image (Multipart). */
    public Call<JsonObject> uploadCover(String companyName,
                                        byte[] imageBytes,
                                        String fileName,
                                        String mimeType,
                                        JsonResult result) {
        String safeCompany = (companyName == null || companyName.trim().isEmpty())
                ? "Company" : companyName.trim();
        String safeName = (fileName == null || fileName.isEmpty()) ? "cover.jpg" : fileName;
        String safeMime = (mimeType == null || mimeType.isEmpty()) ? "image/jpeg" : mimeType;
        okhttp3.RequestBody namePart = okhttp3.RequestBody.create(
                safeCompany, okhttp3.MediaType.parse("text/plain"));
        okhttp3.RequestBody fileBody = okhttp3.RequestBody.create(
                imageBytes, okhttp3.MediaType.parse(safeMime));
        okhttp3.MultipartBody.Part coverPart = okhttp3.MultipartBody.Part.createFormData(
                "cover", safeName, fileBody);
        return enqueue(api.updateCompanyProfileCover(namePart, coverPart), result);
    }

    /** GET /api/company/posts — list all internship posts created by this company. */
    public Call<JsonObject> listPosts(@Nullable String status, JsonResult result) {
        return enqueue(api.companyPosts(status, 50), result);
    }

    /** GET /api/company/posts — filtered by status, work_mode, sort. */
    public Call<JsonObject> listPostsFiltered(@Nullable String status,
                                              @Nullable String workMode,
                                              @Nullable String sort,
                                              JsonResult result) {
        return enqueue(api.companyPostsFiltered(status, workMode, sort, 50), result);
    }

    /** POST /api/company/posts/{id}/toggle-close — close or reopen a post. */
    public Call<JsonObject> toggleClosePost(int postId, JsonResult result) {
        return enqueue(api.toggleCloseCompanyPost(postId), result);
    }

    /** POST /api/company/posts — create a new internship listing. */
    public Call<JsonObject> storePost(@NonNull JsonObject payload, JsonResult result) {
        return enqueue(api.storeCompanyPost(payload), result);
    }

    /** GET /api/company/posts/{id} — single company post. */
    public Call<JsonObject> showPost(int id, JsonResult result) {
        return enqueue(api.showCompanyPost(id), result);
    }

    /** PUT /api/company/posts/{id} — update an internship post. */
    public Call<JsonObject> updatePost(int id, @NonNull JsonObject payload, JsonResult result) {
        return enqueue(api.updateCompanyPost(id, payload), result);
    }

    /** DELETE /api/company/posts/{id} — delete an internship post. */
    public Call<JsonObject> deletePost(int id, JsonResult result) {
        return enqueue(api.deleteCompanyPost(id), result);
    }

    /** POST /api/company/posts/{id}/duplicate — duplicate post as draft. */
    public Call<JsonObject> duplicatePost(int id, JsonResult result) {
        return enqueue(api.duplicateCompanyPost(id), result);
    }

    /** POST /api/company/posts/{id}/submit — submit draft post for admin approval. */
    public Call<JsonObject> submitPost(int id, JsonResult result) {
        return enqueue(api.submitCompanyPost(id), result);
    }

    /** GET /api/company/applicants — full paginated applicants list. */
    public Call<JsonObject> listApplicants(JsonResult result) {
        return enqueue(api.companyApplicants(null, null, 50), result);
    }

    /** GET /api/company/applicants — filtered by post_id and/or status. */
    public Call<JsonObject> listApplicants(@Nullable Integer postId, @Nullable String status, JsonResult result) {
        return enqueue(api.companyApplicants(postId, status, 50), result);
    }

    /** GET /api/company/applicants/{id} — single applicant details. */
    public Call<JsonObject> showApplicant(int appId, JsonResult result) {
        return enqueue(api.showCompanyApplicant(appId), result);
    }

    /** PUT /api/company/applicants/{id}/status — update applicant status. */
    public Call<JsonObject> updateApplicantStatus(int appId, String status, String companyNotes, JsonResult result) {
        JsonObject body = new JsonObject();
        body.addProperty("status", status);
        if (companyNotes != null && !companyNotes.isEmpty()) {
            body.addProperty("company_notes", companyNotes);
        }
        return enqueue(api.updateApplicantStatus(appId, body), result);
    }

    /** POST /api/company/applicants/{id}/interview — schedule interview for applicant. */
    public Call<JsonObject> scheduleInterview(int appId, JsonObject body, JsonResult result) {
        return enqueue(api.scheduleInterview(appId, body), result);
    }

    /** PATCH /api/company/applicants/bulk-status — update multiple applicant statuses. */
    public Call<JsonObject> bulkUpdateApplicantStatus(@NonNull JsonObject payload, JsonResult result) {
        return enqueue(api.bulkUpdateApplicantStatus(payload), result);
    }

    /** GET /api/company/students/{studentId} — student profile for company view. */
    public Call<JsonObject> showStudentProfile(int studentId, JsonResult result) {
        return enqueue(api.showCompanyStudentProfile(studentId), result);
    }

    /** GET /api/company/interviews — company interviews list with filters. */
    public Call<JsonObject> listInterviews(@Nullable String tab,
                                           @Nullable String meetingType,
                                           @Nullable Integer postId,
                                           @Nullable String search,
                                           JsonResult result) {
        return enqueue(api.companyInterviews(tab, meetingType, postId, search, 50), result);
    }

    /** POST /api/company/interviews — schedule/store interview. */
    public Call<JsonObject> storeInterview(@NonNull JsonObject payload, JsonResult result) {
        return enqueue(api.storeCompanyInterview(payload), result);
    }

    /** PUT /api/company/interviews/{id} — update/reschedule interview. */
    public Call<JsonObject> updateInterview(int interviewId, @NonNull JsonObject payload, JsonResult result) {
        return enqueue(api.updateCompanyInterview(interviewId, payload), result);
    }

    /** PATCH /api/company/interviews/{id}/status — update interview status. */
    public Call<JsonObject> updateInterviewStatus(int interviewId, @NonNull JsonObject payload, JsonResult result) {
        return enqueue(api.updateCompanyInterviewStatus(interviewId, payload), result);
    }

    /** GET /api/company/reports — hiring reports and analytics. */
    public Call<JsonObject> hiringReports(@Nullable String postId,
                                          @Nullable String status,
                                          @Nullable String dateFrom,
                                          @Nullable String dateTo,
                                          @Nullable String search,
                                          JsonResult result) {
        return enqueue(api.companyReports(postId, status, dateFrom, dateTo, search, 50), result);
    }

    /** GET /api/notifications/unread-count */
    public Call<JsonObject> unreadNotificationCount(JsonResult result) {
        return enqueue(api.unreadNotificationCount(), result);
    }

    /** GET /api/files/applications/{applicationId}/resume — download applicant's resume PDF. */
    public Call<okhttp3.ResponseBody> downloadApplicationResume(int applicationId) {
        return api.downloadApplicationResume(applicationId);
    }

    // ── Retrofit callback helper ─────────────────────────────────────────────

    private <T> Call<T> enqueue(Call<T> call, JsonResult result) {
        call.enqueue(new Callback<T>() {
            @Override
            public void onResponse(@NonNull Call<T> c, @NonNull Response<T> r) {
                if (r.isSuccessful() && r.body() instanceof JsonObject) {
                    result.onSuccess((JsonObject) r.body());
                } else {
                    result.onError(ApiError.from(r));
                }
            }

            @Override
            public void onFailure(@NonNull Call<T> c, @NonNull Throwable t) {
                result.onError(ApiError.network(t));
            }
        });
        return call;
    }
}
