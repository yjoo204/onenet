package com.example.onenetgraduationproject;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class ResetPasswordActivity extends AppCompatActivity {

    private EditText etUsername;
    private EditText etPassword;
    private EditText etConfirmPassword;
    private Button btnReset;
    private Button btnCancel;
    private UserManager userManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);

        // 初始化组件
        etUsername = findViewById(R.id.et_reset_username);
        etPassword = findViewById(R.id.et_reset_password);
        etConfirmPassword = findViewById(R.id.et_reset_confirm_password);
        btnReset = findViewById(R.id.btn_reset_submit);
        btnCancel = findViewById(R.id.btn_reset_cancel);
        userManager = new UserManager(this);

        // 设置重置密码按钮点击事件
        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String username = etUsername.getText().toString().trim();
                String password = etPassword.getText().toString().trim();
                String confirmPassword = etConfirmPassword.getText().toString().trim();

                // 验证输入
                if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                    Toast.makeText(ResetPasswordActivity.this, "所有字段都不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (password.length() < 6) {
                    Toast.makeText(ResetPasswordActivity.this, "密码长度不能少于6个字符", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!password.equals(confirmPassword)) {
                    Toast.makeText(ResetPasswordActivity.this, "两次输入的密码不一致", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 检查用户是否存在
                if (!userManager.userExists(username)) {
                    Toast.makeText(ResetPasswordActivity.this, "用户不存在", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 重置密码
                if (userManager.resetPassword(username, password)) {
                    Toast.makeText(ResetPasswordActivity.this, "密码重置成功", Toast.LENGTH_SHORT).show();
                    finish(); // 关闭重置密码活动
                } else {
                    Toast.makeText(ResetPasswordActivity.this, "密码重置失败", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // 设置取消按钮点击事件
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish(); // 关闭重置密码活动
            }
        });
    }
}