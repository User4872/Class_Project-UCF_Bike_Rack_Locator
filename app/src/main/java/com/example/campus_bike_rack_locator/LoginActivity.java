package com.example.campus_bike_rack_locator;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    private final BikeRackRepository repo = new BikeRackRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Already signed in from a previous run: skip straight to the map.
        if (repo.isLoggedIn()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login);
        EditText username = findViewById(R.id.editTextText2);
        EditText password = findViewById(R.id.editTextText3);
        Button loginButton = findViewById(R.id.login_button);
        TextView signupLink = findViewById(R.id.signup_link);

        loginButton.setOnClickListener(v -> {
            loginButton.setEnabled(false);
            repo.login(username.getText().toString(), password.getText().toString(),
                    new BikeRackRepository.Callback() {
                        @Override
                        public void onSuccess() {
                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                            finish();
                        }

                        @Override
                        public void onError(String message) {
                            loginButton.setEnabled(true);
                            Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
                        }
                    });
        });

        signupLink.setOnClickListener(v ->
                startActivity(new Intent(this, SignUpActivity.class)));
    }
}