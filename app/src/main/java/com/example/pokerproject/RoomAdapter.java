package com.example.pokerproject;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class RoomAdapter extends RecyclerView.Adapter<RoomAdapter.RoomViewHolder> {
    private ArrayList<GameRoom> rooms;
    private OnRoomClickListener listener;


    public RoomAdapter(ArrayList<GameRoom> rooms, OnRoomClickListener listener) {
        this.rooms = rooms;
        this.listener = listener;
    }


    @NonNull
    @Override
    public RoomAdapter.RoomViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_room, parent, false);

        return new RoomViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RoomAdapter.RoomViewHolder holder, int position) {
        GameRoom room = rooms.get(position);
        holder.tvItemRoomCode.setText("Room: " + room.getRoomID());

        int playerCount = (room.getPlayers() != null) ? room.getPlayers().size() : 0;
        holder.tvItemPlayerCount.setText("Players: " + playerCount + "/4");

        holder.btnItemJoin.setOnClickListener(v -> {
            if (listener != null) {
                listener.onJoin(room);
            }
        });



    }

    @Override
    public int getItemCount() {
        return rooms.size();
    }

    public class RoomViewHolder extends RecyclerView.ViewHolder {
        TextView tvItemRoomCode, tvItemPlayerCount;
        Button btnItemJoin;

        public RoomViewHolder(@NonNull View itemView) {
            super(itemView);
            tvItemRoomCode = itemView.findViewById(R.id.tvItemRoomCode);
            tvItemPlayerCount = itemView.findViewById(R.id.tvItemPlayerCount);
            btnItemJoin = itemView.findViewById(R.id.btnItemJoin);
        }
    }
}
