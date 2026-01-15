package com.example.onenetgraduationproject;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.io.UnsupportedEncodingException;

import javax.net.ssl.HttpsURLConnection;

public class HomeFragment extends Fragment {
    private static final String TAG = "HomeFragment";
    // 定义SharedPreferences的文件名
    private static final String PREF_NAME = "OneNetSettings";

    // OneNet平台配置（完全从用户输入获取）
    static String deviceName;
    public static String productId;
    private String userId;
    private String userAccessKey;
    private String queryUrl;

    // UI组件
    private TextView tvAllData,tv_title;

    private Handler handler = new Handler(Looper.getMainLooper());
    static String token;

    // 刷新任务的Runnable
    private Runnable refreshRunnable;

    // 点击计数相关变量
    private int clickCount = 0; // 记录点击次数
    private long lastClickTime = 0; // 记录上次点击时间
    private static final long CLICK_TIME_INTERVAL = 500; // 点击时间间隔阈值（毫秒）

    public HomeFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // 初始化UI组件
        initViews(view);
        loadSavedData();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadSavedData();
    }

    @Override
    public void onPause() {
        super.onPause();
        // 停止刷新任务
        if (refreshRunnable != null) {
            handler.removeCallbacks(refreshRunnable);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    private void initViews(View view) {
        tvAllData = view.findViewById(R.id.tv_all_data);
        tv_title = view.findViewById(R.id.tv_title);
        // 点击事件
        tv_title.setOnClickListener(v -> {
            // 计算当前时间与上次点击时间的间隔
            long currentTime = System.currentTimeMillis();

            // 如果点击间隔在阈值内，点击次数加一
            if (currentTime - lastClickTime < CLICK_TIME_INTERVAL) {
                clickCount++;
            } else {
                // 超过时间间隔，重置点击次数
                clickCount = 1;
            }

            // 更新上次点击时间
            lastClickTime = currentTime;

            // 点击三次时执行跳转
            if (clickCount == 3) {
                // 重置点击计数
                clickCount = 0;

                // 切换到settings界面
                Intent intent = new Intent(getActivity(), Settings.class);
                startActivity(intent);
            }
        });
    }

    // 从SharedPreferences加载保存的数据（用户输入的配置）
    private void loadSavedData() {
        // 获取SharedPreferences实例
        SharedPreferences sharedPreferences = getActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        // 完全从用户输入获取，不使用任何硬编码的默认值
        deviceName = sharedPreferences.getString("deviceName", "");
        productId = sharedPreferences.getString("productId", "");
        userId = sharedPreferences.getString("userId", "");
        userAccessKey = sharedPreferences.getString("userAccessKey", "");

        // 检查是否所有必要的配置都已设置
        if (isConfigurationComplete()) {
            // 配置完整，启动刷新任务
            startDataRefresh();
        } else {
            // 配置不完整，提示用户去设置界面
            showConfigurationPrompt();
        }
    }

    // 检查配置是否完整
    private boolean isConfigurationComplete() {
        return deviceName != null && !deviceName.isEmpty() &&
                productId != null && !productId.isEmpty() &&
                userId != null && !userId.isEmpty() &&
                userAccessKey != null && !userAccessKey.isEmpty();
    }

    // 启动数据刷新（仅当配置完整时）
    private void startDataRefresh() {
        // 构建查询URL
        queryUrl = "https://iot-api.heclouds.com/thingmodel/query-device-property?product_id=" + productId + "&device_name=" + deviceName;

        // 生成Token
        try {
            token = generateToken();
        } catch (Exception e) {
            Log.e(TAG, "生成Token失败: " + e.getMessage());
            showToast("认证失败");
            return;
        }

        // 启动刷新任务
        startRefreshTask();
    }

    // 显示配置提示
    private void showConfigurationPrompt() {
        // 停止正在运行的刷新任务
        if (refreshRunnable != null) {
            handler.removeCallbacks(refreshRunnable);
        }

        // 更新UI显示配置提示
//        if (getActivity() != null) {
//            getActivity().runOnUiThread(() -> {
//                tvAllData.setText("请点击标题三次进入设置界面，配置OneNet平台信息");
//                tvAllData.setTextColor(Color.RED);
//            });
//        }
    }

    // 启动定时刷新任务
    private void startRefreshTask() {
        if (refreshRunnable == null) {
            refreshRunnable = new Runnable() {
                @Override
                public void run() {
                    // 子线程执行网络请求
                    new Thread(() -> getOnenetData()).start();
                    // 循环执行（3秒一次）
                    handler.postDelayed(this, 3000);
                }
            };
        }
        // 移除旧任务（避免重复），重新启动任务
        handler.removeCallbacks(refreshRunnable);
        handler.postDelayed(refreshRunnable, 1000);
        Log.d(TAG, "数据刷新任务已启动");
    }

    // 获取OneNet平台数据
    private void getOnenetData() {
        // 再次检查配置是否完整（防止在任务运行过程中配置被删除）
        if (!isConfigurationComplete()) {
            showConfigurationPrompt();
            return;
        }

        HttpsURLConnection connection = null;
        StringBuilder response = new StringBuilder();
        try {
            URL url = new URL(queryUrl);
            connection = (HttpsURLConnection) url.openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("authorization", token);
            Log.e(TAG, token);

            if (connection.getResponseCode() == HttpsURLConnection.HTTP_OK) {
                InputStream is = connection.getInputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                br.close();
                is.close();
                parseAndShowData(response.toString());
            } else {
                Log.e(TAG, "请求失败，响应码: " + connection.getResponseCode());
                showToast("数据获取失败");
            }
        } catch (Exception e) {
            Log.e(TAG, "获取数据异常: " + e.getMessage());
            showToast("网络异常");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        Log.d(TAG, "原始获取OneNet平台数据: " + response.toString());
    }

    // 解析JSON并展示数据
    private void parseAndShowData(String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);
            JSONArray dataArray = jsonObject.optJSONArray("data");

            if (dataArray == null || dataArray.length() == 0) {
                showToast("暂无设备数据");
                return;
            }

            // 构建数据显示字符串
            StringBuilder dataBuilder = new StringBuilder();

            // 遍历所有数据项，直接打印identifier和value
            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject item = dataArray.getJSONObject(i);
                String identifier = item.optString("identifier");
                String valueStr = item.optString("value");

                if (valueStr != null && !valueStr.isEmpty()) {
                    Log.d(TAG, identifier + ": " + valueStr);
                    dataBuilder.append(identifier).append(": " ).append(valueStr).append("\n");
                } else {
                    Log.w(TAG, "属性 " + identifier + " 的值为空");
                    dataBuilder.append(identifier).append(": 空值\n");
                }
            }

            // 在UI线程更新TextView
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    tvAllData.setText(dataBuilder.toString());
                    tvAllData.setTextColor(Color.BLACK); // 恢复正常颜色
                });
            }

        } catch (JSONException e) {
            Log.e(TAG, "解析数据失败: " + e.getMessage());
            showToast("数据解析失败");
        }
    }

    // 生成认证Token
    private String generateToken() throws UnsupportedEncodingException, NoSuchAlgorithmException, InvalidKeyException {
        String version = "2020-05-29";
        String resourceName = "userid/" + userId;
        String expirationTime = String.valueOf(System.currentTimeMillis() / 1000 + 604800);
        String signatureMethod = TokenUtil.SignatureMethod.SHA1.name().toLowerCase();
        return TokenUtil.assembleToken(version, resourceName, expirationTime, signatureMethod, userAccessKey);
    }

    // 显示Toast消息的辅助方法
    private void showToast(String message) {
        if (getActivity() != null && getContext() != null) {
            getActivity().runOnUiThread(() -> Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show());
        }
    }
}