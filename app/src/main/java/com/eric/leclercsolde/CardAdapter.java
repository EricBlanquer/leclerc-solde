package com.eric.leclercsolde;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class CardAdapter extends RecyclerView.Adapter<CardAdapter.Holder> {

    public interface OnCardClick {
        void onClick(Card card);
    }

    private final List<Card> items;
    private final OnCardClick clickListener;

    public CardAdapter(List<Card> items, OnCardClick clickListener) {
        this.items = items;
        this.clickListener = clickListener;
    }

    static class Holder extends RecyclerView.ViewHolder {
        final CheckBox check;
        final TextView title;
        final TextView subtitle;

        Holder(View view) {
            super(view);
            check = view.findViewById(R.id.cardCheck);
            title = view.findViewById(R.id.cardTitle);
            subtitle = view.findViewById(R.id.cardSubtitle);
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_card, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Card card = items.get(position);
        holder.check.setOnCheckedChangeListener(null);
        holder.check.setChecked(card.selected);
        holder.check.setOnCheckedChangeListener((button, checked) -> card.selected = checked);
        Context context = holder.itemView.getContext();
        holder.title.setText(titleText(context, card));
        holder.subtitle.setText(subtitleText(context, card));
        holder.itemView.setOnClickListener(v -> clickListener.onClick(card));
    }

    private String titleText(Context context, Card card) {
        if (card.faceAmount != null) {
            return context.getString(R.string.card_title_amount, card.formattedNumber(), card.faceAmount);
        }
        return card.formattedNumber();
    }

    private String subtitleText(Context context, Card card) {
        switch (card.status) {
            case CHECKING:
                return context.getString(R.string.status_checking);
            case DONE:
                if (card.balanceValidity != null) {
                    return context.getString(R.string.status_done_until, card.balance, card.balanceValidity);
                }
                return context.getString(R.string.status_done, card.balance);
            case ERROR:
                return context.getString(R.string.status_error,
                        card.error != null ? card.error : context.getString(R.string.unknown_error));
            default:
                return context.getString(R.string.status_pending);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}
