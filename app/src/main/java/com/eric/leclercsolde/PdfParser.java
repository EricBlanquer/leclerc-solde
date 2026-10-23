package com.eric.leclercsolde;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PdfParser {

    private static final int LAST_PAGE = 1;
    private static final long MAX_MAIN_MEMORY_BYTES = 16L * 1024 * 1024;
    private static final String READ_MODE = "r";
    private static final Pattern NUMBER = Pattern.compile("n\\s*[°o]?\\s*(\\d{13})");
    private static final Pattern CODE =
            Pattern.compile("code de s[eé]curit[eé] internet\\s*:?\\s*(\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern ID =
            Pattern.compile("ID carte cadeau\\s*:?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern AMOUNT = Pattern.compile("(\\d+(?:[.,]\\d{1,2})?)\\s*€");
    private static final Pattern VALIDITY = Pattern.compile("jusqu['’]au\\s*(\\d{2}/\\d{2}/\\d{4})");

    private PdfParser() {
    }

    public static Card parse(Context context, Uri uri) throws IOException {
        PDFBoxResourceLoader.init(context);
        try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(openReadOnly(context, uri));
             PDDocument doc = PDDocument.load(in, MemoryUsageSetting.setupMixed(MAX_MAIN_MEMORY_BYTES))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setEndPage(LAST_PAGE);
            String text = stripper.getText(doc);
            String number = find(NUMBER, text);
            String code = find(CODE, text);
            if (number == null || code == null) {
                return null;
            }
            return new Card(find(ID, text), number, code, find(AMOUNT, text), find(VALIDITY, text));
        }
    }

    public static ParcelFileDescriptor openReadOnly(Context context, Uri uri) throws FileNotFoundException {
        ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, READ_MODE);
        if (pfd == null) {
            throw new FileNotFoundException(uri.toString());
        }
        return pfd;
    }

    private static String find(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }
}
