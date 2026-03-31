package com.example.onenetgraduationproject;

import static com.example.onenetgraduationproject.HomeFragment.deviceName;
import static com.example.onenetgraduationproject.HomeFragment.productId;
import static com.example.onenetgraduationproject.HomeFragment.token;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

public class ControlFragment extends Fragment {
    private static final String TAG = "ControlFragment";

    // MQTT配置
    //private String publishTopic = "$sys/v79fer6hC4/pi1/thing/property/post";
    private String setDevicePropertyUrl = "https://iot-api.heclouds.com/thingmodel/set-device-property";
    // UI组件
    private EditText etPropertyKey;
    private EditText etPropertyValue;
    private Button btnSendProperty;

    // 新增：设备控制开关
    private Switch switchBuzzer;
    private Switch switchFan;
    private Switch switchLed;
    private Switch switchKaiguan;

    private EditText etTemperatureLimit;
    private Button btnSetTemperatureLimit;
    private EditText etHumidityUpperLimit;
    private Button btnSetHumidityUpperLimit;

    public ControlFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // 全屏显示
        getActivity().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_control, container, false);

        // 初始化UI组件和逻辑
        initViews(view);

        return view;
    }

    private void initViews(View view) {
        // 初始化原有UI组件
        etPropertyKey = view.findViewById(R.id.et_property_key);
        etPropertyValue = view.findViewById(R.id.et_property_value);
        btnSendProperty = view.findViewById(R.id.btn_send_property);
        // 初始化阈值设置组件
        etTemperatureLimit = view.findViewById(R.id.et_temperature_limit);
        btnSetTemperatureLimit = view.findViewById(R.id.btn_set_temperature_limit);
        etHumidityUpperLimit = view.findViewById(R.id.et_humidity_upper_limit);
        btnSetHumidityUpperLimit = view.findViewById(R.id.btn_set_humidity_upper_limit);


        // 设置原有按钮点击事件
        btnSendProperty.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendProperty();
            }
        });

        // 初始化新增的开关组件
        switchBuzzer = view.findViewById(R.id.switch_buzzer);
        switchFan = view.findViewById(R.id.switch_fan);
        switchLed = view.findViewById(R.id.switch_led);
        switchKaiguan = view.findViewById(R.id.switch_kaiguan);

        // 设置开关点击事件
        setupSwitchListeners();

        setupThresholdListeners();
    }

    // 添加阈值设置监听器
    private void setupThresholdListeners() {
        // 温度阈值设置
        btnSetTemperatureLimit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String temperatureValue = etTemperatureLimit.getText().toString().trim();
                if (temperatureValue.isEmpty()) {
                    showToast("请输入温度阈值");
                    return;
                }
                setDeviceProperty("Temperature_Limit", temperatureValue);
            }
        });

        // 湿度上限设置
        btnSetHumidityUpperLimit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String humidityValue = etHumidityUpperLimit.getText().toString().trim();
                if (humidityValue.isEmpty()) {
                    showToast("请输入湿度上限");
                    return;
                }
                setDeviceProperty("HumidityUpperLimit", humidityValue);
            }
        });
    }

    // 新增：设置开关监听器
    private void setupSwitchListeners() {
        // 蜂鸣器开关
        switchBuzzer.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int value = isChecked ? 1 : 0;
            setDeviceProperty("buzzer", String.valueOf(value));
        });

        // 风扇开关
        switchFan.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int value = isChecked ? 1 : 0;
            setDeviceProperty("fan", String.valueOf(value));
        });

        // LED灯开关
        switchLed.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int value = isChecked ? 1 : 0;
            setDeviceProperty("led", String.valueOf(value));
        });

        // 开关控制
        switchKaiguan.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int value = isChecked ? 1 : 0;
            setDeviceProperty("kaiguan", String.valueOf(value));
        });
    }

    private void sendProperty() {
        String propertyKey = etPropertyKey.getText().toString().trim();
        String propertyValue = etPropertyValue.getText().toString().trim();

        // 验证输入
        if (propertyKey.isEmpty()) {
            showToast("请输入属性名");
            return;
        }

        if (propertyValue.isEmpty()) {
            showToast("请输入属性值");
            return;
        }

        // 调用setDeviceProperty方法，传递正确的参数
        setDeviceProperty(propertyKey, propertyValue);
    }

    // 显示Toast消息
    private void showToast(String message) {
        if (getActivity() != null) {
            Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
        }
    }

    // 设置设备属性方法（保持不变）
    private void setDeviceProperty(String propertyKey, Object propertyValue) {
        // 确保网络请求在子线程中执行
        new Thread(() -> {
            HttpsURLConnection connection = null;
            try {
                // 尝试将字符串类型的propertyValue转换为合适的数值类型
                Object finalValue = propertyValue;
                if (propertyValue instanceof String) {
                    String stringValue = (String) propertyValue;
                    try {
                        // 尝试解析为数字
                        if (stringValue.contains(".")) {
                            finalValue = Double.parseDouble(stringValue);
                        } else {
                            finalValue = Integer.parseInt(stringValue);
                        }
                    } catch (NumberFormatException e) {
                        // 解析失败，保持原字符串类型
                        Log.d(TAG, "属性值无法解析为数字，作为字符串处理: " + stringValue);
                    }
                }

                // 创建URL对象
                URL url = new URL(setDevicePropertyUrl);
                connection = (HttpsURLConnection) url.openConnection();

                // 设置请求方法为POST
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("authorization", token);
                connection.setDoOutput(true);

                // 构建JSON请求体
                JSONObject requestBody = new JSONObject();
                requestBody.put("product_id", productId);
                requestBody.put("device_name", deviceName);
                JSONObject params = new JSONObject();
                params.put(propertyKey, finalValue);

                requestBody.put("params", params);

                // 发送请求体
                OutputStream outputStream = connection.getOutputStream();
                outputStream.write(requestBody.toString().getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
                outputStream.close();
                // 打印请求体
                Log.d(TAG, "设置设备属性请求体: " + requestBody.toString());
                // 获取响应码
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // 读取响应内容
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    // 处理响应数据
                    String responseString = response.toString();
                    Log.d(TAG, "设置设备属性成功，响应: " + responseString);

                    // 在主线程显示成功信息
                    Object finalValue1 = finalValue;
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getActivity(), propertyKey + "已" + (finalValue1.equals(1) ? "开启" : "关闭"), Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "设置设备属性成功: " + propertyKey + " = " + finalValue1);
                    });
                } else {
                    // 请求失败，读取错误信息
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    errorReader.close();

                    Log.e(TAG, "设置设备属性失败，响应码: " + responseCode + "，错误信息: " + errorResponse.toString());

                    // 在主线程显示失败信息
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getActivity(), "设置设备属性失败，错误码: " + responseCode, Toast.LENGTH_SHORT).show();

                        // 失败时恢复开关状态
                        restoreSwitchState(propertyKey);
                    });
                }
            } catch (IOException | JSONException e) {
                Log.e(TAG, "设置设备属性异常: " + e.getMessage());
                // 在主线程显示异常信息
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getActivity(), "设置设备属性异常: " + e.getMessage(), Toast.LENGTH_SHORT).show();

                    // 异常时恢复开关状态
                    restoreSwitchState((String) propertyValue);
                });
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    // 新增：恢复开关状态（当控制失败时调用）
    private void restoreSwitchState(String propertyKey) {
        if (getActivity() == null) return;

        getActivity().runOnUiThread(() -> {
            switch (propertyKey) {
                case "buzzer":
                    switchBuzzer.setChecked(!switchBuzzer.isChecked());
                    break;
                case "fan":
                    switchFan.setChecked(!switchFan.isChecked());
                    break;
                case "led":
                    switchLed.setChecked(!switchLed.isChecked());
                    break;
                case "kaiguan":
                    switchKaiguan.setChecked(!switchKaiguan.isChecked());
                    break;
            }
        });
    }
}