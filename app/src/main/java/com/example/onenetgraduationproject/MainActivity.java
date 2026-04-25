package com.example.onenetgraduationproject;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private BottomNavigationView bottomNavigationView;
    // Fragment实例
    private HomeFragment homeFragment;
    private ControlFragment controlFragment;
    private MonitorFragment monitorFragment;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragmen);

        handler = new Handler(Looper.getMainLooper());

        // 初始化组件
        bottomNavigationView = findViewById(R.id.bottom_navigation);

        // 创建Fragment实例
        homeFragment = new HomeFragment();
        controlFragment = new ControlFragment();
        monitorFragment = new MonitorFragment();

        // 设置默认Fragment
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, homeFragment)
                .addToBackStack(null)
                .commit();

        // 设置底部导航栏点击事件
        bottomNavigationView.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;

            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                selectedFragment = homeFragment;
            } else if (itemId == R.id.nav_control) {
                selectedFragment = controlFragment;
            } else if (itemId == R.id.nav_history) {
                selectedFragment = monitorFragment;
            }

            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
                return true;
            }
            return false;
        });

    }
}