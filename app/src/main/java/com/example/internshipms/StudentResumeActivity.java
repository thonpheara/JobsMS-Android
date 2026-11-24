package com.example.internshipms;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.internshipms.network.ApiError;
import com.example.internshipms.network.Json;
import com.example.internshipms.repository.StudentRepository;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class StudentResumeActivity extends AppCompatActivity {

    private StudentRepository repository;
    private Call<JsonObject> profileCall;
    private Call<JsonObject> actionCall;
    private Call<ResponseBody> downloadCall;

    private View cardCurrentResume;
    private TextView tvResumeName;
    private TextView tvResumeMeta;

    private final ActivityResultLauncher<String[]> documentPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onDocumentPicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_student_resume);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        repository = new StudentRepository(this);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        cardCurrentResume = findViewById(R.id.cardCurrentResume);
        tvResumeName = findViewById(R.id.tvResumeName);
        tvResumeMeta = findViewById(R.id.tvResumeMeta);
        cardCurrentResume.setVisibility(View.GONE);

        View.OnClickListener launchPicker = v -> documentPicker.launch(new String[]{
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        });

        findViewById(R.id.btnUpdateResume).setOnClickListener(launchPicker);
        findViewById(R.id.cardUploadDropzone).setOnClickListener(launchPicker);
        findViewById(R.id.btnBrowseFiles).setOnClickListener(launchPicker);

        findViewById(R.id.btnDownloadResume).setOnClickListener(v -> downloadCurrentResume());
        findViewById(R.id.btnDeleteResume).setOnClickListener(v -> confirmDelete());

        loadResumeFromProfile();
    }

    private void loadResumeFromProfile() {
        if (profileCall != null) profileCall.cancel();
        profileCall = repository.profile(new StudentRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                JsonObject d = Json.data(body);
                JsonObject student = Json.obj(d, "student");
                if (student == null) student = d;

                String resumePath = Json.str(student, null, "resume_path");
                String updatedAt = Json.str(student, null, "updated_at");
                if (resumePath != null && !resumePath.isEmpty()) {
                    cardCurrentResume.setVisibility(View.VISIBLE);
                    tvResumeName.setText(extractFileName(resumePath));
                    tvResumeMeta.setText("PDF • Updated " + StudentDashboardActivity.formatDeadlineDate(updatedAt));
                } else {
                    cardCurrentResume.setVisibility(View.GONE);
                }
            }

            @Override
            public void onError(@NonNull ApiError error) {
            }
        });
    }

    private void onDocumentPicked(@Nullable Uri uri) {
        if (uri == null) return;
        String displayName = "Student_Resume.pdf";
        long sizeBytes = -1;
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    String n = cursor.getString(nameIndex);
                    if (n != null && !n.isEmpty()) displayName = n;
                }
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    sizeBytes = cursor.getLong(sizeIndex);
                }
            }
        } catch (Exception ignored) {
        }

        byte[] fileBytes;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return;
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = in.read(chunk)) != -1) buf.write(chunk, 0, n);
            fileBytes = buf.toByteArray();
        } catch (Exception e) {
            Toast.makeText(this, "Unable to read selected file.", Toast.LENGTH_SHORT).show();
            return;
        }

        final String finalName = displayName;
        final long finalSize = sizeBytes;
        String mime = getContentResolver().getType(uri);

        Toast.makeText(this, "Uploading " + finalName + "...", Toast.LENGTH_SHORT).show();
        if (actionCall != null) actionCall.cancel();
        actionCall = repository.uploadResume(fileBytes, finalName, mime, new StudentRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                cardCurrentResume.setVisibility(View.VISIBLE);
                tvResumeName.setText(finalName);
                String dateStr = new SimpleDateFormat("MMM dd, yyyy", Locale.US).format(new Date());
                String sizeStr = finalSize > 0
                        ? String.format(Locale.US, "%.1f MB", finalSize / (1024f * 1024f))
                        : "PDF";
                tvResumeMeta.setText(sizeStr + " • Uploaded " + dateStr);
                Toast.makeText(StudentResumeActivity.this,
                        "Resume uploaded successfully!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(@NonNull ApiError error) {
                Toast.makeText(StudentResumeActivity.this, error.message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void downloadCurrentResume() {
        String fileName = tvResumeName.getText() != null
                ? tvResumeName.getText().toString() : "Student_Resume.pdf";
        Toast.makeText(this, "Downloading " + fileName + "...", Toast.LENGTH_SHORT).show();

        if (downloadCall != null) downloadCall.cancel();
        downloadCall = repository.downloadProfileResume();
        downloadCall.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(StudentResumeActivity.this,
                            "Unable to download resume.", Toast.LENGTH_SHORT).show();
                    return;
                }
                boolean saved = saveToDownloads(response.body(), fileName);
                Toast.makeText(StudentResumeActivity.this,
                        saved ? "Saved to Downloads: " + fileName : "Download failed.",
                        Toast.LENGTH_LONG).show();
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (!call.isCanceled()) {
                    Toast.makeText(StudentResumeActivity.this,
                            "Network error while downloading.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private boolean saveToDownloads(ResponseBody body, String fileName) {
        try (InputStream in = body.byteStream()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) return false;
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) return false;
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                    return true;
                }
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File outFile = new File(dir, fileName);
                try (OutputStream out = new FileOutputStream(outFile)) {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                    return true;
                }
            }
        } catch (Exception e) {
            return false;
        }
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Resume")
                .setMessage("Remove " + tvResumeName.getText() + " from your active employer profile?")
                .setPositiveButton("Delete", (d, w) -> {
                    if (actionCall != null) actionCall.cancel();
                    actionCall = repository.deleteResume(new StudentRepository.JsonResult() {
                        @Override
                        public void onSuccess(@NonNull JsonObject body) {
                            cardCurrentResume.setVisibility(View.GONE);
                            Toast.makeText(StudentResumeActivity.this,
                                    "Resume removed successfully.", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(@NonNull ApiError error) {
                            Toast.makeText(StudentResumeActivity.this,
                                    error.message, Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static String extractFileName(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String base = slash >= 0 ? path.substring(slash + 1) : path;
        // Strip leading timestamp prefix like "1730634000_" if present
        return base.replaceFirst("^\\d{9,}_", "");
    }

    @Override
    protected void onDestroy() {
        if (profileCall != null) profileCall.cancel();
        if (actionCall != null) actionCall.cancel();
        if (downloadCall != null) downloadCall.cancel();
        super.onDestroy();
    }
}
