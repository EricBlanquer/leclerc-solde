package com.eric.leclercsolde;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PdfLibrary {

    private static final String INDEX_FILE = "pdf_index.json";
    private static final String PDF_MIME_TYPE = "application/pdf";
    private static final String KEY_URI = "uri";
    private static final String KEY_SIZE = "size";
    private static final String KEY_MODIFIED = "modified";
    private static final String KEY_CARD = "card";
    private static final String[] PROJECTION = {
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED};
    private static final String[] ID_PROJECTION = {MediaStore.Files.FileColumns._ID};
    private static final String SELECTION = MediaStore.Files.FileColumns.MIME_TYPE + "=?";
    private static final String[] SELECTION_ARGS = {PDF_MIME_TYPE};

    private static final class Entry {
        final String uri;
        final long size;
        final long modified;
        final Card card;

        Entry(String uri, long size, long modified, Card card) {
            this.uri = uri;
            this.size = size;
            this.modified = modified;
            this.card = card;
        }

        boolean isCard(String number) {
            return card != null && number.equals(card.number);
        }

        JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put(KEY_URI, uri);
            json.put(KEY_SIZE, size);
            json.put(KEY_MODIFIED, modified);
            if (card != null) {
                json.put(KEY_CARD, card.toJson());
            }
            return json;
        }

        static Entry fromJson(JSONObject json) throws JSONException {
            JSONObject card = json.optJSONObject(KEY_CARD);
            return new Entry(json.getString(KEY_URI), json.getLong(KEY_SIZE), json.getLong(KEY_MODIFIED),
                    card == null ? null : Card.fromJson(card));
        }
    }

    private final Context context;
    private final JsonStore store;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private boolean loaded = false;

    public PdfLibrary(Context context) {
        this.context = context.getApplicationContext();
        store = new JsonStore(this.context, INDEX_FILE);
    }

    public List<Card> scan() throws IOException {
        load();
        Uri collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL);
        Map<String, Entry> found = new LinkedHashMap<>();
        try (Cursor cursor = context.getContentResolver().query(collection, PROJECTION, SELECTION, SELECTION_ARGS,
                MediaStore.Files.FileColumns.DATE_ADDED)) {
            if (cursor == null) {
                throw new IOException("MediaStore query returned no cursor");
            }
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID);
            int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE);
            int modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED);
            while (cursor.moveToNext()) {
                Uri uri = ContentUris.withAppendedId(collection, cursor.getLong(idColumn));
                long size = cursor.getLong(sizeColumn);
                long modified = cursor.getLong(modifiedColumn);
                Entry known = entries.get(uri.toString());
                Entry entry = known != null && known.size == size && known.modified == modified
                        ? known
                        : read(uri, size, modified);
                if (entry != null) {
                    found.put(entry.uri, entry);
                }
            }
        }
        entries.clear();
        entries.putAll(found);
        save();
        return cards();
    }

    public Set<String> delete(Set<String> numbers) throws IOException {
        scan();
        ContentResolver resolver = context.getContentResolver();
        Set<String> failed = new HashSet<>();
        Iterator<Entry> iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (entry.card == null || !numbers.contains(entry.card.number)) {
                continue;
            }
            if (delete(resolver, Uri.parse(entry.uri))) {
                iterator.remove();
            } else {
                failed.add(entry.card.number);
            }
        }
        save();
        Set<String> deleted = new HashSet<>(numbers);
        deleted.removeAll(failed);
        return deleted;
    }

    public Uri find(String number) {
        load();
        for (Entry entry : entries.values()) {
            if (entry.isCard(number)) {
                return Uri.parse(entry.uri);
            }
        }
        return null;
    }

    private Entry read(Uri uri, long size, long modified) {
        try {
            return new Entry(uri.toString(), size, modified, PdfParser.parse(context, uri));
        } catch (FileNotFoundException ex) {
            return null;
        } catch (SecurityException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            return new Entry(uri.toString(), size, modified, null);
        }
    }

    private boolean delete(ContentResolver resolver, Uri uri) {
        try {
            return resolver.delete(uri, null, null) > 0 || !exists(resolver, uri);
        } catch (SecurityException ex) {
            return false;
        }
    }

    private boolean exists(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, ID_PROJECTION, null, null, null)) {
            return cursor == null || cursor.getCount() > 0;
        }
    }

    private List<Card> cards() {
        Map<String, Card> byNumber = new LinkedHashMap<>();
        for (Entry entry : entries.values()) {
            if (entry.card != null) {
                byNumber.putIfAbsent(entry.card.number, entry.card.copyDetails());
            }
        }
        return new ArrayList<>(byNumber.values());
    }

    private void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            JSONArray array = store.read();
            for (int i = 0; i < array.length(); i++) {
                Entry entry = Entry.fromJson(array.getJSONObject(i));
                entries.put(entry.uri, entry);
            }
        } catch (IOException | JSONException ex) {
            entries.clear();
        }
    }

    private void save() throws IOException {
        JSONArray array = new JSONArray();
        try {
            for (Entry entry : entries.values()) {
                array.put(entry.toJson());
            }
        } catch (JSONException ex) {
            throw new IOException(ex);
        }
        store.write(array);
    }
}
