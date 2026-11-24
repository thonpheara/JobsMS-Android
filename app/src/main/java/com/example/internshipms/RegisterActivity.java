package com.example.internshipms;

import android.os.Bundle;
import android.util.Patterns;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.internshipms.databinding.ActivityRegisterBinding;
import com.example.internshipms.model.ApiResponse;
import com.example.internshipms.model.LoginResponseData;
import com.example.internshipms.model.RegisterRequest;
import com.example.internshipms.model.User;
import com.example.internshipms.network.ApiError;
import com.example.internshipms.repository.AuthRepository;
import com.example.internshipms.view.ButtonLoader;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

import retrofit2.Call;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private AuthRepository repository;
    private Call<ApiResponse<LoginResponseData>> currentCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        repository = new AuthRepository(this);

        binding.btnRegister.setOnClickListener(v -> attemptRegister());
        binding.btnGoLogin.setOnClickListener(v -> finish());
    }

    private boolean isStudent() {
        return binding.toggleRole.getCheckedButtonId() == R.id.btnStudent;
    }

    private void attemptRegister() {
        clearErrors();

        String name = text(binding.etName.getText());
        String email = text(binding.etEmail.getText());
        String pass = binding.etPassword.getText() == null ? "" : binding.etPassword.getText().toString();
        String confirm = binding.etConfirm.getText() == null ? "" : binding.etConfirm.getText().toString();
        boolean student = isStudent();

        boolean ok = true;
        if (name.isEmpty()) {
            binding.tilName.setError(getString(R.string.error_required));
            ok = false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.setError(getString(R.string.error_email));
            ok = false;
        }
        if (pass.length() < 8) {
            binding.tilPassword.setError(getString(R.string.error_password_short));
            ok = false;
        }
        if (!pass.equals(confirm)) {
            binding.tilConfirm.setError(getString(R.string.error_password_match));
            ok = false;
        }
        if (!ok) {
            shakeIfError(binding.tilName);
            shakeIfError(binding.tilEmail);
            shakeIfError(binding.tilPassword);
            shakeIfError(binding.tilConfirm);
            return;
        }

        RegisterRequest req = new RegisterRequest();
        req.name = name;
        req.email = email;
        req.password = pass;
        req.passwordConfirmation = confirm;
        req.role = student ? "student" : "company";

        setLoading(true);
        currentCall = repository.register(req, new AuthRepository.Result() {
            @Override
            public void onSuccess(User user) {
                setLoading(false);
                Navigator.openDashboard(RegisterActivity.this, user.role);
            }

            @Override
            public void onError(ApiError error) {
                setLoading(false);
                boolean shown = false;
                shown |= show(binding.tilName, error, "name");
                shown |= show(binding.tilEmail, error, "email");
                shown |= show(binding.tilPassword, error, "password");
                if (!shown || error.code != 422) {
                    Snackbar.make(binding.getRoot(), error.message, Snackbar.LENGTH_LONG).show();
                }
            }
        });
    }

    private static boolean show(TextInputLayout layout, ApiError error, String field) {
        String msg = error.fieldErrors.get(field);
        if (msg != null) {
            layout.setError(msg);
            ButtonLoader.shake(layout);
        }
        return msg != null;
    }

    private static void shakeIfError(TextInputLayout layout) {
        if (layout.getError() != null) ButtonLoader.shake(layout);
    }

    private void clearErrors() {
        binding.tilName.setError(null);
        binding.tilEmail.setError(null);
        binding.tilPassword.setError(null);
        binding.tilConfirm.setError(null);
    }

    private void setLoading(boolean loading) {
        if (loading) {
            ButtonLoader.start(binding.btnRegister, "Creating account...");
        } else {
            ButtonLoader.stop(binding.btnRegister);
        }
        binding.tilName.setEnabled(!loading);
        binding.tilEmail.setEnabled(!loading);
        binding.tilPassword.setEnabled(!loading);
        binding.tilConfirm.setEnabled(!loading);
        binding.toggleRole.setEnabled(!loading);
        for (int i = 0; i < binding.toggleRole.getChildCount(); i++) {
            binding.toggleRole.getChildAt(i).setEnabled(!loading);
        }
        binding.btnGoLogin.setEnabled(!loading);
    }

    private static String text(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim();
    }

    @Override
    protected void onDestroy() {
        if (currentCall != null) currentCall.cancel();
        super.onDestroy();
    }
}
