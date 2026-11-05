package com.eric.leclercsolde;

import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;

public class Card {

    public enum Status { PENDING, CHECKING, DONE, ERROR }

    public static final int AMOUNT_DECIMALS = 2;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    public static final Comparator<Card> BY_EXPIRY =
            Comparator.comparing(Card::expiry, Comparator.nullsLast(Comparator.naturalOrder()));

    public final String id;
    public final String number;
    public final String code;
    public final String faceAmount;
    public final String pdfValidity;

    public boolean selected = true;
    public Status status = Status.PENDING;
    public String balance;
    public String balanceValidity;
    public String error;

    public Card(String id, String number, String code, String faceAmount, String pdfValidity) {
        this.id = id;
        this.number = number;
        this.code = code;
        this.faceAmount = faceAmount;
        this.pdfValidity = pdfValidity;
    }

    public Card copyDetails() {
        return new Card(id, number, code, faceAmount, pdfValidity);
    }

    public String formattedNumber() {
        if (number == null || number.length() != 13) {
            return number;
        }
        return number.substring(0, 3) + " " + number.substring(3, 5) + " " + number.substring(5, 8)
                + " " + number.substring(8, 10) + " " + number.substring(10, 13);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("number", number);
        json.put("code", code);
        json.put("faceAmount", faceAmount);
        json.put("pdfValidity", pdfValidity);
        json.put("selected", selected);
        json.put("status", status.name());
        json.put("balance", balance);
        json.put("balanceValidity", balanceValidity);
        json.put("error", error);
        return json;
    }

    public static Card fromJson(JSONObject json) {
        Card card = new Card(
                json.isNull("id") ? null : json.optString("id", null),
                json.optString("number", null),
                json.optString("code", null),
                json.isNull("faceAmount") ? null : json.optString("faceAmount", null),
                json.isNull("pdfValidity") ? null : json.optString("pdfValidity", null));
        card.selected = json.optBoolean("selected", true);
        try {
            card.status = Status.valueOf(json.optString("status", Status.PENDING.name()));
        } catch (IllegalArgumentException ex) {
            card.status = Status.PENDING;
        }
        card.balance = json.isNull("balance") ? null : json.optString("balance", null);
        card.balanceValidity = json.isNull("balanceValidity") ? null : json.optString("balanceValidity", null);
        card.error = json.isNull("error") ? null : json.optString("error", null);
        return card;
    }

    public LocalDate expiry() {
        LocalDate balanceExpiry = parseDate(balanceValidity);
        return balanceExpiry != null ? balanceExpiry : parseDate(pdfValidity);
    }

    public boolean isExpired(LocalDate today) {
        LocalDate expiry = expiry();
        return expiry != null && expiry.isBefore(today);
    }

    private static LocalDate parseDate(String text) {
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text, DATE_FORMAT);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    public boolean isEmptyBalance() {
        BigDecimal amount = parseAmount(balance);
        return amount != null && amount.signum() == 0;
    }

    public static BigDecimal parseAmount(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.replace(',', '.').replaceAll("[^0-9.]", "");
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static String formatAmount(BigDecimal amount) {
        return amount.setScale(AMOUNT_DECIMALS, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }
}
