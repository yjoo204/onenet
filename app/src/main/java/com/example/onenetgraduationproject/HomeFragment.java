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
    private TextView tv_title;

    // 实时传感器数据
    private TextView tv_temperature;
    private TextView tv_humidity;
    private TextView tv_bmp_press;
    private TextView tv_bmp_asl;
    private TextView tv_pm2_5;
    private TextView tv_light_val;
    private TextView tv_uv_intensity;
    private TextView tv_led_status;

    // 阈值设置
    private TextView tv_temperature_threshold;
    private TextView tv_humidity_threshold;
    private TextView tv_pressure_threshold;
    private TextView tv_altitude_threshold;
    private TextView tv_ultraviolet_threshold;

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
        tv_title = view.findViewById(R.id.tv_title);

        // 实时传感器数据
        tv_temperature = view.findViewById(R.id.tv_temperature);
        tv_humidity = view.findViewById(R.id.tv_humidity);
        tv_bmp_press = view.findViewById(R.id.tv_bmp_press);
        tv_bmp_asl = view.findViewById(R.id.tv_bmp_asl);
        tv_pm2_5 = view.findViewById(R.id.tv_pm2_5);
        tv_light_val = view.findViewById(R.id.tv_light_val);
        tv_uv_intensity = view.findViewById(R.id.tv_uv_intensity);
        tv_led_status = view.findViewById(R.id.tv_led_status);

        // 阈值设置
        tv_temperature_threshold = view.findViewById(R.id.tv_temperature_threshold);
        tv_humidity_threshold = view.findViewById(R.id.tv_humidity_threshold);
        tv_pressure_threshold = view.findViewById(R.id.tv_pressure_threshold);
        tv_altitude_threshold = view.findViewById(R.id.tv_altitude_threshold);
        tv_ultraviolet_threshold = view.findViewById(R.id.tv_ultraviolet_threshold);

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

            // 存储所有属性数据
            String temperature = "--";
            String humidity = "--";
            String bmp_press = "--";
            String bmp_asl = "--";
            String pm2_5 = "--";
            String light_val = "--";
            String uv_intensity = "--";
            String led_status = "--";
            String temperature_threshold = "--";
            String humidity_threshold = "--";
            String pressure_threshold = "--";
            String altitude_threshold = "--";
            String ultraviolet_threshold = "--";

            // 遍历所有数据项
            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject item = dataArray.getJSONObject(i);
                String identifier = item.optString("identifier");
                String valueStr = item.optString("value");

                if (valueStr != null && !valueStr.isEmpty()) {
                    Log.d(TAG, identifier + ": " + valueStr);

                    // 根据identifier分配到对应的变量
                    switch (identifier) {
                        case "temperature":
                            temperature = valueStr + " °C";
                            break;
                        case "humidity":
                            humidity = valueStr + " %";
                            break;
                        case "bmp_press":
                            bmp_press = valueStr + " hPa";
                            break;
                        case "bmp_asl":
                            bmp_asl = valueStr + " m";
                            break;
                        case "pm2_5":
                            pm2_5 = valueStr;
                            break;
                        case "light_val":
                            light_val = valueStr + " lux";
                            break;
                        case "uv_intensity":
                            uv_intensity = valueStr;
                            break;
                        case "LED_status":
                            led_status = "true".equalsIgnoreCase(valueStr) ? "开启" : "关闭";
                            break;
                        case "Temperature_threshold":
                            temperature_threshold = valueStr + " °C";
                            break;
                        case "Humidity_threshold":
                            humidity_threshold = valueStr + " %";
                            break;
                        case "Pressure_threshold":
                            pressure_threshold = valueStr + " hPa";
                            break;
                        case "Altitude_threshold":
                            altitude_threshold = valueStr + " m";
                            break;
                        case "Ultraviolet_Threshold":
                            ultraviolet_threshold = valueStr;
                            break;
                    }
                } else {
                    Log.w(TAG, "属性 " + identifier + " 的值为空");
                }
            }

            // 在UI线程更新所有TextView
            if (getActivity() != null) {
                String finalTemperature = temperature;
                String finalHumidity = humidity;
                String finalBmp_press = bmp_press;
                String finalBmp_asl = bmp_asl;
                String finalPm2_ = pm2_5;
                String finalLight_val = light_val;
                String finalUv_intensity = uv_intensity;
                String finalLed_status = led_status;
                String finalTemperature_threshold = temperature_threshold;
                String finalHumidity_threshold = humidity_threshold;
                String finalPressure_threshold = pressure_threshold;
                String finalAltitude_threshold = altitude_threshold;
                String finalUltraviolet_threshold = ultraviolet_threshold;
                getActivity().runOnUiThread(() -> {
                    // 更新实时传感器数据
                    tv_temperature.setText(finalTemperature);
                    tv_humidity.setText(finalHumidity);
                    tv_bmp_press.setText(finalBmp_press);
                    tv_bmp_asl.setText(finalBmp_asl);
                    tv_pm2_5.setText(finalPm2_);
                    tv_light_val.setText(finalLight_val);
                    tv_uv_intensity.setText(finalUv_intensity);
                    tv_led_status.setText(finalLed_status);

                    // 更新阈值设置
                    tv_temperature_threshold.setText(finalTemperature_threshold);
                    tv_humidity_threshold.setText(finalHumidity_threshold);
                    tv_pressure_threshold.setText(finalPressure_threshold);
                    tv_altitude_threshold.setText(finalAltitude_threshold);
                    tv_ultraviolet_threshold.setText(finalUltraviolet_threshold);
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