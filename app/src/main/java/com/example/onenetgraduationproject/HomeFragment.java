package com.example.onenetgraduationproject;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.HttpsURLConnection;

public class HomeFragment extends Fragment {
    private static final String TAG = "HomeFragment";
    private static final String PREF_NAME = "OneNetSettings";

    // OneNet平台配置
    static String deviceName;
    public static String productId;
    private String userId;
    private String userAccessKey;
    private String queryUrl;

    // UI组件
    private TextView tv_title;

    // 实时水质传感器数据
    private TextView tv_temperature;
    private TextView tv_ph_value;
    private TextView tv_turbidity_percent;
    private TextView tv_water_level_percent;

    // 设备状态
    private TextView tv_heat;
    private TextView tv_buzzer;
    private TextView tv_relay1;
    private TextView tv_relay2;
    private TextView tv_relay3;
    private TextView tv_relay4;
    private TextView tv_relay5;

    // 阈值设置
    private TextView tv_tp_yz;
    private TextView tv_turbiditythreshold;
    private TextView tv_sw_yz;
    private TextView tv_sw_high_yz;
    private TextView tv_temp_water_yz;

    private Handler handler = new Handler(Looper.getMainLooper());
    static String token;
    private Runnable refreshRunnable;

    // 点击计数相关变量
    private int clickCount = 0;
    private long lastClickTime = 0;
    private static final long CLICK_TIME_INTERVAL = 500;

    public HomeFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
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
        tv_title = view.findViewById(R.id.tv_title);

        // 实时水质传感器数据
        tv_temperature = view.findViewById(R.id.tv_temperature);
        tv_ph_value = view.findViewById(R.id.tv_ph_value);
        tv_turbidity_percent = view.findViewById(R.id.tv_turbidity_percent);
        tv_water_level_percent = view.findViewById(R.id.tv_water_level_percent);

        // 设备状态
        tv_heat = view.findViewById(R.id.tv_heat);
        tv_buzzer = view.findViewById(R.id.tv_buzzer);
        tv_relay1 = view.findViewById(R.id.tv_relay1);
        tv_relay2 = view.findViewById(R.id.tv_relay2);
        tv_relay3 = view.findViewById(R.id.tv_relay3);
        tv_relay4 = view.findViewById(R.id.tv_relay4);
        tv_relay5 = view.findViewById(R.id.tv_relay5);

        // 阈值设置
        tv_tp_yz = view.findViewById(R.id.tv_tp_yz);
        tv_turbiditythreshold = view.findViewById(R.id.tv_turbiditythreshold);
        tv_sw_yz = view.findViewById(R.id.tv_sw_yz);
        tv_sw_high_yz = view.findViewById(R.id.tv_sw_high_yz);
        tv_temp_water_yz = view.findViewById(R.id.tv_temp_water_yz);

