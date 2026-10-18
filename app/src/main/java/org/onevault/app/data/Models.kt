package org.onevault.app.data

data class Account(
    val id: String, val email: String, val alias: String, val category: String,
    val status: String, val notes: String, val reserved: Long, val quota: Long,
    val used: Long, val linked: Boolean
)
data class Platform(val id: String, val host: String, val title: String, val category: String)
data class Association(val id: String, val accountId: String, val platformId: String,
    val evidence: String, val note: String, val updatedAt: Long)
data class RemoteFile(val accountId: String, val id: String, val name: String,
    val mime: String, val bytes: Long, val webLink: String, val modified: String)
data class Job(val id: String, val accountId: String, val uri: String, val fileName: String,
    val bytes: Long, val done: Long, val sessionSealed: String, val state: String)
