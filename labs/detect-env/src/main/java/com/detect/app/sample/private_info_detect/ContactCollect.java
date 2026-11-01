package com.detect.app.sample.private_info_detect;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContactCollect {
    private static final String TAG = "ContactCollect";

    private static final int REQUEST_CODE = 1;

    public static String getAllInfo(Context context) {
        Map<String, Object> result = allMapInfo(context);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        String resultStr = gson.toJson(result);

        Log.d(TAG, new GsonBuilder().create().toJson(result));

        return resultStr;
    }

    public static Map<String, Object> allMapInfo(Context context) {
        Map<String, Object> result = new HashMap<>();
        try {
            result.put("ContactInfo", getContactInfo(context));
        } catch (Exception e) {
            Log.e(TAG, "allMapInfo: ", e);
        }
        return result;

    }

    public static Map<String, List<Map<String, String>>> getContactInfo(Context context) {
        Map<String, List<Map<String, String>>> contacts = new HashMap<>();
        ContentResolver contentResolver = context.getContentResolver();
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.READ_CONTACTS}, REQUEST_CODE);
        }


        Cursor cursor = contentResolver.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);
        if (cursor != null && cursor.getCount() > 0) {
            while (cursor.moveToNext()) {
                int idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID);
                String id = idIndex != -1 ? cursor.getString(idIndex) : null;

                int nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);
                String name = nameIndex != -1 ? cursor.getString(nameIndex) : null;

                int hasPhoneNumberIndex = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER);
                int hasPhoneNumber = hasPhoneNumberIndex != -1 ? cursor.getInt(hasPhoneNumberIndex) : 0;

                if (hasPhoneNumber > 0) {
                    List<Map<String, String>> phoneNumbers = new ArrayList<>();
                    Cursor phoneCursor = contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            new String[]{id},
                            null);

                    while (phoneCursor.moveToNext()) {
                        int phoneNumberIndex = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                        String phoneNumber = phoneNumberIndex != -1 ? phoneCursor.getString(phoneNumberIndex) : null;
                        Map<String, String> phoneDetail = new HashMap<>();
                        phoneDetail.put("number", phoneNumber);
                        phoneNumbers.add(phoneDetail);
                    }
                    phoneCursor.close();
                    contacts.put(name, phoneNumbers);
                }
            }
            cursor.close();
        }
        return contacts;
    }

}
