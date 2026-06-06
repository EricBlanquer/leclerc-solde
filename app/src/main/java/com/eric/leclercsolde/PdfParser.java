package com.eric.leclercsolde;

import android.content.Context;
import android.net.Uri;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PdfParser {

    private static final Pattern NUMBER = Pattern.compile("n\\s*[°o]?\\s*(\\d{13})");
    private static final Pattern CODE =
            Pattern.compile("code de s[eé]curit[eé] internet\\s*:?\\s*(\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern ID =
            Pattern.compile("ID carte cadeau\\s*:?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern AMOUNT = Pattern.compile("(\\d+(?:[.,]\\d{1,2})?)\\s*€");
    private static final Pattern VALIDITY = Pattern.compile("jusqu['’]au\\s*(\\d{2}/\\d{2}/\\d{4})");

    private PdfParser() {
    }

    public static Card parse(Context context, Uri uri) throws Exception {
        PDFBoxResourceLoader.init(context);
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             PDDocument doc = PDDocument.load(in)) {
            String text = new PDFTextStripper().getText(doc);
            String number = find(NUMBER, text);
            String code = find(CODE, text);
            if (number == null || code == null) {
                return null;
            }
            return new Card(find(ID, text), number, code, find(AMOUNT, text), find(VALIDITY, text), uri.toString());
        }
    }

    private static String find(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }
}
