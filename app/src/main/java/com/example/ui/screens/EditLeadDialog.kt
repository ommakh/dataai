package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.local.ExtractedLead

@Composable
fun EditLeadDialog(
    lead: ExtractedLead,
    onDismiss: () -> Unit,
    onSave: (ExtractedLead) -> Unit,
    onSyncNow: (ExtractedLead) -> Unit
) {
    var name by remember { mutableStateOf(lead.name) }
    var email by remember { mutableStateOf(lead.email) }
    var phone by remember { mutableStateOf(lead.phone) }
    var company by remember { mutableStateOf(lead.company) }
    var jobTitle by remember { mutableStateOf(lead.jobTitle) }
    var website by remember { mutableStateOf(lead.website) }
    var address by remember { mutableStateOf(lead.address) }
    var notes by remember { mutableStateOf(lead.notes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Lead Details",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = company,
                    onValueChange = { company = it },
                    label = { Text("Company") },
                    leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = jobTitle,
                    onValueChange = { jobTitle = it },
                    label = { Text("Job Title / Role") },
                    leadingIcon = { Icon(Icons.Default.Work, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = website,
                    onValueChange = { website = it },
                    label = { Text("Website") },
                    leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes & Context") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = lead.copy(
                        name = name.trim(),
                        email = email.trim(),
                        phone = phone.trim(),
                        company = company.trim(),
                        jobTitle = jobTitle.trim(),
                        website = website.trim(),
                        address = address.trim(),
                        notes = notes.trim()
                    )
                    onSave(updated)
                }
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = {
                        val updated = lead.copy(
                            name = name.trim(),
                            email = email.trim(),
                            phone = phone.trim(),
                            company = company.trim(),
                            jobTitle = jobTitle.trim(),
                            website = website.trim(),
                            address = address.trim(),
                            notes = notes.trim()
                        )
                        onSave(updated)
                        onSyncNow(updated)
                    }
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Save & Sync")
                }
                OutlinedButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
