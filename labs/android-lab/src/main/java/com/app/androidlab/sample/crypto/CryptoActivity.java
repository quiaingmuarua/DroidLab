package com.app.androidlab.sample.crypto;

import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import com.app.androidlab.sample.R;
import com.app.androidlab.sample.base.BaseProtectActivity;
import com.app.androidlab.sample.base.Item;
import com.app.androidlab.sample.base.ItemAdapter;
import com.app.androidlab.sample.base.Status;

import java.util.ArrayList;

public class CryptoActivity extends BaseProtectActivity {
    private static final String TAG = "CryptoActivity";
    private ArrayList<Item> itemList;
    private ItemAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crypto);

        RecyclerView recyclerView = findViewById(R.id.protect_crypto_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        EditText ed = findViewById(R.id.editText);
        String inputStr = ed.getText().toString();
        Log.d(TAG, "onCreate: EditText " + inputStr);

        itemList = new ArrayList<>();
        itemList.add(new Item("java_cryptos", Status.Execute, () -> JavaCryptoUtils.getAllInfo(inputStr)));
        itemList.add(new Item("ndk_md5", Status.Execute, () -> NdkCryptoUtils.md5FromNative(inputStr)));
        adapter = new ItemAdapter(itemList);
        adapter.setOnItemClickListener((position) -> updateItem(ed, position));
        recyclerView.setAdapter(adapter);
    }

    private void updateItem(EditText ed, int position) {
        String result = "input=" + ed.getText() + "\n"+ "encrypt_result=" + itemList.get(position).getResultSupplier().get();

        showAlertDialog(itemList.get(position).getTitle(), result);
    }


}