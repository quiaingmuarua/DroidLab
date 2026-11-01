package com.app.androidlab.sample.base;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

public class BaseProtectActivity extends AppCompatActivity {

   public  void showAlertDialog(Context context,String title, String message) {
      // 创建 EditText
      EditText editText = new EditText(this);
      editText.setText(message);
      editText.setFocusable(false);
      editText.setClickable(false);
      editText.setCursorVisible(false);
      editText.setLongClickable(true);
      editText.setTextIsSelectable(true);
      editText.setBackground(null);
      new AlertDialog.Builder(context)
              .setTitle(title)
              .setView(editText)
              .setPositiveButton("OK", (dialogInterface, i) -> dialogInterface.dismiss())
              .show();
   }


     public  void showAlertDialog(String title,String message) {

        showAlertDialog(this,title,message);
     }
}
