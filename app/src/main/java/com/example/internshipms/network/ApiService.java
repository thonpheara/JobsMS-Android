package com.example.internshipms.network;

import com.example.internshipms.model.ApiResponse;
import com.example.internshipms.model.LoginRequest;
import com.example.internshipms.model.LoginResponseData;
import com.example.internshipms.model.RegisterRequest;
import com.google.gson.JsonObject;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.PATCH;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Part;

public interface ApiService {
    @POST("auth/login")
    Call<ApiResponse<LoginResponseData>> login(@Body LoginRequest request);

    @POST("auth/register")
    Call<ApiResponse<LoginResponseData>> register(@Body RegisterRequest request);

    /** GET /api/auth/me — current authenticated user + unread counts. */
    @GET("auth/me")
    Call<JsonObject> me();

    /** POST /api/auth/logout — revoke current Sanctum token. */
    @POST("auth/logout")
    Call<JsonObject> logout();

    /** PUT /api/auth/password — update password. */
    @PUT("auth/password")
    Call<JsonObject> updatePassword(@Body JsonObject body);

    /** POST /api/auth/avatar — upload profile avatar image. */
    @Multipart
    @POST("auth/avatar")
    Call<JsonObject> updateAvatar(@Part MultipartBody.Part avatar);

    /** DELETE /api/auth/avatar — remove profile avatar image. */
    @DELETE("auth/avatar")
    Call<JsonObject> removeAvatar();

    // ---------- Student Portal (StudentApiController) ----------

    /** GET /api/student/dashboard — student, stats, recent_applications, featured_posts. */
    @GET("student/dashboard")
    Call<JsonObject> studentDashboard();

    /** GET /api/student/profile — user + student profile. */
    @GET("student/profile")
    Call<JsonObject> studentProfile();

    /** POST /api/student/profile — update student profile fields. */
    @POST("student/profile")
    Call<JsonObject> updateStudentProfile(@Body JsonObject body);

    /** POST /api/student/resume — upload PDF/DOC/DOCX resume. */
    @Multipart
    @POST("student/resume")
    Call<JsonObject> uploadResume(@Part MultipartBody.Part resume);

    /** DELETE /api/student/resume — delete resume. */
    @DELETE("student/resume")
    Call<JsonObject> deleteResume();

    /** GET /api/student/posts — browse open internship listings with optional filters & sort. */
    @GET("student/posts")
    Call<JsonObject> studentPosts(
            @Query("search") String search,
            @Query("category") String category,
            @Query("type") String type,
            @Query("location") String location,
            @Query("deadline") String deadline,
            @Query("stipend") String stipend,
            @Query("stipend_min") Integer stipendMin,
            @Query("sort") String sort,
            @Query("per_page") Integer perPage);

    /** GET /api/student/posts/{id} — post details + has_applied + is_bookmarked + existing_application. */
    @GET("student/posts/{id}")
    Call<JsonObject> showPost(@Path("id") int id);

    /** POST /api/student/posts/{id}/bookmark — toggle bookmark on a post. */
    @POST("student/posts/{id}/bookmark")
    Call<JsonObject> toggleBookmark(@Path("id") int id);

    /** GET /api/student/bookmarks — student's saved/bookmarked posts. */
    @GET("student/bookmarks")
    Call<JsonObject> studentBookmarks(@Query("per_page") Integer perPage);

    /** POST /api/student/posts/{id}/apply — submit application (JSON when using profile resume). */
    @POST("student/posts/{id}/apply")
    Call<JsonObject> applyPost(@Path("id") int id, @Body JsonObject body);

    /** POST /api/student/posts/{id}/apply — submit application with custom resume file. */
    @Multipart
    @POST("student/posts/{id}/apply")
    Call<JsonObject> applyPostMultipart(
            @Path("id") int id,
            @Part("cover_letter") RequestBody coverLetter,
            @Part MultipartBody.Part resume);

    /** GET /api/student/applications — student's submitted applications. */
    @GET("student/applications")
    Call<JsonObject> studentApplications(
            @Query("status") String status,
            @Query("per_page") Integer perPage);

    /** GET /api/student/applications/{id} — single application details. */
    @GET("student/applications/{id}")
    Call<JsonObject> showApplication(@Path("id") int id);

