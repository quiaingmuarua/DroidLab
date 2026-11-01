package com.app.androidlab.sample;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.androidlab.sample.R;
import com.app.androidlab.sample.base.DefaultActivity;
import com.app.androidlab.sample.base.Item;
import com.app.androidlab.sample.base.ItemAdapter;
import com.app.androidlab.sample.base.Status;
import com.app.androidlab.sample.crypto.CryptoActivity;
import com.app.androidlab.sample.network.NetworkActivity;
import com.app.androidlab.sample.playground.PlaygroundActivity;
import com.app.androidlab.sample.runtime.RuntimeActivity;
import com.app.androidlab.sample.system.SysInfoActivity;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.util.ArrayList;

public class AndroidLabMainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ItemAdapter adapter;
    private ArrayList<Item> itemList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.protect_activity_main);
        HiddenApiBypass.addHiddenApiExemptions("");

        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        itemList = new ArrayList<>();
        itemList.add(new Item("system-info", "device and system fingerprint demos", Status.TODOCheck));
        itemList.add(new Item("network", "common network request demos and certificate handling", Status.TODOCheck));
        itemList.add(new Item("crypto", "common crypto algorithm demos", Status.TODOCheck));
        itemList.add(new Item("runtime", "inline asm and system call related demos", Status.TODOCheck));
        itemList.add(new Item("playground", "lightweight Java and NDK object demos", Status.TODOCheck));

        adapter = new ItemAdapter(itemList);
        adapter.setOnItemClickListener((position) -> {
            String title = itemList.get(position).getTitle();
            Intent intent;
            switch (title) {
                case "system-info":
                    intent = new Intent(AndroidLabMainActivity.this, SysInfoActivity.class);
                    break;
                case "network":
                    intent = new Intent(AndroidLabMainActivity.this, NetworkActivity.class);
                    break;
                case "crypto":
                    intent = new Intent(AndroidLabMainActivity.this, CryptoActivity.class);
                    break;
                case "runtime":
                    intent = new Intent(AndroidLabMainActivity.this, RuntimeActivity.class);
                    break;
                case "playground":
                    intent = new Intent(AndroidLabMainActivity.this, PlaygroundActivity.class);
                    break;
                default:
                    intent = new Intent(AndroidLabMainActivity.this, DefaultActivity.class);
            }
            startActivity(intent);
        });
        recyclerView.setAdapter(adapter);
    }
}
