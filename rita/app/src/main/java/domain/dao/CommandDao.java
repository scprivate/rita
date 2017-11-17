package domain.dao;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import java.util.List;

import domain.Command;

/**
 * Created by thales on 17/11/17.
 */


@Dao
public interface CommandDao {
    @Query("SELECT * FROM command")
    List<Command> getAll();

    @Query("SELECT * FROM command WHERE id_cmd = :id")
    Command getById(int id);

    @Query("SELECT * FROM command WHERE name = :name")
    Command getByName(String name);

    @Insert
    void insertAll(Command...commands);

    @Update
    void update(Command command);

    @Delete
    void delete(Command command);
}