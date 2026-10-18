package org.onevault.app.cloud

import org.json.JSONObject
import org.onevault.app.data.RemoteFile
import org.onevault.app.core.ResumableRanges
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Native HTTPS transport. Never log bearer tokens or resumable session URLs. */
class GoogleDriveApi(private val bearer:String) {
    private fun conn(url:String, method:String="GET"):HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod=method;setRequestProperty("Authorization","Bearer $bearer")
        connectTimeout=25_000;readTimeout=70_000;instanceFollowRedirects=false
    }
    private fun urlEncode(value:String):String=URLEncoder.encode(value,"UTF-8")
    private fun response(c:HttpURLConnection):String {
        val code=c.responseCode
        val text=(if(code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }?:""
        if(code !in 200..299) error("Google HTTP $code: ${text.take(250)}")
        return text
    }
    fun quota():Pair<Long,Long> {
        val c=conn("https://www.googleapis.com/drive/v3/about?fields=storageQuota(limit,usage)")
        return try {
            val s=JSONObject(response(c)).getJSONObject("storageQuota")
            Pair(s.optString("limit","0").toLongOrNull()?:0L,s.optString("usage","0").toLongOrNull()?:0L)
        } finally {c.disconnect()}
    }
    fun listAll(accountId:String,maxFiles:Int=10_000):List<RemoteFile> {
        val all=mutableListOf<RemoteFile>()
        var page:String?=null
        do {
            val tail=if(page==null) "" else "&pageToken=${urlEncode(page)}"
            val c=conn("https://www.googleapis.com/drive/v3/files?pageSize=500&q=${urlEncode("trashed = false")}"+
                "&fields=${urlEncode("nextPageToken,files(id,name,mimeType,size,modifiedTime,webViewLink)")}$tail")
            val json=try {JSONObject(response(c))} finally {c.disconnect()}
            val files=json.optJSONArray("files")
            if(files!=null) for(i in 0 until files.length()) {
                val f=files.getJSONObject(i)
                all+=RemoteFile(accountId,f.getString("id"),f.optString("name","Untitled"),
                    f.optString("mimeType","application/octet-stream"),
                    f.optString("size","0").toLongOrNull()?:0L,
                    f.optString("webViewLink",""),f.optString("modifiedTime",""))
            }
            page=json.optString("nextPageToken","").ifBlank{null}
        } while(page!=null && all.size<maxFiles)
        // Do not silently declare a partial cache complete.
        if(page!=null) error("Drive has more than $maxFiles files; increase sync limit")
        return all
    }
    fun download(id:String,out:OutputStream) {
        val c=conn("https://www.googleapis.com/drive/v3/files/${urlEncode(id)}?alt=media")
        try {
            val code=c.responseCode
            if(code!=200) response(c)
            c.inputStream.use { input -> input.copyTo(out,1024*1024) }
            out.flush()
        } finally {c.disconnect()}
    }
    data class TransferResult(val uploaded:Long,val complete:Boolean)
    fun startUpload(fileName:String,mime:String,bytes:Long):String {
        val c=conn("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&fields=id,name,size","POST")
        try {
            c.doOutput=true
            c.setRequestProperty("Content-Type","application/json; charset=UTF-8")
            c.setRequestProperty("X-Upload-Content-Type",mime)
            c.setRequestProperty("X-Upload-Content-Length",bytes.toString())
            val metadata=JSONObject().put("name",fileName).toString().toByteArray(Charsets.UTF_8)
            c.setFixedLengthStreamingMode(metadata.size)
            c.outputStream.use { it.write(metadata) }
            val status=c.responseCode
            if(status !in 200..299) response(c)
            return c.getHeaderField("Location")?:error("Upload session not returned")
        } finally {c.disconnect()}
    }
    fun currentOffset(sessionUrl:String,total:Long):TransferResult {
        // Resumable URI is a bearer credential; never disclose it.
        require(sessionUrl.startsWith("https://www.googleapis.com/") || sessionUrl.startsWith("https://www.google.com/"))
        val c=(URL(sessionUrl).openConnection() as HttpURLConnection).apply {
            requestMethod="PUT";setRequestProperty("Content-Range","bytes */$total")
            setRequestProperty("Content-Length","0")
            connectTimeout=25_000;readTimeout=70_000;instanceFollowRedirects=false
        }
        return try {
            when(c.responseCode) {
                200,201 -> TransferResult(total,true)
                308 -> TransferResult(ResumableRanges.acknowledgedBytes(c.getHeaderField("Range"),total) ?: 0L,false)
                else -> error("Resumable session not valid (HTTP ${c.responseCode})")
            }
        } finally {c.disconnect()}
    }
    fun uploadChunk(sessionUrl:String,input:InputStream,start:Long,total:Long,chunkBytes:Int=4*1024*1024):TransferResult {
        require(sessionUrl.startsWith("https://www.googleapis.com/") || sessionUrl.startsWith("https://www.google.com/"))
        require(start in 0..total && total>0)
        require(chunkBytes >= 256 * 1024 && chunkBytes % (256 * 1024) == 0)
        // A resumed session can acknowledge a partial chunk; the next byte offset
        // comes from Google's Range header, not a locally guessed alignment.
        val bytes=minOf(chunkBytes.toLong(),total-start).toInt()
        if(bytes==0) return TransferResult(total,true)
        val end=start+bytes-1
        val c=(URL(sessionUrl).openConnection() as HttpURLConnection).apply {
            requestMethod="PUT";doOutput=true;instanceFollowRedirects=false
            setRequestProperty("Content-Type","application/octet-stream")
            setRequestProperty("Content-Range","bytes $start-$end/$total")
            setFixedLengthStreamingMode(bytes)
            connectTimeout=25_000;readTimeout=70_000
        }
        try {
            c.outputStream.use { out ->
                val buf=ByteArray(256*1024);var remaining=bytes
                while(remaining>0) {
                    val n=input.read(buf,0,minOf(buf.size,remaining))
                    if(n<0) error("Source file became shorter during upload")
                    out.write(buf,0,n);remaining-=n
                }
            }
            return when(c.responseCode) {
                200,201 -> TransferResult(total,true)
                308 -> {
                    val offset=ResumableRanges.acknowledgedBytes(c.getHeaderField("Range"),total)
                        ?: error("Upload acknowledgement lacks Range; retry by querying session")
                    require(offset in (start+1)..(end+1)) { "Upload offset inconsistent; requery session" }
                    TransferResult(offset,false)
                }
                else -> error("Chunk upload failed (HTTP ${c.responseCode})")
            }
        } finally {c.disconnect()}
    }
}