    /** POST /api/student/applications/{id}/withdraw — withdraw pending application. */
    @POST("student/applications/{id}/withdraw")
    Call<JsonObject> withdrawApplication(@Path("id") int id);

    /** GET /api/student/companies/{id} — company public profile + active openings. */
    @GET("student/companies/{id}")
    Call<JsonObject> showCompany(@Path("id") int id);

    /** POST /api/student/interviews/{id}/confirm — confirm interview attendance. */
    @POST("student/interviews/{id}/confirm")
    Call<JsonObject> confirmInterview(@Path("id") int id);

    /** POST /api/student/interviews/{id}/reschedule — request interview reschedule. */
    @POST("student/interviews/{id}/reschedule")
    Call<JsonObject> rescheduleInterview(@Path("id") int id, @Body JsonObject body);

    // ---------- Notifications (NotificationApiController) ----------

    /** GET /api/notifications — unread_count + paginated notifications. */
    @GET("notifications")
    Call<JsonObject> notifications(@Query("per_page") Integer perPage);

    /** GET /api/notifications/unread-count */
    @GET("notifications/unread-count")
    Call<JsonObject> unreadNotificationCount();

    /** POST /api/notifications/{id}/read */
    @POST("notifications/{id}/read")
    Call<JsonObject> markNotificationRead(@Path("id") String id);

    /** POST /api/notifications/read-all */
    @POST("notifications/read-all")
    Call<JsonObject> markAllNotificationsRead();

    /** DELETE /api/notifications/{id} */
    @DELETE("notifications/{id}")
    Call<JsonObject> deleteNotification(@Path("id") String id);

    /** DELETE /api/notifications */
    @DELETE("notifications")
    Call<JsonObject> clearAllNotifications();

    // ---------- Company Portal (CompanyApiController) ----------

    /** GET /api/company/dashboard — company profile, stats, analytics, recent_applicants. */
    @GET("company/dashboard")
    Call<JsonObject> companyDashboard();

    /** GET /api/company/profile — company profile details. */
    @GET("company/profile")
    Call<JsonObject> companyProfile();

    /** POST /api/company/profile — update company profile fields (JSON). */
    @POST("company/profile")
    Call<JsonObject> updateCompanyProfile(@Body JsonObject body);

    /** POST /api/company/profile — update company profile + logo image (Multipart). */
    @Multipart
    @POST("company/profile")
    Call<JsonObject> updateCompanyProfileLogo(
            @Part("company_name") RequestBody companyName,
            @Part MultipartBody.Part logo);

    /** POST /api/company/profile — update company profile + cover image (Multipart). */
    @Multipart
    @POST("company/profile")
    Call<JsonObject> updateCompanyProfileCover(
            @Part("company_name") RequestBody companyName,
            @Part MultipartBody.Part cover);

    /** GET /api/company/posts — paginated list of company internship listings. */
    @GET("company/posts")
    Call<JsonObject> companyPosts(
            @Query("status") String status,
            @Query("per_page") Integer perPage);

    /** GET /api/company/posts — filtered by status, work_mode, sort. */
    @GET("company/posts")
    Call<JsonObject> companyPostsFiltered(
            @Query("status") String status,
            @Query("work_mode") String workMode,
            @Query("sort") String sort,
            @Query("per_page") Integer perPage);

    /** POST /api/company/posts/{id}/toggle-close — close or reopen a post. */
    @POST("company/posts/{id}/toggle-close")
    Call<JsonObject> toggleCloseCompanyPost(@Path("id") int id);

    /** POST /api/company/posts — create a new internship listing (draft or pending_approval). */
    @POST("company/posts")
    Call<JsonObject> storeCompanyPost(@Body JsonObject body);

    /** GET /api/company/posts/{id} — single company post with applications_count. */
    @GET("company/posts/{id}")
    Call<JsonObject> showCompanyPost(@Path("id") int id);

    /** PUT /api/company/posts/{id} — update an internship post. */
    @PUT("company/posts/{id}")
    Call<JsonObject> updateCompanyPost(@Path("id") int id, @Body JsonObject body);

    /** DELETE /api/company/posts/{id} — delete an internship post. */
    @DELETE("company/posts/{id}")
    Call<JsonObject> deleteCompanyPost(@Path("id") int id);

