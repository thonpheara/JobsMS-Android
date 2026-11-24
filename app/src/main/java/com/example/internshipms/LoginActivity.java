package com.example.internshipms;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.internshipms.databinding.ActivityLoginBinding;
import com.example.internshipms.model.ApiResponse;
import com.example.internshipms.model.LoginResponseData;
import com.example.internshipms.model.User;
import com.example.internshipms.network.ApiError;
import com.example.internshipms.repository.AuthRepository;
import com.example.internshipms.session.SessionManager;
import com.example.internshipms.view.ButtonLoader;
import com.google.android.material.snackbar.Snackbar;

import retrofit2.Call;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private AuthRepository repository;
    private Call<ApiResponse<LoginResponseData>> currentCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SessionManager session = SessionManager.get(this);
        if (session.isLoggedIn()) {
            Navigator.openDashboard(this, session.getRole());
            return;
        }

        EdgeToEdge.enable(this);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        repository = new AuthRepository(this);
        binding.btnLogin.setOnClickListener(v -> attemptLogin());
        binding.btnGoRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void attemptLogin() {
        binding.tilEmail.setError(null);
        binding.tilPassword.setError(null);

        String email = text(binding.etEmail.getText());
        String password = text(binding.etPassword.getText());

        boolean ok = true;
        if (email.isEmpty()) {
            binding.tilEmail.setError(getString(R.string.error_required));
            ok = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.setError(getString(R.string.error_email));
            ok = false;
        }
        if (password.isEmpty()) {
            binding.tilPassword.setError(getString(R.string.error_required));
            ok = false;
        }
        if (!ok) {
            if (binding.tilEmail.getError() != null) ButtonLoader.shake(binding.tilEmail);
            if (binding.tilPassword.getError() != null) ButtonLoader.shake(binding.tilPassword);
            return;
        }

        setLoading(true);
        currentCall = repository.login(email, password, new AuthRepository.Result() {
            @Override
            public void onSuccess(User user) {
                setLoading(false);
                Navigator.openDashboard(LoginActivity.this, user.role);
            }

            @Override
            public void onError(ApiError error) {
                setLoading(false);
                String emailError = error.fieldErrors.get("email");
                String passError = error.fieldErrors.get("password");
                if (emailError != null) {
                    binding.tilEmail.setError(emailError);
                    ButtonLoader.shake(binding.tilEmail);
                }
                if (passError != null) {
                    binding.tilPassword.setError(passError);
                    ButtonLoader.shake(binding.tilPassword);
                }
                if (emailError == null && passError == null) {
                    Snackbar.make(binding.getRoot(), error.message, Snackbar.LENGTH_LONG).show();
                }
            }
        });
    }

    private void setLoading(boolean loading) {
        if (loading) {
            ButtonLoader.start(binding.btnLogin, "Signing in...");
        } else {
            ButtonLoader.stop(binding.btnLogin);
        }
        binding.tilEmail.setEnabled(!loading);
        binding.tilPassword.setEnabled(!loading);
        binding.btnGoRegister.setEnabled(!loading);
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
