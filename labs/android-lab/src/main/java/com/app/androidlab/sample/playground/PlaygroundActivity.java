package com.app.androidlab.sample.playground;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;


import com.app.androidlab.sample.R;
import com.app.androidlab.sample.base.Item;
import com.app.androidlab.sample.base.ItemAdapter;
import com.app.androidlab.sample.base.Status;


import java.util.ArrayList;

public class PlaygroundActivity extends AppCompatActivity {
    private static final String TAG = "SysInfoActivity";
    private RecyclerView recyclerView;
    private ItemAdapter adapter;
    private ArrayList<Item> itemList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_android_simple);
        recyclerView = findViewById(R.id.protect_crypto_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        EditText ed = findViewById(R.id.editText);
        String inputStr = ed.getText().toString();

        Log.d(TAG, "onCreate: EditText " + inputStr);
        itemList = new ArrayList<>();
        itemList.add(new Item("java对象", "java对象基础使用", Status.TODOCheck));
        itemList.add(new Item("ndk入门", "ndk注册", Status.TODOCheck));
        itemList.add(new Item("webView", "webView使用", Status.TODOCheck));
        adapter = new ItemAdapter(itemList);

        recyclerView.setAdapter(adapter);
        adapter.setOnItemClickListener((position -> {
            String title = itemList.get(position).getTitle();
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(title);
            switch (title) {
                case "java对象":
                    builder.setMessage(SimpleObjUtils.getJavaObjStr());
                    break;
                case "ndk入门":
                    builder.setMessage(SimpleObjUtils.getNdkObjStr());
                    break;
                default:
                    builder.setMessage("todo");
            }
            builder.setPositiveButton("OK", (dialogInterface, i) -> dialogInterface.dismiss());

            builder.show();


        }));
    }
}

