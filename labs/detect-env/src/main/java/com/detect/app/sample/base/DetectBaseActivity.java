package com.detect.app.sample.base;



import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.detect.app.sample.tool.JsonCompare;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DetectBaseActivity extends AppCompatActivity {
    public RecyclerView recyclerView;
    public ItemAdapter adapter;
    public  ArrayList<DetectItem> detectItemList=  new ArrayList<>();;
    private static final String TAG = "DetectBaseActivity";




    public void saveToPreferences(Context context, String key, String value) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(key, value);
        editor.apply();
    }


    public String readFromPreferences(Context context, String key, String defaultValue) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        return sharedPreferences.getString(key, defaultValue);
    }


    /**
     * 比较两个字符串，并返回不同的行。
     *
     * @param str1 第一个字符串
     * @param str2 第二个字符串
     * @return 包含所有不同行的列表
     */
    public static List<String> compareStringsByLine(String str1, String str2) {
        String[] lines1 = str1.split("\n");
        String[] lines2 = str2.split("\n");

        List<String> differences = new ArrayList<>();
        int maxLines = Math.max(lines1.length, lines2.length);

        for (int i = 0; i < maxLines; i++) {
            String line1 = i < lines1.length ? lines1[i] : "";
            String line2 = i < lines2.length ? lines2[i] : "";

            if (!line1.equals(line2)) {
                differences.add("不同行 " + (i + 1) + ":\n" + line1 + "\n != \n" + line2);
            }
        }

        return differences;
    }




    public void  showResult(String title,String result){

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setPositiveButton("OK", (dialogInterface, i) -> dialogInterface.dismiss());

        String differString = "";
        String cacheResult = readFromPreferences(this, title, "");

        if (!cacheResult.isEmpty() && !result.isEmpty()) {
            Map<String, Object[]> differencesObj = JsonCompare.compareDifferences(result, cacheResult);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            differString=gson.toJson(differencesObj);
            Log.d(TAG, "onCreate: " + differString);
        }

        builder .setView(wrappedText(result));
        builder.setPositiveButton("确定", null);

        String finalDifferString = differString;
        builder.setNegativeButton("不同的信息", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                showDialog(finalDifferString);
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();

        saveToPreferences(this, title, result);
    }


    private void showDialog(String message) {
        new AlertDialog.Builder(this)
                .setView(wrappedText(message))
                .setPositiveButton("确定", null)
                .show();
    }


    private   EditText wrappedText( String message) {
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



    public void  setupResultAdapter() {
        adapter = new ItemAdapter(detectItemList);
        adapter.setOnItemClickListener(position -> {
            DetectItem item = detectItemList.get(position);
            if (item.getIntentSupplier() != null) {
                startActivity(item.getIntentSupplier().get());
                return;
            }
            String result = item.getResultSupplier().get(); // Use the result supplier to get the result
            showResult(item.getTitle(), result);
        });
        recyclerView.setAdapter(adapter);
    }

    public void setupIntentAdapter() {
        adapter = new ItemAdapter(detectItemList);
        adapter.setOnItemClickListener(position -> {
            //
            Intent intent = detectItemList.get(position).getIntentSupplier().get();
            startActivity(intent);
        });
        recyclerView.setAdapter(adapter);
    }
}
