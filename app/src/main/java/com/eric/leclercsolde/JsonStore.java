package com.eric.leclercsolde;

import android.content.Context;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class JsonStore {

    private final AtomicFile file;

    public JsonStore(Context context, String name) {
        file = new AtomicFile(new File(context.getFilesDir(), name));
    }

    public JSONArray read() throws IOException, JSONException {
        try {
            return new JSONArray(new String(file.readFully(), StandardCharsets.UTF_8));
        } catch (FileNotFoundException ex) {
            return new JSONArray();
        }
    }

    public void write(JSONArray array) throws IOException {
        FileOutputStream out = file.startWrite();
        try {
            out.write(array.toString().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(out);
        } catch (IOException ex) {
            file.failWrite(out);
            throw ex;
        }
    }
}
