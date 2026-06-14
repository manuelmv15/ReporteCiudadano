package com.bombayashi.reporteciudadano.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface PendingActionDao {

    @Insert
    long insert(PendingActionEntity entity);

    @Query("SELECT * FROM pending_action WHERE status != 'FAILED' ORDER BY createdAt ASC")
    List<PendingActionEntity> getPending();

    @Query("SELECT COUNT(*) FROM pending_action WHERE status != 'FAILED'")
    int countPending();

    @Update
    void update(PendingActionEntity entity);

    @Delete
    void delete(PendingActionEntity entity);
}
