package com.app.androidlab.sample.system;


import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import com.app.androidlab.sample.R;
import com.app.androidlab.sample.base.Item;
import com.app.androidlab.sample.base.ItemAdapter;
import com.app.androidlab.sample.base.Status;


import java.util.ArrayList;

public class SysInfoActivity extends AppCompatActivity {

    private static final String TAG = "SysInfoActivity";
    private RecyclerView recyclerView;
    private ItemAdapter adapter;
    private ArrayList<Item> itemList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sysinfo);
        recyclerView = findViewById(R.id.protect_crypto_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        EditText ed = findViewById(R.id.editText);
        String inputStr = ed.getText().toString();

        Log.d(TAG, "onCreate: EditText " + inputStr);


        itemList = new ArrayList<>();
        itemList.add(new Item("android_os_build", "android.os.Build", Status.TODOCheck));
        itemList.add(new Item("Telephony", "手机卡网络相关", Status.TODOCheck));
        itemList.add(new Item("settingSecure", "android.provider.Settings 设备信息", Status.TODOCheck));
        itemList.add(new Item("processList", "获取进程列表", Status.TODOCheck));
        itemList.add(new Item("SystemProperties", "android.os.SystemProperties", Status.TODOCheck));


        adapter = new ItemAdapter(itemList);

        recyclerView.setAdapter(adapter);

        adapter.setOnItemClickListener((position -> {
            String title = itemList.get(position).getTitle();
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(title);
            switch (title) {
                case "android_os_build":
                    builder.setView(genEditText(JavaSysInfo.getOsBuildInfo()));
                    break;
                case "processList":
                    builder.setView(genEditText(JavaAPPInfo.processList(this)));
                    break;
                case "settingSecure":
                    builder.setView(genEditText(JavaSysInfo.getSettingSecureInfo(this)));
                    break;
                case "Telephony":
                    builder.setView(genEditText(JavaSysInfo.getTelephonyInfo(this)));
                    break;
                case "SystemProperties":
                    builder.setView(genEditText(JavaSysInfo.getSystemProperties()));
                    break;
                default:
                    builder.setMessage("todo");
            }
            builder.setPositiveButton("OK", (dialogInterface, i) -> dialogInterface.dismiss());
            builder.show();
        }));
    }


    private EditText genEditText(String message) {
        // 创建 EditText
        EditText editText = new EditText(this);
        editText.setText(message);
        editText.setFocusable(false);
        editText.setClickable(false);
        editText.setCursorVisible(false);
        editText.setLongClickable(true);
        editText.setTextIsSelectable(true);
        editText.setBackground(null);
        return editText;


    }


}