        // 标题点击事件（三次点击跳转到设置）
        tv_title.setOnClickListener(v -> {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastClickTime < CLICK_TIME_INTERVAL) {
                clickCount++;
            } else {
                clickCount = 1;
            }
            lastClickTime = currentTime;

            if (clickCount == 3) {
                clickCount = 0;
                Intent intent = new Intent(getActivity(), Settings.class);
                startActivity(intent);
            }
        });
    }

    private void loadSavedData() {
        SharedPreferences sharedPreferences = getActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        deviceName = sharedPreferences.getString("deviceName", "");
        productId = sharedPreferences.getString("productId", "");
        userId = sharedPreferences.getString("userId", "");
        userAccessKey = sharedPreferences.getString("userAccessKey", "");

        if (isConfigurationComplete()) {
            startDataRefresh();
        } else {
            showConfigurationPrompt();
        }
    }

    private boolean isConfigurationComplete() {
        return deviceName != null && !deviceName.isEmpty() &&
                productId != null && !productId.isEmpty() &&
                userId != null && !userId.isEmpty() &&
                userAccessKey != null && !userAccessKey.isEmpty();
    }

    private void startDataRefresh() {
        queryUrl = "https://iot-api.heclouds.com/thingmodel/query-device-property?product_id=" + productId + "&device_name=" + deviceName;

        try {
            token = generateToken();
        } catch (Exception e) {
            Log.e(TAG, "生成Token失败: " + e.getMessage());
            showToast("认证失败");
            return;
        }

        startRefreshTask();
    }

    private void showConfigurationPrompt() {
        if (refreshRunnable != null) {
            handler.removeCallbacks(refreshRunnable);
        }
    }

    private void startRefreshTask() {
        if (refreshRunnable == null) {
            refreshRunnable = new Runnable() {
                @Override
                public void run() {
                    new Thread(() -> getOnenetData()).start();
                    handler.postDelayed(this, 3000);
                }
            };
        }
        handler.removeCallbacks(refreshRunnable);
        handler.postDelayed(refreshRunnable, 1000);
        Log.d(TAG, "数据刷新任务已启动");
    }

    private void getOnenetData() {
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

    private void parseAndShowData(String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);
            JSONArray dataArray = jsonObject.optJSONArray("data");

            if (dataArray == null || dataArray.length() == 0) {
                showToast("暂无设备数据");
                return;
            }

            // 初始化所有变量为默认值
            String temperature = "--";
            String ph_value = "--";
            String turbidity_percent = "--";
            String water_level_percent = "--";

            String heat = "--";
            String buzzer = "--";
            String relay1 = "--";
            String relay2 = "--";
            String relay3 = "--";
            String relay4 = "--";
            String relay5 = "--";

            String tp_yz = "--";
            String turbiditythreshold = "--";
            String sw_yz = "--";
            String sw_high_yz = "--";
            String temp_water_yz = "--";

            // 遍历所有数据项
            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject item = dataArray.getJSONObject(i);
                String identifier = item.optString("identifier");
                String valueStr = item.optString("value");

                if (valueStr != null && !valueStr.isEmpty()) {
                    Log.d(TAG, identifier + ": " + valueStr);

                    // 根据identifier分配到对应的变量
                    switch (identifier) {
                        // 水质传感器数据
                        case "temperature":
                            temperature = valueStr + " °C";
                            break;
                        case "ph_value":
                            ph_value = valueStr;
                            break;
                        case "turbidity_percent":
                            turbidity_percent = valueStr + " %";
                            break;
                        case "water_level_percent":
                            water_level_percent = valueStr + " %";
                            break;

                        // 设备状态
                        case "Heat":
                            heat = "true".equalsIgnoreCase(valueStr) ? "开启" : "关闭";
                            break;
                        case "buzzer":
                            buzzer = "true".equalsIgnoreCase(valueStr) ? "开启" : "关闭";
                            break;
                        case "RELAY1":
                            relay1 = "true".equalsIgnoreCase(valueStr) ? "运行" : "停止";
                            break;
                        case "RELAY2":
                            relay2 = "true".equalsIgnoreCase(valueStr) ? "运行" : "停止";
                            break;
                        case "RELAY3":
                            relay3 = "true".equalsIgnoreCase(valueStr) ? "运行" : "停止";
                            break;
                        case "RELAY4":
                            relay4 = "true".equalsIgnoreCase(valueStr) ? "运行" : "停止";
                            break;
                        case "RELAY5":
                            relay5 = "true".equalsIgnoreCase(valueStr) ? "运行" : "停止";
                            break;

                        // 阈值设置
                        case "tp_yz":
                            tp_yz = valueStr + " °C";
                            break;
                        case "Turbiditythreshold":
                            turbiditythreshold = valueStr;
                            break;
                        case "sw_yz":
                            sw_yz = valueStr + " %";
                            break;
                        case "sw_high_yz":
                            sw_high_yz = valueStr + " %";
                            break;
                        case "temp_water_yz":
                            temp_water_yz = valueStr + " °C";
                            break;
                    }
                } else {
                    Log.w(TAG, "属性 " + identifier + " 的值为空");
                }
            }

            // 在UI线程更新所有TextView
            if (getActivity() != null) {
                String finalTemperature = temperature;
                String finalPh_value = ph_value;
                String finalTurbidity_percent1 = turbidity_percent;
                String finalWater_level_percent1 = water_level_percent;
                String finalHeat = heat;
                String finalBuzzer = buzzer;
                String finalRelay = relay1;
                String finalRelay1 = relay2;
                String finalRelay2 = relay3;
                String finalRelay3 = relay4;
                String finalRelay4 = relay5;
                String finalTp_yz = tp_yz;
                String finalTurbiditythreshold = turbiditythreshold;
                String finalSw_yz = sw_yz;
                String finalSw_high_yz = sw_high_yz;
                String finalTemp_water_yz = temp_water_yz;
                getActivity().runOnUiThread(() -> {
                    // 更新实时水质传感器数据
                    tv_temperature.setText(finalTemperature);
                    tv_ph_value.setText(finalPh_value);
                    tv_turbidity_percent.setText(finalTurbidity_percent1);
                    tv_water_level_percent.setText(finalWater_level_percent1);

                    // 更新设备状态
                    tv_heat.setText(finalHeat);
                    tv_buzzer.setText(finalBuzzer);
                    tv_relay1.setText(finalRelay);
                    tv_relay2.setText(finalRelay1);
                    tv_relay3.setText(finalRelay2);
                    tv_relay4.setText(finalRelay3);
                    tv_relay5.setText(finalRelay4);

                    // 更新阈值设置
                    tv_tp_yz.setText(finalTp_yz);
                    tv_turbiditythreshold.setText(finalTurbiditythreshold);
                    tv_sw_yz.setText(finalSw_yz);
                    tv_sw_high_yz.setText(finalSw_high_yz);
                    tv_temp_water_yz.setText(finalTemp_water_yz);
                });
            }

        } catch (JSONException e) {
            Log.e(TAG, "解析数据失败: " + e.getMessage());
            showToast("数据解析失败");
        }
    }

    private String generateToken() throws UnsupportedEncodingException, NoSuchAlgorithmException, InvalidKeyException {
        String version = "2020-05-29";
        String resourceName = "userid/" + userId;
        String expirationTime = String.valueOf(System.currentTimeMillis() / 1000 + 604800);
        String signatureMethod = TokenUtil.SignatureMethod.SHA1.name().toLowerCase();
        return TokenUtil.assembleToken(version, resourceName, expirationTime, signatureMethod, userAccessKey);
    }

    private void showToast(String message) {
        if (getActivity() != null && getContext() != null) {
            getActivity().runOnUiThread(() -> Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show());
        }
    }
}