package com.example.onenetgraduationproject;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.EditText;
import android.widget.Toast;

public class Settings extends Activity {

    // 定义SharedPreferences的文件名
    private static final String PREF_NAME = "OneNetSettings";

    // UI组件
    private EditText deviceNameEditText;
    private EditText productIdEditText;
    private EditText userIdEditText;
    private EditText userAccessKeyEditText;
    private Button saveButton;
    private ImageButton backButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 设置布局文件
        setContentView(R.layout.settings);
        initViews();
        loadSavedData(); // 加载已保存的数据
    }

    // 初始化界面元素
    private void initViews() {
        // 从布局文件中获取控件实例
        deviceNameEditText = findViewById(R.id.deviceName);
        productIdEditText = findViewById(R.id.productId);
        userIdEditText = findViewById(R.id.userId);
        userAccessKeyEditText = findViewById(R.id.userAccessKey);
        saveButton = findViewById(R.id.button);
        backButton = findViewById(R.id.imageButton);

        // 设置返回按钮的点击事件
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish(); // 关闭当前活动，返回上一个活动
            }
        });

        // 设置保存按钮的点击事件
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
            }
        });
    }

    // 保存设置数据到SharedPreferences
    private void saveSettings() {
        // 获取用户输入的数据
        String deviceName = deviceNameEditText.getText().toString().trim();
        String productId = productIdEditText.getText().toString().trim();
        String userId = userIdEditText.getText().toString().trim();
        String userAccessKey = userAccessKeyEditText.getText().toString().trim();

        // 获取SharedPreferences实例
        SharedPreferences sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        // 存储数据
        editor.putString("deviceName", deviceName);
        editor.putString("productId", productId);
        editor.putString("userId", userId);
        editor.putString("userAccessKey", userAccessKey);

        // 提交保存
        boolean isSaved = editor.commit();

        // 显示保存结果提示
        if (isSaved) {
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "保存失败，请重试", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadSavedData() {

        SharedPreferences sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String deviceName = sharedPreferences.getString("deviceName", "");
        String productId = sharedPreferences.getString("productId", "");
        String userId = sharedPreferences.getString("userId", "");
        String userAccessKey = sharedPreferences.getString("userAccessKey", "");

        // 设置到EditText中
        deviceNameEditText.setText(deviceName);
        productIdEditText.setText(productId);
        userIdEditText.setText(userId);
        userAccessKeyEditText.setText(userAccessKey);
    }
}