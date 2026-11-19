package com.example.campus_bike_rack_locator;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SignUpActivity extends AppCompatActivity {

    private final BikeRackRepository repo = new BikeRackRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        EditText first = findViewById(R.id.firstNameField);
        EditText last = findViewById(R.id.lastNameField);
        EditText username = findViewById(R.id.emailAddressField);
        EditText password = findViewById(R.id.passwordField);
        Button signUp = findViewById(R.id.signup_button);

        signUp.setOnClickListener(v -> {
            signUp.setEnabled(false);
            repo.register(first.getText().toString(), last.getText().toString(),
                    username.getText().toString(), password.getText().toString(),
                    new BikeRackRepository.Callback() {
                        @Override
                        public void onSuccess() {
                            startActivity(new Intent(SignUpActivity.this, MainActivity.class));
                            finishAffinity();
                        }

                        @Override
                        public void onError(String message) {
                            signUp.setEnabled(true);
                            Toast.makeText(SignUpActivity.this, message, Toast.LENGTH_LONG).show();
                        }
                    });
        });
    }
}