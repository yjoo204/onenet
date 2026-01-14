package com.example.onenetgraduationproject;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import static com.example.onenetgraduationproject.HomeFragment.token;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;
import android.widget.EditText;

public class MonitorFragment extends Fragment {
    private View aaChartView;
    private EditText et_history_identifier;
    private Button btn_history_data;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // 全屏显示
        getActivity().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        View view = inflater.inflate(R.layout.fragment_monitor, container, false);
        
        // 初始化UI组件和逻辑
        initViews(view);
        
        return view;
    }

    private void initViews(View view) {
        aaChartView = view.findViewById(R.id.AAChartView);
        et_history_identifier = view.findViewById(R.id.et_history_identifier);
        btn_history_data = view.findViewById(R.id.btn_history_data);
        // 设置按钮点击事件
        btn_history_data.setOnClickListener(v -> {
            // 获取用户输入的标识符
            String identifier = btn_history_data.getText().toString().trim();
            if (identifier.isEmpty()) {
                Toast.makeText(getActivity(), "请输入属性标识符", Toast.LENGTH_SHORT).show();
                return;
            }
            // 获取最近24小时的LED历史数据
            long endTime = System.currentTimeMillis();
            long startTime = endTime - (24 * 60 * 60 * 1000); // 24小时前
            // 在子线程执行历史数据请求
            new Thread(() -> getHistoryData(identifier, startTime, endTime)).start();
        });
    }
    private String getHistoryDataUrl(String identifier, long startTime, long endTime) {
        return "https://iot-api.heclouds.com/thingmodel/query-device-property-history?product_id=" +
                HomeFragment.productId + "&device_name=" + HomeFragment.deviceName +
                "&identifier=" + identifier +
                "&start_time=" + startTime +
                "&end_time=" + endTime;
    }

    // 获取历史数据
    private void getHistoryData(String identifier, long startTime, long endTime) {
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
            }
        } catch (Exception e) {
            Log.e(TAG, "获取历史数据异常: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        // 日志输出
        Log.d(TAG, "历史数据请求URL: " + historyUrl);
        Log.d(TAG, "使用的Token: " + token);
        Log.d(TAG, "历史数据响应: " + response);
    }


    private void parseAndShowHistoryData(String string) {

    }
}