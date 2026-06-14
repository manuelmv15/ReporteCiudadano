package com.bombayashi.reporteciudadano.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ReportCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(ReportCacheEntity entity);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<ReportCacheEntity> entities);

    @Query("SELECT * FROM report_cache")
    List<ReportCacheEntity> getAll();

    @Query("DELETE FROM report_cache WHERE reportId = :reportId")
    void deleteById(String reportId);

    @Delete
    void delete(ReportCacheEntity entity);
}
