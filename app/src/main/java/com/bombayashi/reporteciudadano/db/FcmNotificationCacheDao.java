package com.bombayashi.reporteciudadano.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface FcmNotificationCacheDao {
    @Insert
    void insert(FcmNotificationCacheEntity entity);

    @Update
    void update(FcmNotificationCacheEntity entity);

    @Delete
    void delete(FcmNotificationCacheEntity entity);

    @Query("SELECT * FROM fcm_notification_cache WHERE isProcessed = 0 ORDER BY receivedAt DESC")
    List<FcmNotificationCacheEntity> getUnprocessedNotifications();

    @Query("SELECT * FROM fcm_notification_cache WHERE reportId = :reportId ORDER BY receivedAt DESC LIMIT 1")
    FcmNotificationCacheEntity getLatestByReportId(int reportId);

    @Query("SELECT * FROM fcm_notification_cache ORDER BY receivedAt DESC LIMIT :limit")
    List<FcmNotificationCacheEntity> getRecent(int limit);

    @Query("UPDATE fcm_notification_cache SET isProcessed = 1 WHERE reportId = :reportId")
    void markAsProcessed(int reportId);

    @Query("UPDATE fcm_notification_cache SET isViewed = 1 WHERE reportId = :reportId")
    void markAsViewed(int reportId);

    @Query("DELETE FROM fcm_notification_cache WHERE receivedAt < :timestamp")
    void deleteOlderThan(long timestamp);

    @Query("DELETE FROM fcm_notification_cache")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM fcm_notification_cache WHERE isProcessed = 0")
    int getUnprocessedCount();
}
