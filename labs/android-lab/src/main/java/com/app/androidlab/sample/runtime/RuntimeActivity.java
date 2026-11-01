package com.app.androidlab.sample.runtime;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;

import com.app.androidlab.sample.R;
import com.app.androidlab.sample.base.BaseProtectActivity;
import com.app.androidlab.sample.base.Item;
import com.app.androidlab.sample.base.ItemAdapter;
import com.app.androidlab.sample.base.Status;

import java.util.ArrayList;

public class RuntimeActivity extends BaseProtectActivity {
    private static final String TAG = "RuntimeActivity";
    private RecyclerView recyclerView;
    private ItemAdapter adapter;
    private ArrayList<Item> itemList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fundamental);
        recyclerView = findViewById(R.id.protect_crypto_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        EditText ed = findViewById(R.id.editText);
        String inputStr = ed.getText().toString();

        Log.d(TAG, "onCreate: EditText " + inputStr);

        itemList = new ArrayList<>();
        itemList.add(new Item("sysCall", "system call checks", Status.TODOCheck));
        itemList.add(new Item("sysLibc", "system libc checks", Status.TODOCheck));
        itemList.add(new Item("binder", "binder calls", Status.TODOCheck));
        itemList.add(new Item("elfLoader", "dynamic linker and ELF loading", Status.TODOCheck));
        adapter = new ItemAdapter(itemList);

        recyclerView.setAdapter(adapter);

        adapter.setOnItemClickListener((position -> {
            String title = itemList.get(position).getTitle();
            switch (title) {
                case "sysCall":
                    showAlertDialog(title, SysCallUtils.fileCheckStr());
                    break;
                case "sysLibc":
                    showAlertDialog(title, SysLibUtils.fileCheckStr());
                    break;
                case "elfLoader":
                    showAlertDialog(title, SelfLoadUtils.stringFromJNICpp());
                    break;
                default:
                    showAlertDialog(title, "todo");
            }
        }));
    }
}

