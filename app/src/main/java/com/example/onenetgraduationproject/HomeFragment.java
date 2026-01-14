package com.example.onenetgraduationproject;

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

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
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
import android.content.Context;

public class HomeFragment extends Fragment {
    private static final String TAG = "HomeFragment";
    // OneNet平台配置
    static String deviceName = "pi1";
    public static String productId = "v79fer6hC4";
    private String userId = "432577";
    private String userAccessKey = "uO/Y5Jr5Tj97TSpVaSvMxDlSqkSAsIw/P46fOxhHuZWoovs39BQIL98mAtEbWmml";
    private String queryUrl = "https://iot-api.heclouds.com/thingmodel/query-device-property?product_id=" + productId + "&device_name=" + deviceName;
    private MqttAndroidClient mqttAndroidClient;

    // UI组件
    private TextView tvTemperature, tvHumidity, tvSmoke, tvLightStatus, ivDoorIcon, ivFanIcon, tvSafetyStatus;
    // 绑定主线程Looper，确保消息分发稳定
    private Handler handler = new Handler(Looper.getMainLooper());
    static String token;

    // 属性值缓存
    private int temperature = 0;
    private int humidity = 0;
    private int smoke = 0;
    private int ledState = 0;
    private int doorState = 0;
    private int fan = 0;
    private int rs485 = 0;

    // 刷新任务的Runnable
    private Runnable refreshRunnable;

    public HomeFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // 初始化UI组件
        initViews(view);

        // 生成Token
        try {
            token = generateToken();
        } catch (Exception e) {
            Log.e(TAG, "生成Token失败: " + e.getMessage());
            showToast("认证失败");
            return view;
        }
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // 启动或恢复刷新任务
        startRefreshTask();
        Log.d(TAG, "HomeFragment 可见，启动数据刷新任务");
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    private void initViews(View view) {
        tvTemperature = view.findViewById(R.id.tv_temperature_value);
        tvHumidity = view.findViewById(R.id.tv_humidity_value);
        tvSmoke = view.findViewById(R.id.tv_smoke_value);
        tvLightStatus = view.findViewById(R.id.tv_light_status);
        ivDoorIcon = view.findViewById(R.id.tv_door_status);
        ivFanIcon = view.findViewById(R.id.tv_fan_status);
        tvSafetyStatus = view.findViewById(R.id.tv_safety_status);
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

    //保留该方法，不再调用
    private void pauseRefreshTask() {
        if (refreshRunnable != null) {
            handler.removeCallbacks(refreshRunnable);
        }
    }

    // 获取OneNet平台数据
    private void getOnenetData() {
        HttpsURLConnection connection = null;
        StringBuilder response = new StringBuilder();
        try {
            URL url = new URL(queryUrl);
            connection = (HttpsURLConnection) url.openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("authorization", token);
            Log.e(TAG,token);

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

            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject item = dataArray.getJSONObject(i);
                String identifier = item.optString("identifier");
                String valueStr = item.optString("value");

                if (valueStr == null || valueStr.isEmpty()) {
                    Log.w(TAG, "属性 " + identifier + " 的值为空，跳过解析");
                    continue;
                }
                if ("temp".equals(identifier)) {
                    temperature = Integer.parseInt(valueStr);
                } else if ("hum".equals(identifier)) {
                    humidity = Integer.parseInt(valueStr);
                } else if ("smoke".equals(identifier)) {
                    smoke = Integer.parseInt(valueStr);
                } else if ("led".equals(identifier)) {
                    ledState = Integer.parseInt(valueStr);
                } else if ("kaiguan".equals(identifier)) {
                    doorState = Integer.parseInt(valueStr);
                } else if ("fan".equals(identifier)) {
                    fan = Integer.parseInt(valueStr);
                } else if ("rs485".equals(identifier)) {
                    rs485 = Integer.parseInt(valueStr);
                } else {
                    Log.d(TAG, "忽略未使用的属性: " + identifier);
                }
            }

            if (getActivity() != null) {
                getActivity().runOnUiThread(this::updateUI);
            }

        } catch (JSONException | NumberFormatException e) {
            Log.e(TAG, "解析数据失败: " + e.getMessage());
            showToast("数据解析失败");
        }
    }

    // 更新UI显示
    private void updateUI() {
        tvTemperature.setText(String.valueOf(temperature));
        tvHumidity.setText(String.valueOf(humidity));
        tvSmoke.setText(String.valueOf(smoke));
        tvLightStatus.setText(ledState == 1 ? "已开启" : "已关闭");
        tvLightStatus.setTextColor(ledState == 1 ? Color.parseColor("#10B981") : Color.parseColor("#1F2937"));
        tvLightStatus.setBackgroundResource(ledState == 1 ? R.drawable.yuanjiao2 : R.drawable.yuanjiao1);
        ivDoorIcon.setText(doorState == 1 ? "已开启" : "已关闭");
        ivDoorIcon.setTextColor(doorState == 1 ? Color.parseColor("#10B981") : Color.parseColor("#1F2937"));
        ivDoorIcon.setBackgroundResource(doorState == 1 ? R.drawable.yuanjiao2 : R.drawable.yuanjiao1);
        ivFanIcon.setText(fan == 1 ? "已开启" : "已关闭");
        ivFanIcon.setTextColor(fan == 1 ? Color.parseColor("#10B981") : Color.parseColor("#1F2937"));
        ivFanIcon.setBackgroundResource(fan == 1 ? R.drawable.yuanjiao2 : R.drawable.yuanjiao1);
        tvSafetyStatus.setText(rs485 == 1 ? "异常" : "安全");
        tvSafetyStatus.setTextColor(rs485 == 1 ? Color.parseColor("#EF4444") : Color.parseColor("#000000"));
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