package org.onevault.app.work

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.onevault.app.cloud.GoogleDriveApi
import org.onevault.app.cloud.GoogleTokens
import org.onevault.app.core.StorageBudget
import org.onevault.app.core.TransferPolicy
import org.onevault.app.data.VaultDb

/** Persistent resumable upload: process death and network loss preserve the session. */
class UploadWorker(ctx:Context,params:WorkerParameters):CoroutineWorker(ctx,params) {
    override suspend fun doWork():Result = withContext(Dispatchers.IO) {
        val id=inputData.getString("job_id") ?: return@withContext Result.failure()
        val db=VaultDb(applicationContext)
        val job=db.job(id) ?: return@withContext Result.failure()
        try {
            if(job.bytes<=0L) {
                db.updateJob(id,0,null,"blocked: empty file unsupported")
                return@withContext Result.failure()
            }
            val token=GoogleTokens(applicationContext,db).validToken(job.accountId)
            val api=GoogleDriveApi(token)
            val account=db.account(job.accountId) ?: return@withContext Result.failure()
            val remoteQuota=api.quota()
            db.quota(account.id,remoteQuota.first,remoteQuota.second)
            if(remoteQuota.first <= 0L) {
                db.updateJob(id,job.done,null,"blocked: unavailable quota")
                return@withContext Result.failure()
            }
            var session=db.session(job)
            // Missing quota data is NOT evidence of unlimited capacity. Account for older
            // queued jobs to avoid oversubscribing the same account with concurrent uploads.
            if(session.isBlank() && !TransferPolicy.safeForNewUpload(
                    StorageBudget(remoteQuota.first,remoteQuota.second,account.reserved),
                    job.bytes,db.earlierPendingBytes(id,account.id))) {
                db.updateJob(id,job.done,null,"blocked: insufficient safe space or unknown quota")
                return@withContext Result.failure()
            }
            var sent=job.done
            var confirmedComplete=false
            if(session.isBlank()) {
                session=api.startUpload(job.fileName,"application/octet-stream",job.bytes)
                db.updateJob(id,0,session,"uploading")
            } else {
                val status=api.currentOffset(session,job.bytes)
                if(status.complete) {
                    db.updateJob(id,job.bytes,session,"complete")
                    return@withContext Result.success()
                }
                sent=status.uploaded
                db.updateJob(id,sent,session,"uploading")
            }
            val uri=Uri.parse(job.uri)
            while(sent<job.bytes && !isStopped) {
                applicationContext.contentResolver.openInputStream(uri)?.use { input ->
                    var skipped=0L
                    while(skipped<sent) {
                        val n=input.skip(sent-skipped)
                        if(n<=0) { if(input.read()<0) error("Source is shorter than recorded offset") else skipped++ }
                        else skipped+=n
                    }
                    val result=api.uploadChunk(session,input,sent,job.bytes)
                    sent=result.uploaded
                    db.updateJob(id,sent,session,if(result.complete)"complete" else "uploading")
                    if(result.complete) confirmedComplete=true
                    setProgress(workDataOf("bytes_done" to sent,"bytes_total" to job.bytes))
                } ?: error("Source URI unavailable")
            }
            if(sent==job.bytes) {
                if(confirmedComplete) return@withContext Result.success()
                // A final 308 response does not prove server-side completion.
                val state=api.currentOffset(session,job.bytes)
                if(state.complete) {
                    db.updateJob(id,job.bytes,session,"complete")
                    Result.success()
                } else {
                    db.updateJob(id,state.uploaded,session,"retry pending")
                    Result.retry()
                }
            } else Result.retry()
        } catch(e:Exception) {
            val shouldRetry = runAttemptCount < 8 && !isStopped
            db.updateJob(id,db.job(id)?.done?:0,null,if(shouldRetry) "retry pending" else "failed")
            if(shouldRetry) Result.retry() else Result.failure()
        } finally {db.close()}
    }
}
