package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LeadDao {
    @Query("SELECT * FROM extracted_leads ORDER BY createdAt DESC")
    fun getAllLeads(): Flow<List<ExtractedLead>>

    @Query("SELECT * FROM extracted_leads WHERE id = :id")
    suspend fun getLeadById(id: Long): ExtractedLead?

    @Query("SELECT * FROM extracted_leads WHERE syncStatus = :status ORDER BY createdAt DESC")
    fun getLeadsByStatus(status: String): Flow<List<ExtractedLead>>

    @Query("SELECT * FROM extracted_leads WHERE source = :source ORDER BY createdAt DESC")
    fun getLeadsBySource(source: String): Flow<List<ExtractedLead>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLead(lead: ExtractedLead): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLeads(leads: List<ExtractedLead>): List<Long>

    @Update
    suspend fun updateLead(lead: ExtractedLead)

    @Delete
    suspend fun deleteLead(lead: ExtractedLead)

    @Query("DELETE FROM extracted_leads WHERE id = :id")
    suspend fun deleteLeadById(id: Long)

    @Query("DELETE FROM extracted_leads")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM extracted_leads")
    fun getCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM extracted_leads WHERE syncStatus = 'SYNCED'")
    fun getSyncedCount(): Flow<Int>
}
