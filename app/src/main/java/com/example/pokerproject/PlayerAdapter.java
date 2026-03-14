package com.example.pokerproject;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class PlayerAdapter extends RecyclerView.Adapter<PlayerAdapter.PlayerViewHolder> {
    private List<User> players = new ArrayList<>();
    private OnPlayerKickListener onPlayerKickListener;
    private String myUid;
    private boolean isHost;
    public PlayerAdapter(String uId, OnPlayerKickListener listener)
    {
        this.myUid = uId;
        this.onPlayerKickListener = listener;
        this.players = new ArrayList<>();

    }
    public void updateList(List<User> newPlayers, boolean isHost) {
        this.players = newPlayers;
        this.isHost = isHost; // שומרים את הסטטוס אם אני מארח
        notifyDataSetChanged(); // מעדכן את התצוגה
    }





    @NonNull
    @Override
    public PlayerAdapter.PlayerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_player, parent, false);
        return new PlayerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlayerAdapter.PlayerViewHolder holder, int position) {
        User player = players.get(position);

        // 1. נתונים בסיסיים
        holder.tvNickname.setText(player.getNickname());
        if (player.getImageURL() != null) {
            Glide.with(holder.itemView.getContext()).load(player.getImageURL()).into(holder.ivProfile);
        }

        // 2. לוגיקת כפתור Kick (החלק החסר)
        // האם אני המארח? וגם השחקן הזה הוא לא אני?
        if (isHost && !player.getUid().equals(myUid)) {
            holder.btnKick.setVisibility(View.VISIBLE);
            holder.btnKick.setOnClickListener(v -> {
                // קריאה למאזין החיצוני
                if (onPlayerKickListener != null) {
                    onPlayerKickListener.onKick(player);
                }
            });
        } else {
            // אם אני לא מנהל, או שזה השם שלי - תסתיר את הכפתור
            holder.btnKick.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return players.size();
    }
    public static class PlayerViewHolder extends RecyclerView.ViewHolder {
        TextView tvNickname;
        ImageButton btnKick;
        ImageView ivProfile;
        public PlayerViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNickname = itemView.findViewById(R.id.tvNickname);
            btnKick = itemView.findViewById(R.id.btnKick);
            ivProfile = itemView.findViewById(R.id.ivProfile);

        }
    }
}

