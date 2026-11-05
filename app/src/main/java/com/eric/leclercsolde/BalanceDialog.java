package com.eric.leclercsolde;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputLayout;

import java.math.BigDecimal;

public final class BalanceDialog {

    public interface Listener {
        void onBalanceEntered(Card card, BigDecimal balance);
    }

    private final Context context;
    private final Card card;
    private final BigDecimal known;
    private final Listener listener;
    private final AlertDialog dialog;
    private final TextInputLayout inputLayout;
    private final EditText input;
    private final TextView preview;
    private boolean remainingMode;

    public BalanceDialog(Context context, Card card, Listener listener) {
        this.context = context;
        this.card = card;
        this.listener = listener;
        boolean checked = card.balance != null;
        known = Card.parseAmount(checked ? card.balance : card.faceAmount);
        remainingMode = known == null;
        dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.update_balance)
                .setView(R.layout.dialog_balance)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.create();
        inputLayout = dialog.findViewById(R.id.balanceInputLayout);
        input = dialog.findViewById(R.id.balanceInput);
        preview = dialog.findViewById(R.id.balancePreview);
        TextView knownView = dialog.findViewById(R.id.balanceKnown);
        if (known == null) {
            knownView.setVisibility(View.GONE);
        } else {
            knownView.setText(context.getString(checked ? R.string.current_balance : R.string.initial_amount,
                    Card.formatAmount(known)));
        }
        MaterialButtonToggleGroup mode = dialog.findViewById(R.id.balanceMode);
        dialog.findViewById(R.id.balanceModeSpent).setEnabled(known != null);
        mode.check(remainingMode ? R.id.balanceModeRemaining : R.id.balanceModeSpent);
        mode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                remainingMode = checkedId == R.id.balanceModeRemaining;
                refresh();
            }
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable text) {
                refresh();
            }
        });
        input.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> submit());
        Window window = dialog.getWindow();
        if (window != null) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        refresh();
    }

    public void show() {
        dialog.show();
        input.requestFocus();
    }

    private void refresh() {
        inputLayout.setHint(remainingMode ? R.string.remaining_balance : R.string.amount_spent);
        BigDecimal amount = enteredAmount();
        String error = input.length() == 0 ? null : errorFor(amount);
        inputLayout.setError(error);
        inputLayout.setErrorEnabled(error != null);
        boolean showPreview = !remainingMode && amount != null && error == null;
        preview.setVisibility(showPreview ? View.VISIBLE : View.GONE);
        if (showPreview) {
            preview.setText(context.getString(R.string.new_balance, Card.formatAmount(known.subtract(amount))));
        }
    }

    private void submit() {
        BigDecimal amount = enteredAmount();
        String error = input.length() == 0 ? context.getString(R.string.enter_amount) : errorFor(amount);
        if (error != null) {
            inputLayout.setError(error);
            return;
        }
        listener.onBalanceEntered(card, remainingMode ? amount : known.subtract(amount));
        dialog.dismiss();
    }

    private BigDecimal enteredAmount() {
        BigDecimal amount = Card.parseAmount(input.getText().toString());
        return amount != null && amount.scale() <= Card.AMOUNT_DECIMALS ? amount : null;
    }

    private String errorFor(BigDecimal amount) {
        if (amount == null) {
            return context.getString(R.string.invalid_amount);
        }
        if (!remainingMode && amount.compareTo(known) > 0) {
            return context.getString(R.string.amount_exceeds_balance, Card.formatAmount(known));
        }
        return null;
    }
}
