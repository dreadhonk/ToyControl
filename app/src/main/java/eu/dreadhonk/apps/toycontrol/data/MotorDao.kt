package eu.dreadhonk.apps.toycontrol.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface MotorDao {
    @Delete
    fun delete(motor: Motor)

    @Insert
    fun insertAll(vararg motors: Motor)

    @Query("SELECT * FROM motor")
    fun getAll(): List<Motor>
}