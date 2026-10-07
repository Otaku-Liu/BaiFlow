package com.baiflow.android.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/** 本地 Room 数据库 — 笔记存储（在线同步的本地镜像）。 */
@Database(entities = {LocalNote.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    /** v2：LocalNote 增加 clientId（推送 CREATE 的幂等 id） */
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE bf_local_note ADD COLUMN clientId TEXT");
        }
    };

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
                            .addMigrations(MIGRATION_1_2)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
