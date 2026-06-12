package Adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pokerproject.R;

import java.util.ArrayList;

import Models.GameRoom;

public class RoomAdapter extends RecyclerView.Adapter<RoomAdapter.RoomViewHolder> {

    // רשימת הנתונים שלנו: כל החדרים הפתוחים שנמשכו מפיירבייס
    private ArrayList<GameRoom> rooms;

    // משתנה מסוג "מאזין" - הדרך שלנו להודיע למסך הלובי שלחצו על כפתור "הצטרף" בתוך השורה
    private OnRoomClickListener listener;

    // בנאי: מופעל פעם אחת כשיוצרים את המתאם ב-LobbyActivity
    // הוא מקבל את הרשימה הריקה (שתתמלא בהמשך) ואת מה שצריך לקרות כשלוחצים על חדר
    public RoomAdapter(ArrayList<GameRoom> rooms, OnRoomClickListener listener) {
        this.rooms = rooms;
        this.listener = listener;
    }

    // 1. "ייצור הקופסה": הפונקציה הזו מופעלת כשצריך ליצור שורה חדשה וריקה על המסך
    @NonNull
    @Override
    public RoomAdapter.RoomViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // הופך את קובץ ה-XML של העיצוב (item_room) לאובייקט גרפי אמיתי (View) בזיכרון
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_room, parent, false);

        // שולח את העיצוב ל"מחזיק השורה" (RoomViewHolder) שיצרנו למטה
        return new RoomViewHolder(view);
    }

    // 2. "מילוי הקופסה": הפונקציה הזו מופעלת על כל חדר ברשימה, ותפקידה לחבר את הנתונים לשורה הגרפית
    @Override
    public void onBindViewHolder(@NonNull RoomAdapter.RoomViewHolder holder, int position) {
        // שולפים את החדר הנוכחי מתוך הרשימה לפי המיקום שלו (position)
        GameRoom room = rooms.get(position);

        // מעדכנים את הטקסט שמציג את קוד החדר
        holder.tvItemRoomCode.setText("Room: " + room.getRoomID());

        // מעדכנים את שם המארח (Host) שפתח את החדר
        if (room.getHostName() != null) {
            holder.tvHostName.setText("Host: " + room.getHostName());
        } else {
            holder.tvHostName.setText("Host: Unknown");
        }

        // חישוב כמה שחקנים כבר נמצאים בחדר והצגה בתבנית של "X/4"
        int playerCount = (room.getPlayers() != null) ? room.getPlayers().size() : 0;
        holder.tvItemPlayerCount.setText("Players: " + playerCount + "/4");

        // מגדירים מה יקרה כשילחצו על כפתור "הצטרף" של השורה הספציפית הזו
        holder.btnItemJoin.setOnClickListener(v -> {
            if (listener != null) {
                // מפעיל את הפונקציה שהוגדרה ב-LobbyActivity (שמעבירה אותנו למסך ההמתנה)
                listener.onJoin(room);
            }
        });
    }

    // 3. אומר למערכת של אנדרואיד כמה שורות בסך הכל צריך לצייר
    @Override
    public int getItemCount() {
        return rooms.size();
    }

    // =========================================================================
    // מחלקה פנימית: "מחזיק השורה" (ViewHolder)
    // התפקיד שלה הוא לשמור רפרנסים (חיבורים) לרכיבים הגרפיים של שורה *אחת* בודדת,
    // כדי שלא נצטרך לעשות findViewById כבד בכל פעם שגוללים את המסך
    // =========================================================================
    public class RoomViewHolder extends RecyclerView.ViewHolder {
        TextView tvItemRoomCode, tvItemPlayerCount, tvHostName;
        Button btnItemJoin;

        public RoomViewHolder(@NonNull View itemView) {
            super(itemView);
            // מחברים את הרכיבים מהעיצוב (item_room.xml) למשתנים שלנו
            tvItemRoomCode = itemView.findViewById(R.id.tvRoomCode);
            tvItemPlayerCount = itemView.findViewById(R.id.tvItemPlayerCount);
            tvHostName = itemView.findViewById(R.id.tvHostName);
            btnItemJoin = itemView.findViewById(R.id.btnJoinRoom);
        }
    }
}