package com.eric.leclercsolde;

import org.json.JSONException;
import org.json.JSONObject;

public class Card {

    public enum Status { PENDING, CHECKING, DONE, ERROR }

    public final String id;
    public final String number;
    public final String code;
    public final String faceAmount;
    public final String pdfValidity;
    public final String sourceUri;

    public boolean selected = true;
    public Status status = Status.PENDING;
    public String balance;
    public String balanceValidity;
    public String error;

    public Card(String id, String number, String code, String faceAmount, String pdfValidity, String sourceUri) {
        this.id = id;
        this.number = number;
        this.code = code;
        this.faceAmount = faceAmount;
        this.pdfValidity = pdfValidity;
        this.sourceUri = sourceUri;
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
        json.put("sourceUri", sourceUri);
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
                json.isNull("pdfValidity") ? null : json.optString("pdfValidity", null),
                json.isNull("sourceUri") ? null : json.optString("sourceUri", null));
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

    public boolean isEmptyBalance() {
        if (balance == null) {
            return false;
        }
        String normalized = balance.replace(",", ".").replaceAll("[^0-9.]", "");
        try {
            return Double.parseDouble(normalized) == 0d;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
