package com.example.onenetgraduationproject;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import static com.example.onenetgraduationproject.HomeFragment.token;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.github.AAChartModel.AAChartCore.AAChartCreator.AAChartModel;
import com.github.AAChartModel.AAChartCore.AAChartCreator.AAChartView;
import com.github.AAChartModel.AAChartCore.AAChartCreator.AASeriesElement;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

public class MonitorFragment extends Fragment {
    private EditText et_history_identifier;
    private Button btn_history_data, btn_quick_history_data;
    private AAChartView aaChartView;
    private Handler handler;
    private Spinner sp_time_range, sp_quick_time_range, sp_device_property;

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
        et_history_identifier = view.findViewById(R.id.et_history_identifier);
        btn_history_data = view.findViewById(R.id.btn_history_data);
        btn_quick_history_data = view.findViewById(R.id.btn_quick_history_data);
        aaChartView = view.findViewById(R.id.AAChartView);
        sp_time_range = view.findViewById(R.id.sp_time_range);
        sp_quick_time_range = view.findViewById(R.id.sp_quick_time_range);
        sp_device_property = view.findViewById(R.id.sp_device_property);
        handler = new Handler(Looper.getMainLooper());

        // 设置手动查询按钮点击事件
        btn_history_data.setOnClickListener(v -> {
            // 获取用户输入的标识符
            String identifier = et_history_identifier.getText().toString().trim();
            if (identifier.isEmpty()) {
                Toast.makeText(getActivity(), "请输入属性标识符", Toast.LENGTH_SHORT).show();
                return;
            }

            // 获取当前时间作为结束时间
            long endTime = System.currentTimeMillis();
            // 根据Spinner选择的时间范围计算起始时间
            long startTime = calculateStartTime(endTime, sp_time_range);

            // 在子线程执行历史数据请求
            new Thread(() -> getHistoryData(identifier, startTime, endTime)).start();
        });

        // 设置快捷查询按钮点击事件
        btn_quick_history_data.setOnClickListener(v -> {
            // 获取Spinner中选择的属性
            String identifier = sp_device_property.getSelectedItem().toString();

            // 获取当前时间作为结束时间
            long endTime = System.currentTimeMillis();
            // 根据Spinner选择的时间范围计算起始时间
            long startTime = calculateStartTime(endTime, sp_quick_time_range);

            // 在子线程执行历史数据请求
            new Thread(() -> getHistoryData(identifier, startTime, endTime)).start();
        });
    }

    /**
     * 根据Spinner选择的时间范围计算起始时间
     * @param endTime 结束时间（当前时间）
     * @param spinner 时间范围选择器
     * @return 起始时间
     */
    private long calculateStartTime(long endTime, Spinner spinner) {
        int selectedPosition = spinner.getSelectedItemPosition();
        switch (selectedPosition) {
            case 0: // 10分钟
                return endTime - (10 * 60 * 1000);
            case 1: // 30分钟
                return endTime - (30 * 60 * 1000);
            case 2: // 1小时
                return endTime - (1 * 60 * 60 * 1000);
            case 3: // 6小时
                return endTime - (6 * 60 * 60 * 1000);
            case 4: // 12小时
                return endTime - (12 * 60 * 60 * 1000);
            case 5: // 24小时
                return endTime - (24 * 60 * 60 * 1000);
            case 6: // 3天
                return endTime - (3 * 24 * 60 * 60 * 1000);
            case 7: // 7天
                return endTime - (7 * 24 * 60 * 60 * 1000);
            default: // 默认10分钟
                return endTime - (10 * 60 * 1000);
        }
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
                parseAndShowHistoryData(response.toString(), identifier);
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

    private void parseAndShowHistoryData(String json, String identifier) {
        try {
            // 解析JSON响应
            JSONObject jsonObject = new JSONObject(json);
            int code = jsonObject.getInt("code");
            if (code == 0) {
                JSONObject data = jsonObject.getJSONObject("data");
                JSONArray list = data.getJSONArray("list");

                // 准备图表数据
                List<String> xAxisCategories = new ArrayList<>();
                List<Double> values = new ArrayList<>();

                // 格式化时间
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");

                // 遍历数据列表
                for (int i = 0; i < list.length(); i++) {
                    JSONObject item = list.getJSONObject(i);
                    long time = item.getLong("time");
                    double value = Double.parseDouble(item.getString("value"));

                    // 转换时间格式
                    String formattedTime = sdf.format(new Date(time));

                    // 添加到数据列表
                    xAxisCategories.add(formattedTime);
                    values.add(value);
                }

                // 在主线程更新UI
                handler.post(() -> {
                    // 创建图表模型
                    AAChartModel aaChartModel = new AAChartModel()
                            .chartType("line")
                            .title("历史数据趋势图")
                            .subtitle("属性: " + identifier + " 的数据变化")
                            .backgroundColor("#ffffff")
                            .dataLabelsEnabled(true)
                            .categories(xAxisCategories.toArray(new String[0]))
                            .series(new AASeriesElement[]{
                                    new AASeriesElement()
                                            .name(identifier)
                                            .data(values.toArray(new Double[0]))
                            });

                    // 绘制图表
                    aaChartView.aa_drawChartWithChartModel(aaChartModel);
                });
            } else {
                String msg = jsonObject.getString("msg");
                Log.e(TAG, "获取历史数据失败: " + msg);
                handler.post(() -> Toast.makeText(getActivity(), "获取历史数据失败: " + msg, Toast.LENGTH_SHORT).show());
            }
        } catch (Exception e) {
            Log.e(TAG, "解析历史数据异常: " + e.getMessage());
            handler.post(() -> Toast.makeText(getActivity(), "解析历史数据异常", Toast.LENGTH_SHORT).show());
        }
    }
}