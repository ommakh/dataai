package com.example.data.repository

import com.example.data.local.ExtractedLead
import com.example.data.local.LeadDao
import kotlinx.coroutines.flow.Flow

class LeadRepository(private val leadDao: LeadDao) {
    val allLeads: Flow<List<ExtractedLead>> = leadDao.getAllLeads()
    val totalCount: Flow<Int> = leadDao.getCount()
    val syncedCount: Flow<Int> = leadDao.getSyncedCount()

    fun getLeadsByStatus(status: String): Flow<List<ExtractedLead>> =
        leadDao.getLeadsByStatus(status)

    fun getLeadsBySource(source: String): Flow<List<ExtractedLead>> =
        leadDao.getLeadsBySource(source)

    suspend fun getLeadById(id: Long): ExtractedLead? = leadDao.getLeadById(id)

    suspend fun insertLead(lead: ExtractedLead): Long = leadDao.insertLead(lead)

    suspend fun insertLeads(leads: List<ExtractedLead>): List<Long> = leadDao.insertLeads(leads)

    suspend fun updateLead(lead: ExtractedLead) = leadDao.updateLead(lead)

    suspend fun deleteLead(lead: ExtractedLead) = leadDao.deleteLead(lead)

    suspend fun deleteLeadById(id: Long) = leadDao.deleteLeadById(id)

    suspend fun clearAll() = leadDao.clearAll()
}