    /** POST /api/company/posts/{id}/duplicate — clone an existing post as a draft. */
    @POST("company/posts/{id}/duplicate")
    Call<JsonObject> duplicateCompanyPost(@Path("id") int id);

    /** POST /api/company/posts/{id}/submit — submit a draft post for admin approval. */
    @POST("company/posts/{id}/submit")
    Call<JsonObject> submitCompanyPost(@Path("id") int id);

    /** GET /api/company/applicants — paginated list of all applicants. */
    @GET("company/applicants")
    Call<JsonObject> companyApplicants(
            @Query("post_id") Integer postId,
            @Query("status") String status,
            @Query("per_page") Integer perPage);

    /** GET /api/company/applicants/{id} — single applicant details. */
    @GET("company/applicants/{id}")
    Call<JsonObject> showCompanyApplicant(@Path("id") int id);

    /** PUT /api/company/applicants/{id}/status — update applicant status. */
    @PUT("company/applicants/{id}/status")
    Call<JsonObject> updateApplicantStatus(@Path("id") int id, @Body JsonObject body);

    /** POST /api/company/applicants/{id}/interview — schedule interview. */
    @POST("company/applicants/{id}/interview")
    Call<JsonObject> scheduleInterview(@Path("id") int id, @Body JsonObject body);

    /** PATCH /api/company/applicants/bulk-status — update status for multiple applicants. */
    @PATCH("company/applicants/bulk-status")
    Call<JsonObject> bulkUpdateApplicantStatus(@Body JsonObject body);

    /** GET /api/company/students/{studentId} — student profile for company view. */
    @GET("company/students/{studentId}")
    Call<JsonObject> showCompanyStudentProfile(@Path("studentId") int studentId);

    /** GET /api/company/interviews — company interview list with filters. */
    @GET("company/interviews")
    Call<JsonObject> companyInterviews(
            @Query("tab") String tab,
            @Query("meeting_type") String meetingType,
            @Query("post_id") Integer postId,
            @Query("search") String search,
            @Query("per_page") Integer perPage);

    /** POST /api/company/interviews — create interview. */
    @POST("company/interviews")
    Call<JsonObject> storeCompanyInterview(@Body JsonObject body);

    /** PUT /api/company/interviews/{id} — update/reschedule interview. */
    @PUT("company/interviews/{id}")
    Call<JsonObject> updateCompanyInterview(@Path("id") int id, @Body JsonObject body);

    /** PATCH /api/company/interviews/{id}/status — update interview status (completed, cancelled, etc). */
    @PATCH("company/interviews/{id}/status")
    Call<JsonObject> updateCompanyInterviewStatus(@Path("id") int id, @Body JsonObject body);

    /** GET /api/company/reports — hiring reports and stats. */
    @GET("company/reports")
    Call<JsonObject> companyReports(
            @Query("post_id") String postId,
            @Query("status") String status,
            @Query("date_from") String dateFrom,
            @Query("date_to") String dateTo,
            @Query("search") String search,
            @Query("per_page") Integer perPage);

    // ---------- Conversations & Chat (ConversationApiController) ----------

    /** GET /api/conversations — list user's conversations. */
    @GET("conversations")
    Call<JsonObject> conversations();

    /** GET /api/conversations/{id} — conversation details + messages. */
    @GET("conversations/{id}")
    Call<JsonObject> showConversation(@Path("id") int id);

    /** POST /api/conversations/{id}/messages — send a message in conversation. */
    @POST("conversations/{id}/messages")
    Call<JsonObject> sendMessage(@Path("id") int id, @Body JsonObject body);

    /** POST /api/conversations/start/{applicationId} — find or create conversation for application. */
    @POST("conversations/start/{applicationId}")
    Call<JsonObject> startConversation(@Path("applicationId") int applicationId);

    // ---------- File / Resume Access (FileApiController) ----------

    /** GET /api/files/students/resume — download current student's profile resume. */
    @GET("files/students/resume")
    Call<ResponseBody> downloadProfileResume();

    /** GET /api/files/applications/{applicationId}/resume — download application resume. */
    @GET("files/applications/{applicationId}/resume")
    Call<ResponseBody> downloadApplicationResume(@Path("applicationId") int applicationId);
}
