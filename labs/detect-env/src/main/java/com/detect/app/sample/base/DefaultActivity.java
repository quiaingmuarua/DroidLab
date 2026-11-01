package com.detect.app.sample.base;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.detect.app.sample.R;


public class DefaultActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.detect_activity_default);
    }
}