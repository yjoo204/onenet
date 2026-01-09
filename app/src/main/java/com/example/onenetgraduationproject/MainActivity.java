package com.example.onenetgraduationproject;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "OneNetDemo";
    // OneNet平台配置
    private String deviceName = "pi1";
    private String productId = "v79fer6hC4";
    private String userId = "432577";
    private String userAccessKey = "uO/Y5Jr5Tj97TSpVaSvMxDlSqkSAsIw/P46fOxhHuZWoovs39BQIL98mAtEbWmml";

    // 网络请求地址
    private String queryUrl = "https://iot-api.heclouds.com/thingmodel/query-device-property?product_id=" + productId + "&device_name=" + deviceName;

    //    product_id	string	是	产品ID，平台生成唯一ID
//    device_name	string	是	设备名称
//    identifier	string	是	属性功能点标识
//    start_time	string	是	查询起始时间，毫秒时间戳
//    end_time	string	是	查询结束时间，毫秒时间戳
    private String queryUrlhistoryUrl = "https://iot-api.heclouds.com/thingmodel/query-device-property-history?product_id=" + productId + "&device_name=" + deviceName+"&identifier=someDate"+"&start_time=1681713647000"+"&end_time=1681799966562";
    private String setDevicePropertyUrl = "https://iot-api.heclouds.com/thingmodel/set-device-property";

    // MQTT配置
    private String mqttServerUri = "tcp://mqtts.heclouds.com:1883";
    private String clientId = "pi1";
    private String mqttUsername = "v79fer6hC4";
    private String mqttPassword = "version=2018-10-31&res=products%2Fv79fer6hC4&et=1806681600&method=md5&sign=o2KPjpSQeqL8Pb7TiC03Dw%3D%3D";
    private String subscribeTopic = "$sys/v79fer6hC4/pi1/thing/property/post/reply";
    private String publishTopic = "$sys/v79fer6hC4/pi1/thing/property/post";

    private MqttAndroidClient mqttAndroidClient;

    // UI组件
    private String token;
    private Handler handler = new Handler();
    private ScrollView scrollView;
    private LinearLayout contentLayout;
    private EditText propertyKeyEt;
    private EditText propertyValueEt;
    private EditText historyIdentifierEt; // 新增：历史数据标识符输入框
    private Button sendPropertyBtn,historyDataBtn;
    private ImageButton Return;
    private BottomNavigationView bottomNavigationView;
    // 用于缓存属性视图，实现更新功能
    private Map<String, TextView> propertyViews = new HashMap<>();

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // 全屏显示
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        // 初始化UI组件
        scrollView = findViewById(R.id.scroll_view);
        contentLayout = findViewById(R.id.content_layout);
        propertyKeyEt = findViewById(R.id.et_property_key);
        propertyValueEt = findViewById(R.id.et_property_value);
        sendPropertyBtn = findViewById(R.id.btn_send_property);
        historyDataBtn = findViewById(R.id.btn_history_data); // 初始化历史数据按钮
        historyIdentifierEt = findViewById(R.id.et_history_identifier); // 初始化标识符输入框
        Return = findViewById(R.id.Return);

        bottomNavigationView = findViewById(R.id.bottom_navigation);
        setupBottomNavigation();

        Return.setOnClickListener(v -> {
            finish();
        });

        // 设置发送属性按钮监听
        sendPropertyBtn.setOnClickListener(v -> sendCustomProperty());
        // 设置历史数据按钮监听
        historyDataBtn.setOnClickListener(v -> {
            // 获取用户输入的标识符
            String identifier = historyIdentifierEt.getText().toString().trim();
            if (identifier.isEmpty()) {
                Toast.makeText(this, "请输入属性标识符", Toast.LENGTH_SHORT).show();
                return;
            }

            // 获取最近24小时的LED历史数据
            long endTime = System.currentTimeMillis();
            long startTime = endTime - (24 * 60 * 60 * 1000); // 24小时前
            // 在子线程执行历史数据请求
            new Thread(() -> getHistoryData(identifier, startTime, endTime)).start();
        });

        // 生成Token
        try {
            token = generateToken();
        } catch (Exception e) {
            Log.e(TAG, "生成Token失败: " + e.getMessage());
            Toast.makeText(this, "认证失败", Toast.LENGTH_SHORT).show();
            return;
        }

        // 解决Android 9以上网络请求在主线程的问题
        if (android.os.Build.VERSION.SDK_INT > 9) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

        // 初始化MQTT并连接
        initMqtt();

        // 启动定时刷新任务（每3秒刷新一次）
        startRefreshTask();
    }

    private void setupBottomNavigation() {
        bottomNavigationView.setOnNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                // 跳转到首页
                Intent homeIntent = new Intent(MainActivity.this, AttributeDisplayActivity.class);
                startActivity(homeIntent);
                finish(); // 关闭当前Activity
                return true;
            } else if (itemId == R.id.nav_control) {
                // 当前已经是控制界面，不需要跳转
                showToast("当前页面");
                return true;
            } else if (itemId == R.id.nav_history) {
                // 当前已经是历史数据界面，不需要跳转
                showToast("当前页面");
                return true;
            }
            return false;
        });

        // 设置当前选中的菜单项为"控制"或"历史"
        bottomNavigationView.setSelectedItemId(R.id.nav_control);
    }

    // 显示Toast消息
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }


    // 发送自定义属性
    private void sendCustomProperty() {
        String key = propertyKeyEt.getText().toString().trim();
        String valueStr = propertyValueEt.getText().toString().trim();

        if (key.isEmpty() || valueStr.isEmpty()) {
            Toast.makeText(this, "属性名和属性值不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // 尝试将值转换为数字，如果不能转换则使用字符串
            Object value;
            try {
                value = Integer.parseInt(valueStr);
            } catch (NumberFormatException e) {
                value = valueStr; // 保持为字符串
            }

            // 使用MQTT发送属性
            publishMqttMessage(key, value);
            propertyValueEt.setText("");
        } catch (Exception e) {
            Log.e(TAG, "发送属性失败: " + e.getMessage());
            Toast.makeText(this, "发送失败", Toast.LENGTH_SHORT).show();
        }
    }
    // 初始化MQTT
    private void initMqtt() {
        mqttAndroidClient = new MqttAndroidClient(getApplicationContext(), mqttServerUri, clientId);

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

    // 解析MQTT回复消息
    private void parseMqttReply(String message) {
        try {
            JSONObject json = new JSONObject(message);
            String code = json.optString("code");

            if ("0".equals(code)) {
                runOnUiThread(() -> Toast.makeText(this, "属性更新成功", Toast.LENGTH_SHORT).show());
            } else {
                String errorMsg = json.optString("msg", "更新失败");
                runOnUiThread(() -> Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show());
            }
        } catch (JSONException e) {
            Log.e(TAG, "解析MQTT回复失败: " + e.getMessage());
        }
    }

    // 连接到MQTT服务器
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

                    subscribeToTopic();

                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG, "MQTT连接失败: " + exception.getMessage());
                    runOnUiThread(() -> {
                        addMessageToView("MQTT连接失败: " + exception.getMessage());
                        // 5秒后重试连接
                        handler.postDelayed(() -> connectToMqtt(), 5000);
                    });
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    // 订阅主题
    private void subscribeToTopic() {
        try {
            mqttAndroidClient.subscribe(subscribeTopic, 1, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(TAG, "订阅主题成功: " + subscribeTopic);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    String errorMessage = "订阅主题失败";
                    if (exception != null && exception.getMessage() != null) {
                        errorMessage += ": " + exception.getMessage();
                    }
                    Log.e(TAG, errorMessage);
                    addMessageToView(errorMessage);
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    // 发布MQTT消息 - 通用方法，可发送任何属性
    private void publishMqttMessage(String propertyKey, Object value) {
        // 获取共享的MQTT客户端
        MqttAndroidClient mqttAndroidClient = AttributeDisplayActivity.getSharedMqttClient();

        if (mqttAndroidClient == null || !mqttAndroidClient.isConnected()) {
            Log.e(TAG, "MQTT未连接，无法发布消息");
            runOnUiThread(() -> Toast.makeText(this, "MQTT未连接，请先启动主界面", Toast.LENGTH_SHORT).show());
            return;
        }

        try {
            // 构建发布内容
            JSONObject jsonObject = new JSONObject();
            //id不能修改
            jsonObject.put("id","123");
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
            mqttAndroidClient.publish("$sys/v79fer6hC4/pi1/thing/property/post", mqttMessage, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(TAG, "消息发布成功: " + message);
                    runOnUiThread(() -> Toast.makeText(MainActivity.this,
                            "已发送: " + propertyKey + " = " + value, Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    String errorMessage = "消息发布失败";
                    if (exception != null && exception.getMessage() != null) {
                        errorMessage += ": " + exception.getMessage();
                    }
                    Log.e(TAG, errorMessage);
                    String finalErrorMessage = errorMessage;
                    runOnUiThread(() -> Toast.makeText(MainActivity.this,
                            finalErrorMessage, Toast.LENGTH_SHORT).show());
                }
            });
        } catch (JSONException | MqttException e) {
            Log.e(TAG, "发布消息异常: " + e.getMessage());
            runOnUiThread(() -> Toast.makeText(this, "发送异常: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }


    // 向界面添加消息
    private void addMessageToView(String message) {
        TextView textView = new TextView(this);
        textView.setTextSize(14);
        textView.setPadding(0, 8, 0, 8);
        textView.setTextColor(getResources().getColor(R.color.design_default_color_secondary));
        textView.setText(message);

        // 保留最近10条消息
        if (contentLayout.getChildCount() > 10) {
            contentLayout.removeViewAt(0);
        }

        contentLayout.addView(textView);
        // 滚动到底部
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    // 生成认证Token
    private String generateToken() throws UnsupportedEncodingException, NoSuchAlgorithmException, InvalidKeyException {
        String version = "2020-05-29";
        String resourceName = "userid/" + userId;
        String expirationTime = String.valueOf(System.currentTimeMillis() / 1000 + 3600); // 1小时有效期
        String signatureMethod = TokenUtil.SignatureMethod.SHA1.name().toLowerCase();
        return TokenUtil.assembleToken(version, resourceName, expirationTime, signatureMethod, userAccessKey);
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
            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStream is = connection.getInputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                br.close();
                is.close();

                // 解析并展示所有数据
                parseAndShowAllData(response.toString());
            } else {
                Log.e(TAG, "请求失败，响应码: " + connection.getResponseCode());
                runOnUiThread(() -> Toast.makeText(this, "数据获取失败", Toast.LENGTH_SHORT).show());
            }
        } catch (Exception e) {
            Log.e(TAG, "获取数据异常: " + e.getMessage());
            runOnUiThread(() -> Toast.makeText(this, "网络异常", Toast.LENGTH_SHORT).show());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        // 日志输出
        Log.d(TAG, "请求URL: " + queryUrl);
        Log.d(TAG, "使用的Token: " + token);
        Log.d(TAG, "服务器响应: " + response.toString());
    }

    // 解析JSON并展示所有属性 - 改为更新模式
    private void parseAndShowAllData(String json) {
        JSONArray dataArray = null;
        try {
            JSONObject jsonObject = new JSONObject(json);
            dataArray = jsonObject.optJSONArray("data");
            if (dataArray == null || dataArray.length() == 0) {
                runOnUiThread(() -> showEmptyState());
                return;
            }

            // 在主线程更新UI
            JSONArray finalDataArray = dataArray;
            runOnUiThread(() -> {
                // 遍历所有属性并更新或添加到布局
                for (int i = 0; i < finalDataArray.length(); i++) {
                    try {
                        JSONObject item = finalDataArray.getJSONObject(i);
                        String identifier = item.optString("identifier", "未知标识");
                        Object value = item.opt("value");
                        String valueStr = (value != null) ? value.toString() : "无数据";

                        // 检查是否已存在该属性的视图
                        if (propertyViews.containsKey(identifier)) {
                            // 存在则更新值
                            TextView textView = propertyViews.get(identifier);
                            textView.setText(String.format("%s：%s", identifier, valueStr));
                        } else {
                            // 不存在则创建新视图
                            TextView textView = new TextView(this);
                            textView.setTextSize(16);
                            textView.setPadding(0, 12, 0, 12);
                            textView.setTextColor(getResources().getColor(R.color.black));
                            textView.setText(String.format("%s：%s", identifier, valueStr));

                            // 添加到布局和缓存
                            contentLayout.addView(textView);
                            propertyViews.put(identifier, textView);
                        }

                        // 如果属性名输入框有值，且与当前属性匹配，则同步进度条
//                         if (!propertyKeyEt.getText().toString().isEmpty() &&
//                                identifier.equals(propertyKeyEt.getText().toString())) {
//                            try {
//                                int propValue = Integer.parseInt(valueStr);
//                                valueSeekBar.setProgress(propValue);
//                                seekBarValueTextView.setText("当前值: " + propValue);
//                            } catch (NumberFormatException e) {
//                                Log.e(TAG, "解析" + identifier + "值失败: " + e.getMessage());
//                            }
//                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "解析单个属性失败: " + e.getMessage());
                    }
                }
                // 滚动到底部
                scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
            });
        } catch (JSONException e) {
            Log.e(TAG, "解析JSON失败: " + e.getMessage());
            runOnUiThread(() -> Toast.makeText(this, "数据解析失败", Toast.LENGTH_SHORT).show());
        }
    }

    // 当没有数据时显示
    private void showEmptyState() {
        contentLayout.removeAllViews();
        propertyViews.clear(); // 清空缓存
        TextView emptyTv = new TextView(this);
        emptyTv.setTextSize(16);
        emptyTv.setTextColor(getResources().getColor(R.color.gray));
        emptyTv.setText("暂无设备数据");
        contentLayout.addView(emptyTv);
    }
    // 设置设备属性URL
    //POST http(s)://iot-api.heclouds.com/thingmodel/set-device-property
    //Content-type: application/json
    //{
    //    "product_id": "9MaNe52pNO",
    //    "device_name": "no001",
    //    "params": {
    //        "switch": true,           // bool
    //        "text": "hello",          // string
    //        "humidity": 12 ,          // int32
    //        "number": 1564448722123,        // int64
    //        "temperature": 30.2             // float
    //        "lng": 3.1234567890123456789,   // double
    //        "type": 1,                      // enum
    //        "error": 256,                   // bitmap
    //        "event":  {                     // struct
    //            "a": 1,
    //            "b": true
    //        }
    //    }
    //}
    // 使用HTTP API设置设备属性
    private void setDeviceProperty(String propertyKey, Object propertyValue) {
        // 确保网络请求在子线程中执行
        new Thread(() -> {
            HttpsURLConnection connection = null;
            try {
                // 创建URL对象
                URL url = new URL(setDevicePropertyUrl);
                connection = (HttpsURLConnection) url.openConnection();

                // 设置请求方法为POST
                connection.setRequestMethod("POST");
                // 设置连接超时时间
                connection.setConnectTimeout(5000);
                // 设置读取超时时间
                connection.setReadTimeout(5000);
                // 设置请求头
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", token);
                // 允许输出数据
                connection.setDoOutput(true);

                // 构建JSON请求体
                JSONObject requestBody = new JSONObject();
                requestBody.put("product_id", productId);
                requestBody.put("device_name", deviceName);

                JSONObject params = new JSONObject();
                JSONObject property = new JSONObject();
                property.put("value", propertyValue);
                params.put(propertyKey, property);

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
                    runOnUiThread(() -> {
                        Toast.makeText(this, "设置设备属性成功", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "设置设备属性成功: " + propertyKey + " = " +propertyValue);
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
                    runOnUiThread(() -> {
                        Toast.makeText(this, "设置设备属性失败，错误码: " + responseCode, Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (IOException | JSONException e) {
                Log.e(TAG, "设置设备属性异常: " + e.getMessage());
                runOnUiThread(() -> {
                    Toast.makeText(this, "设置设备属性异常: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            } finally {
                // 断开连接
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }


    // 获取历史数据URL
    private String getHistoryDataUrl(String identifier, long startTime, long endTime) {
        return "https://iot-api.heclouds.com/thingmodel/query-device-property-history?product_id=" +
                productId + "&device_name=" + deviceName +
                "&identifier=" + identifier +
                "&start_time=" + startTime +
                "&end_time=" + endTime;
    }

    // 获取历史数据
    private void getHistoryData(String identifier, long startTime, long endTime) {
        // api设置设备属性
        setDeviceProperty("temp",1.1);
        String historyUrl = getHistoryDataUrl(identifier, startTime, endTime);
        HttpsURLConnection connection = null;
        StringBuilder response = new StringBuilder();
        try {
            URL url = new URL(historyUrl);
            connection = (HttpsURLConnection) url.openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("authorization", token);

            // 处理响应
            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStream is = connection.getInputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                br.close();
                is.close();

                // 解析并展示历史数据
                parseAndShowHistoryData(response.toString());
            } else {
                Log.e(TAG, "历史数据请求失败，响应码: " + connection.getResponseCode());
                runOnUiThread(() -> Toast.makeText(this, "历史数据获取失败", Toast.LENGTH_SHORT).show());
            }
        } catch (Exception e) {
            Log.e(TAG, "获取历史数据异常: " + e.getMessage());
            runOnUiThread(() -> Toast.makeText(this, "历史数据网络异常", Toast.LENGTH_SHORT).show());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        // 日志输出
        Log.d(TAG, "历史数据请求URL: " + historyUrl);
        Log.d(TAG, "使用的Token: " + token);
        Log.d(TAG, "历史数据响应: " + response.toString());
    }

    // 解析并展示历史数据
    private void parseAndShowHistoryData(String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);
            int code = jsonObject.optInt("code", -1);

            if (code != 0) {
                String errorMsg = jsonObject.optString("msg", "请求失败");
                runOnUiThread(() -> Toast.makeText(this, "历史数据获取失败: " + errorMsg, Toast.LENGTH_SHORT).show());
                return;
            }

            JSONObject dataObj = jsonObject.optJSONObject("data");
            JSONArray dataArray = dataObj != null ? dataObj.optJSONArray("list") : null;

            if (dataArray == null || dataArray.length() == 0) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "暂无历史数据", Toast.LENGTH_SHORT).show();
                });
                return;
            }


        } catch (JSONException e) {
            Log.e(TAG, "解析历史数据失败: " + e.getMessage());
            runOnUiThread(() -> Toast.makeText(this, "数据解析失败", Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    public void onBackPressed() {
        // 返回时设置结果，通知属性显示界面刷新数据
        setResult(RESULT_OK);
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
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