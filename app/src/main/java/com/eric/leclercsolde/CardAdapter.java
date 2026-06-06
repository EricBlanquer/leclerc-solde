package com.eric.leclercsolde;

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
        holder.title.setText(titleText(card));
        holder.subtitle.setText(subtitleText(card));
        holder.itemView.setOnClickListener(v -> clickListener.onClick(card));
    }

    private String titleText(Card card) {
        if (card.faceAmount != null) {
            return card.formattedNumber() + "   ·   " + card.faceAmount + " €";
        }
        return card.formattedNumber();
    }

    private String subtitleText(Card card) {
        switch (card.status) {
            case CHECKING:
                return "Vérification…";
            case DONE:
                String validity = card.balanceValidity != null ? "  (jusqu'au " + card.balanceValidity + ")" : "";
                return "Solde : " + card.balance + " €" + validity;
            case ERROR:
                return "Erreur : " + (card.error != null ? card.error : "inconnue");
            default:
                return "En attente";
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}
