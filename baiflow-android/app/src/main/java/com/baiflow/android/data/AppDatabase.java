package com.baiflow.android.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/** 本地 Room 数据库 — 离线模式三态共用的笔记存储。 */
@Database(entities = {LocalNote.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract LocalNoteDao noteDao();

    public static AppDatabase get(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    // 笔记本地缓存量级很小（个人服务器），允许主线程同步查询，避免每处 UI 调用
                    // 都套后台线程；若未来缓存增大再改为后台执行器 + 异步 API。
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "baiflow.db")
                            .allowMainThreadQueries()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
