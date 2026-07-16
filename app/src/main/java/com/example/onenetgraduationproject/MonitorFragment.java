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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
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
    private Spinner spPropertySelect;
    private Spinner spTimeRange;
    private Button btnQuery;
    private AAChartView aaChartView;
    private TextView tvMinValue;
    private TextView tvAvgValue;
    private TextView tvMaxValue;
    private Handler handler;

    // 属性列表
    private String[] propertyList = {"temp", "hum", "adc2_raw", "adc2_v"};
    private String[] propertyNames = {"温度", "湿度", "红外", "烟雾"};

    // 时间范围选项
    private String[] timeRangeOptions = {"1小时", "6小时", "12小时", "24小时"};
    private long[] timeRangeValues = {3600000, 21600000, 43200000, 86400000}; // 毫秒

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
        spPropertySelect = view.findViewById(R.id.sp_property_select);
        spTimeRange = view.findViewById(R.id.sp_time_range);
        btnQuery = view.findViewById(R.id.btn_query);
        aaChartView = view.findViewById(R.id.AAChartView);
        tvMinValue = view.findViewById(R.id.tv_min_value);
        tvAvgValue = view.findViewById(R.id.tv_avg_value);
        tvMaxValue = view.findViewById(R.id.tv_max_value);
        handler = new Handler(Looper.getMainLooper());

        // 设置属性选择Spinner
        ArrayAdapter<String> propertyAdapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, propertyNames);
        propertyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spPropertySelect.setAdapter(propertyAdapter);

        // 设置时间范围Spinner
        ArrayAdapter<String> timeAdapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, timeRangeOptions);
        timeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spTimeRange.setAdapter(timeAdapter);

        // 设置按钮点击事件
        btnQuery.setOnClickListener(v -> {
            // 获取选中的属性
            int propertyIndex = spPropertySelect.getSelectedItemPosition();
            String identifier = propertyList[propertyIndex];

            // 获取选中的时间范围
            int timeIndex = spTimeRange.getSelectedItemPosition();
            long timeRange = timeRangeValues[timeIndex];

            // 计算时间范围
            long endTime = System.currentTimeMillis();
            long startTime = endTime - timeRange;

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

    private void parseAndShowHistoryData(String json) {
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
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");

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

                // 计算统计数据
                double min = Double.MAX_VALUE;
                double max = Double.MIN_VALUE;
                double sum = 0;
                for (Double value : values) {
                    min = Math.min(min, value);
                    max = Math.max(max, value);
                    sum += value;
                }
                double avg = values.size() > 0 ? sum / values.size() : 0;

                // 获取当前选中的属性名称
                int propertyIndex = spPropertySelect.getSelectedItemPosition();
                String propertyName = propertyNames[propertyIndex];

                // 在主线程更新UI
                double finalMin = min;
                double finalMax = max;
                double finalMax1 = max;
                handler.post(() -> {
                    // 更新统计数据
                    tvMinValue.setText(String.format("%.1f", finalMin));
                    tvAvgValue.setText(String.format("%.1f", avg));
                    tvMaxValue.setText(String.format("%.1f", finalMax1));

                    // 创建图表模型
                    AAChartModel aaChartModel = new AAChartModel()
                            .chartType("line")
                            .title(propertyName + "历史趋势")
                            .subtitle("数据可视化")
                            .backgroundColor("#ffffff")
                            .dataLabelsEnabled(false)
                            .categories(xAxisCategories.toArray(new String[0]))
                            .series(new AASeriesElement[]{
                                    new AASeriesElement()
                                            .name(propertyName)
                                            .data(values.toArray(new Double[0]))
                                            .color("#4A90D9")
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