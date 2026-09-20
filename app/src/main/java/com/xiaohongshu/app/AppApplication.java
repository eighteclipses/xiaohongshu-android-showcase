package com.xiaohongshu.app;

import android.app.Application;
import android.content.Context;
import com.xiaohongshu.database.AppDatabase;
import com.xiaohongshu.database.DatabaseInitializer;

/**
 * 应用Application类
 * 初始化全局资源和数据库
 */
public class AppApplication extends Application {
    public static final String TAG = "xiaohongshu";
    
    private static Context appContext;
    private static AppDatabase database;
    private DatabaseInitializer databaseInitializer;
    
    /**
     * 获取应用上下文
     */
    public static Context getAppContext() {
        return appContext;
    }
    
    /**
     * 获取数据库实例
     */
    public static AppDatabase getDatabase() {
        return database;
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        appContext = getApplicationContext();
        
        // 初始化数据库
        database = AppDatabase.getInstance(appContext);
        
        // 初始化数据库（迁移数据并初始化基础数据）
        databaseInitializer = new DatabaseInitializer(appContext);
        databaseInitializer.initialize();
    }
    
    @Override
    public void onTerminate() {
        super.onTerminate();
        if (databaseInitializer != null) {
            databaseInitializer.shutdown();
        }
    }
}
