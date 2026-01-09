package com.example.onenetgraduationproject;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;

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

public class AttributeDisplayActivity extends AppCompatActivity {
    private static final String TAG = "AttributeDisplay";
    // 添加静态变量用于共享MQTT客户端
    private BottomNavigationView bottomNavigationView;
    private static MqttAndroidClient sharedMqttClient;
    // OneNet平台配置
    private String deviceName = "pi1";
    private String productId = "v79fer6hC4";
    private String userId = "432577";
    private String userAccessKey = "uO/Y5Jr5Tj97TSpVaSvMxDlSqkSAsIw/P46fOxhHuZWoovs39BQIL98mAtEbWmml";
    private String queryUrl = "https://iot-api.heclouds.com/thingmodel/query-device-property?product_id=" + productId + "&device_name=" + deviceName;
    // MQTT配置（从MainActivity迁移过来）
    private String mqttServerUri = "tcp://mqtts.heclouds.com:1883";
    private String clientId = "pi1";
    private String mqttUsername = "v79fer6hC4";
    private String mqttPassword = "version=2018-10-31&res=products%2Fv79fer6hC4&et=1806681600&method=md5&sign=o2KPjpSQeqL8Pb7TiC03Dw%3D%3D";
    private String subscribeTopic = "$sys/v79fer6hC4/pi1/thing/property/post/reply";
    private String publishTopic = "$sys/v79fer6hC4/pi1/thing/property/post";
    private MqttAndroidClient mqttAndroidClient;
    // UI组件 - 只保留布局中存在的控件
    private TextView tvTemperature, tvHumidity, tvSmoke, tvLightStatus, ivDoorIcon, ivFanIcon, tvSafetyStatus;
    private Handler handler = new Handler();
    private String token;
    // 属性值缓存 - 只保留布局中使用的属性
    private int temperature = 0;
    private int humidity = 0;
    private int smoke = 0;
    private int ledState = 0;
    private int doorState = 0;
    private int fan = 0;
    private int rs485 = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_attribute_display);

        // 全屏显示
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        // 初始化UI组件
        initViews();
        // 设置底部导航栏
        setupBottomNavigation();

        // 生成Token
        try {
            token = generateToken();
        } catch (Exception e) {
            Log.e(TAG, "生成Token失败: " + e.getMessage());
            showToast("认证失败");
            return;
        }

        // 初始化MQTT并连接（从MainActivity迁移过来）
        initMqtt();

        // 启动定时刷新任务
        startRefreshTask();
    }

    private void initViews() {
        // 状态文本 - 与布局ID匹配，使用findViewById
        tvTemperature = findViewById(R.id.tv_temperature_value);
        tvHumidity = findViewById(R.id.tv_humidity_value);
        tvSmoke = findViewById(R.id.tv_smoke_value);
        tvLightStatus = findViewById(R.id.tv_light_status);
        ivDoorIcon = findViewById(R.id.tv_door_status);
        ivFanIcon = findViewById(R.id.tv_fan_status);
        tvSafetyStatus = findViewById(R.id.tv_safety_status);
        bottomNavigationView = findViewById(R.id.bottom_navigation);
    }
    // 设置底部导航栏
    private void setupBottomNavigation() {
        bottomNavigationView.setOnNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                // 显示首页布局
                showHomeLayout();
                return true;
            } else if (itemId == R.id.nav_control) {
                // 跳转到控制界面
                Intent controlIntent = new Intent(AttributeDisplayActivity.this, MainActivity.class);
                startActivity(controlIntent);
                return true;
            } else if (itemId == R.id.nav_history) {
                // 跳转到历史数据界面
                Intent historyIntent = new Intent(AttributeDisplayActivity.this, Control.class);
                startActivity(historyIntent);
                return true;
            }
            return false;
        });
        // 设置当前选中的菜单项为"首页"
        bottomNavigationView.setSelectedItemId(R.id.nav_home);
    }

    // 显示首页布局
    private void showHomeLayout() {
        // 当前已经是首页，不需要做任何操作
        showToast("当前页面");
    }
    // 初始化MQTT（从MainActivity迁移过来）
    private void initMqtt() {
        // 使用getApplicationContext()获取上下文
        mqttAndroidClient = new MqttAndroidClient(getApplicationContext(), mqttServerUri, clientId);
        sharedMqttClient = mqttAndroidClient; // 设置共享客户端

        // 设置MQTT回调
        mqttAndroidClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                String errorMessage = "MQTT连接丢失";
                if (cause != null && cause.getMessage() != null) {
                    errorMessage += ": " + cause.getMessage();
                }
                Log.d(TAG, errorMessage);

                // 尝试重连
                connectToMqtt();
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                String messageContent = new String(message.getPayload(), StandardCharsets.UTF_8);
                Log.d(TAG, "收到MQTT消息: " + messageContent);

                // 解析回复消息，更新UI
                parseMqttReply(messageContent);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                Log.d(TAG, "消息发送完成");
            }
        });

        // 连接MQTT服务器
        connectToMqtt();
    }

    // 添加静态方法供其他组件获取MQTT客户端
    public static MqttAndroidClient getSharedMqttClient() {
        return sharedMqttClient;
    }

    // 连接到MQTT服务器（从MainActivity迁移过来）
    private void connectToMqtt() {
        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setUserName(mqttUsername);
        mqttConnectOptions.setPassword(mqttPassword.toCharArray());
        mqttConnectOptions.setCleanSession(true);
        mqttConnectOptions.setConnectionTimeout(10);
        mqttConnectOptions.setKeepAliveInterval(60);

        try {
            mqttAndroidClient.connect(mqttConnectOptions, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(TAG, "MQTT连接成功");
                    runOnUiThread(() -> {
                        // 连接成功后订阅主题
                        subscribeToTopic();
                    });
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG, "MQTT连接失败: " + exception.getMessage());
                    runOnUiThread(() -> {
                        // 5秒后重试连接
                        handler.postDelayed(() -> connectToMqtt(), 5000);
                    });
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    // 订阅主题（从MainActivity迁移过来）
    private void subscribeToTopic() {
        try {
            mqttAndroidClient.subscribe(subscribeTopic, 1, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(TAG, "订阅主题成功: " + subscribeTopic);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG, "订阅主题失败: " + exception.getMessage());
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    // 发布MQTT消息（控制LED等属性）
    private void publishMqttMessage(String propertyKey, Object value) {
        if (mqttAndroidClient == null || !mqttAndroidClient.isConnected()) {
            Log.e(TAG, "MQTT未连接，无法发布消息");
            showToast("MQTT未连接，无法发送消息");
            return;
        }

        try {
            // 构建发布内容
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("id", "123");
            jsonObject.put("version", "1.0");

            JSONObject params = new JSONObject();
            JSONObject property = new JSONObject();
            property.put("value", value);
            params.put(propertyKey, property);

            jsonObject.put("params", params);

            String message = jsonObject.toString();
            MqttMessage mqttMessage = new MqttMessage();
            mqttMessage.setPayload(message.getBytes(StandardCharsets.UTF_8));
            mqttMessage.setQos(1);

            // 发布消息
            mqttAndroidClient.publish(publishTopic, mqttMessage, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(TAG, "消息发布成功: " + message);
                    showToast("已发送: " + propertyKey + " = " + value);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG, "消息发布失败: " + exception.getMessage());
                    showToast("发送失败: " + exception.getMessage());
                }
            });
        } catch (JSONException | MqttException e) {
            Log.e(TAG, "发布消息异常: " + e.getMessage());
        }
    }

    // 解析MQTT回复消息
    private void parseMqttReply(String message) {
        try {
            JSONObject json = new JSONObject(message);
            String code = json.optString("code");

            if ("0".equals(code)) {
                showToast("属性更新成功");
            } else {
                String errorMsg = json.optString("msg", "更新失败");
                showToast(errorMsg);
            }
        } catch (JSONException e) {
            Log.e(TAG, "解析MQTT回复失败: " + e.getMessage());
        }
    }

    // 启动定时刷新任务
    private void startRefreshTask() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // 子线程执行网络请求
                new Thread(() -> getOnenetData()).start();
                // 循环执行（3秒一次）
                handler.postDelayed(this, 3000);
            }
        }, 1000); // 延迟1秒后开始
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

            // 处理响应
            if (connection.getResponseCode() == HttpsURLConnection.HTTP_OK) {
                InputStream is = connection.getInputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                br.close();
                is.close();
                // 解析并展示数据
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
        // 打印原始获取OneNet平台数据
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

            // 遍历所有属性
            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject item = dataArray.getJSONObject(i);
                String identifier = item.optString("identifier");
                String valueStr = item.optString("value");

                // 添加空值检查
                if (valueStr == null || valueStr.isEmpty()) {
                    Log.w(TAG, "属性 " + identifier + " 的值为空，跳过解析");
                    continue; // 跳过空值
                }
                // 根据属性标识更新对应的值
                switch (identifier) {
                    case "temp":
                        try {
                            temperature = Integer.parseInt(valueStr);
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "解析温度值失败: " + e.getMessage());
                        }
                        break;
                    case "hum":
                        try {
                            humidity = Integer.parseInt(valueStr);
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "解析湿度值失败: " + e.getMessage());
                        }
                        break;
                    case "smoke":
                        try {
                            smoke = Integer.parseInt(valueStr);
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "解析烟雾值失败: " + e.getMessage());
                        }
                        break;
                    case "led":
                        try {
                            ledState = Integer.parseInt(valueStr);
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "解析LED状态值失败: " + e.getMessage());
                        }
                        break;
                    case "kaiguan":
                        try {
                            doorState = Integer.parseInt(valueStr);
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "解析门状态值失败: " + e.getMessage());
                        }
                        break;
                    case "fan":
                        try {
                            fan = Integer.parseInt(valueStr);
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "解析风扇状态值失败: " + e.getMessage());
                        }
                        break;
                    case "rs485":
                        try {
                            rs485 = Integer.parseInt(valueStr);
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "解析RS485状态值失败: " + e.getMessage());
                        }
                        break;
                    default:
                        Log.d(TAG, "忽略未使用的属性: " + identifier);
                        break;
                }
            }

            // 在主线程更新UI
            runOnUiThread(this::updateUI);

        } catch (JSONException e) {
            Log.e(TAG, "解析JSON失败: " + e.getMessage());
            showToast("数据解析失败");
        }
    }

    // 更新UI显示 - 只更新布局中存在的控件
    private void updateUI() {
        // 更新温度值
        tvTemperature.setText(String.valueOf(temperature));
        // 更新湿度值
        tvHumidity.setText(String.valueOf(humidity));
        // 更新烟雾值
        tvSmoke.setText(String.valueOf(smoke));
        // 更新LED状态
        tvLightStatus.setText(ledState == 1 ? "已开启" : "已关闭");
        tvLightStatus.setTextColor(ledState == 1 ? Color.parseColor("#10B981") : Color.parseColor("#1F2937"));
        tvLightStatus.setBackgroundResource(ledState == 1 ? R.drawable.yuanjiao2 : R.drawable.yuanjiao1);
        // 更新门状态
        ivDoorIcon.setText(doorState == 1 ? "已开启" : "已关闭");
        ivDoorIcon.setTextColor(doorState == 1 ? Color.parseColor("#10B981") : Color.parseColor("#1F2937"));
        ivDoorIcon.setBackgroundResource(doorState == 1 ? R.drawable.yuanjiao2 : R.drawable.yuanjiao1);
        // 更新风扇状态
        ivFanIcon.setText(fan == 1 ? "已开启" : "已关闭");
        ivFanIcon.setTextColor(fan == 1 ? Color.parseColor("#10B981") : Color.parseColor("#1F2937"));
        ivFanIcon.setBackgroundResource(fan == 1 ? R.drawable.yuanjiao2 : R.drawable.yuanjiao1);
        // 更新RS485状态
        tvSafetyStatus.setText(rs485 == 1 ? "异常" : "安全");
        tvSafetyStatus.setTextColor(rs485 == 1 ? Color.parseColor("#EF4444") : Color.parseColor("#000000"));
    }

    // 生成认证Token
    private String generateToken() throws UnsupportedEncodingException, NoSuchAlgorithmException, InvalidKeyException {
        String version = "2020-05-29";
        String resourceName = "userid/" + userId;
        String expirationTime = String.valueOf(System.currentTimeMillis() / 1000 + 3600); // 1小时有效期
        String signatureMethod = TokenUtil.SignatureMethod.SHA1.name().toLowerCase();
        return TokenUtil.assembleToken(version, resourceName, expirationTime, signatureMethod, userAccessKey);
    }

    // 显示Toast消息的辅助方法
    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(AttributeDisplayActivity.this, message, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 停止刷新任务
        handler.removeCallbacksAndMessages(null);

        // 断开MQTT连接
        if (mqttAndroidClient != null && mqttAndroidClient.isConnected()) {
            try {
                mqttAndroidClient.disconnect();
                Log.d(TAG, "MQTT已断开连接");
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
    }
}