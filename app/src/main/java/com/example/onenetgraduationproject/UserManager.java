package com.example.onenetgraduationproject;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.reflect.TypeToken;
import com.google.gson.Gson;


import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class UserManager {
    private static final String TAG = "UserManager";
    private static final String PREF_NAME = "user_prefs";
    private static final String KEY_USERS = "users";
    private static final String KEY_CURRENT_USER = "current_user";

    private SharedPreferences sharedPreferences;
    private Gson gson;

    public UserManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    // 保存用户列表
    private void saveUsers(List<User> users) {
        String json = gson.toJson(users);
        sharedPreferences.edit().putString(KEY_USERS, json).apply();
    }

    // 获取所有用户
    private List<User> getUsers() {
        String json = sharedPreferences.getString(KEY_USERS, null);
        if (json == null) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<List<User>>() {}.getType();
        return gson.fromJson(json, type);
    }

    // 注册新用户
    public boolean registerUser(String username, String password) {
        List<User> users = getUsers();

        // 检查用户名是否已存在
        for (User user : users) {
            if (user.getUsername().equals(username)) {
                return false; // 用户名已存在
            }
        }

        // 添加新用户
        users.add(new User(username, password));
        saveUsers(users);
        return true;
    }

    // 用户登录
    public boolean loginUser(String username, String password) {
        List<User> users = getUsers();

        for (User user : users) {
            if (user.getUsername().equals(username) && user.getPassword().equals(password)) {
                // 保存当前登录用户
                sharedPreferences.edit().putString(KEY_CURRENT_USER, username).apply();
                return true;
            }
        }

        return false; // 用户名或密码错误
    }

    // 检查用户是否已登录
    public boolean isUserLoggedIn() {
        return sharedPreferences.getString(KEY_CURRENT_USER, null) != null;
    }

    // 获取当前登录用户
    public String getCurrentUsername() {
        return sharedPreferences.getString(KEY_CURRENT_USER, null);
    }

    // 用户登出
    public void logoutUser() {
        sharedPreferences.edit().remove(KEY_CURRENT_USER).apply();
    }
}