package org.onevault.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import org.onevault.app.core.RegistrationEvidence
import org.onevault.app.security.LocalCrypto
import java.util.UUID

class VaultDb(context: Context): SQLiteOpenHelper(context, "onevault.db", null, 1) {
    private val crypto = LocalCrypto()
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true); db.enableWriteAheadLogging() }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE account(id TEXT PRIMARY KEY,email TEXT UNIQUE NOT NULL,
            alias TEXT NOT NULL,category TEXT NOT NULL,status TEXT NOT NULL,notes TEXT NOT NULL,
            reserved INTEGER NOT NULL DEFAULT 2147483648,quota INTEGER NOT NULL DEFAULT 0,
            used INTEGER NOT NULL DEFAULT 0,auth_sealed TEXT NOT NULL DEFAULT '')""")
        db.execSQL("""CREATE TABLE platform(id TEXT PRIMARY KEY,host TEXT UNIQUE NOT NULL,
            title TEXT NOT NULL,category TEXT NOT NULL)""")
        db.execSQL("""CREATE TABLE association(id TEXT PRIMARY KEY, account_id TEXT NOT NULL,
            platform_id TEXT NOT NULL,evidence TEXT NOT NULL,note TEXT NOT NULL,updated_at INTEGER NOT NULL,
            UNIQUE(account_id,platform_id),FOREIGN KEY(account_id) REFERENCES account(id) ON DELETE CASCADE,
            FOREIGN KEY(platform_id) REFERENCES platform(id) ON DELETE CASCADE)""")
        db.execSQL("""CREATE TABLE secret(id TEXT PRIMARY KEY,account_id TEXT NOT NULL,
            label TEXT NOT NULL,user_name TEXT NOT NULL,sealed TEXT NOT NULL,
            FOREIGN KEY(account_id) REFERENCES account(id) ON DELETE CASCADE)""")
        db.execSQL("""CREATE TABLE remote_file(account_id TEXT NOT NULL,id TEXT NOT NULL,
            name TEXT NOT NULL,mime TEXT NOT NULL,bytes INTEGER NOT NULL,web_link TEXT NOT NULL,
            modified TEXT NOT NULL, PRIMARY KEY(account_id,id),
            FOREIGN KEY(account_id) REFERENCES account(id) ON DELETE CASCADE)""")
        db.execSQL("""CREATE TABLE transfer(id TEXT PRIMARY KEY, account_id TEXT NOT NULL,
            uri TEXT NOT NULL,file_name TEXT NOT NULL,bytes INTEGER NOT NULL,done INTEGER NOT NULL,
            session_sealed TEXT NOT NULL DEFAULT '',state TEXT NOT NULL DEFAULT 'queued',
            FOREIGN KEY(account_id) REFERENCES account(id) ON DELETE CASCADE)""")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw IllegalStateException("Migration required: $oldVersion to $newVersion")
    }
    fun accounts(): List<Account> = readableDatabase.rawQuery(
        "SELECT id,email,alias,category,status,notes,reserved,quota,used,auth_sealed FROM account ORDER BY alias,email",null).use { c ->
        buildList { while(c.moveToNext()) add(Account(c.getString(0),c.getString(1),c.getString(2),
            c.getString(3),c.getString(4),c.getString(5),c.getLong(6),c.getLong(7),c.getLong(8),c.getString(9).isNotEmpty())) }
    }
    fun account(id: String) = accounts().firstOrNull { it.id == id }
    fun addAccount(email: String, alias: String, category: String, reserved: Long = 2L*1024*1024*1024): String {
        require(android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) { "Invalid email" }
        val id = UUID.randomUUID().toString()
        writableDatabase.insertOrThrow("account",null,ContentValues().apply {
            put("id",id);put("email",email.trim().lowercase());put("alias",alias.trim().ifEmpty { email.substringBefore('@') })
            put("category",category.trim().ifEmpty { "General" });put("status","active");put("notes","");put("reserved",reserved)
        })
        return id
    }
    fun updateAccount(id: String, alias: String, category: String, status: String, notes: String, reserved: Long) {
        require(reserved >= 0)
        writableDatabase.update("account",ContentValues().apply {
            put("alias",alias);put("category",category);put("status",status);put("notes",notes);put("reserved",reserved)
        },"id=?",arrayOf(id))
    }
    fun quota(id: String, limit: Long, used: Long) {
        writableDatabase.update("account",ContentValues().apply { put("quota",limit);put("used",used) },"id=?",arrayOf(id))
    }
    fun auth(id: String, state: String) {
        writableDatabase.update("account",ContentValues().apply { put("auth_sealed",crypto.seal(state)) },"id=?",arrayOf(id))
    }
    fun authState(id: String): String? = readableDatabase.rawQuery("SELECT auth_sealed FROM account WHERE id=?",arrayOf(id)).use { c ->
        if (c.moveToFirst() && c.getString(0).isNotEmpty()) crypto.open(c.getString(0)) else null
    }
    fun unlink(id:String) { writableDatabase.update("account",ContentValues().apply { put("auth_sealed","") },"id=?",arrayOf(id)) }
    fun platforms():List<Platform> = readableDatabase.rawQuery("SELECT id,host,title,category FROM platform ORDER BY title",null).use { c ->
        buildList { while(c.moveToNext()) add(Platform(c.getString(0),c.getString(1),c.getString(2),c.getString(3))) }
    }
    fun addPlatform(host:String,title:String,category:String):String {
        val normalized = org.onevault.app.core.PlatformNames.normalize(host)
        require(normalized.contains('.') && !normalized.contains(' '))
        val existing = platforms().firstOrNull { it.host == normalized }
        if(existing!=null) return existing.id
        val id=UUID.randomUUID().toString()
        writableDatabase.insertOrThrow("platform",null,ContentValues().apply {
            put("id",id);put("host",normalized);put("title",title.trim().ifEmpty{normalized});put("category",category.trim().ifEmpty{"General"})
        })
        return id
    }
    fun associations():List<Association> = readableDatabase.rawQuery(
        "SELECT id,account_id,platform_id,evidence,note,updated_at FROM association ORDER BY updated_at DESC",null).use { c ->
        buildList { while(c.moveToNext()) add(Association(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getLong(5))) }
    }
    fun linkAccountPlatform(aid:String,pid:String,evidence:String,note:String="", preserveStrongerEvidence:Boolean=false) {
        require(evidence in listOf("confirmed","probable","unknown"))
        val database=writableDatabase
        database.beginTransaction()
        try {
            val existing=database.rawQuery("SELECT id,evidence,note FROM association WHERE account_id=? AND platform_id=?",
                arrayOf(aid,pid)).use { c ->
                if(c.moveToFirst()) Triple(c.getString(0),c.getString(1),c.getString(2)) else null
            }
            val finalEvidence=if(preserveStrongerEvidence && existing!=null)
                RegistrationEvidence.keepStronger(existing.second,evidence) else evidence
            if(existing!=null) {
                // Import never degrades a confirmed registration or erases its manual evidence note.
                if(!preserveStrongerEvidence || finalEvidence != existing.second) {
                    database.update("association",ContentValues().apply {
                        put("evidence",finalEvidence)
                        put("note",if(preserveStrongerEvidence && existing.third.isNotBlank()) existing.third else note)
                        put("updated_at",System.currentTimeMillis())
                    },"id=?",arrayOf(existing.first))
                }
            } else {
                database.insertOrThrow("association",null,ContentValues().apply {
                    put("id",UUID.randomUUID().toString());put("account_id",aid);put("platform_id",pid)
                    put("evidence",finalEvidence);put("note",note);put("updated_at",System.currentTimeMillis())
                })
            }
            database.setTransactionSuccessful()
        } finally { database.endTransaction() }
    }
    fun storeSecret(accountId:String,label:String,username:String,password:String):String {
        require(password.isNotEmpty())
        val id=UUID.randomUUID().toString()
        writableDatabase.insertOrThrow("secret",null,ContentValues().apply {
            put("id",id);put("account_id",accountId);put("label",label);put("user_name",username);put("sealed",crypto.seal(password))
        })
        return id
    }
    data class SecretHeader(val id:String,val accountId:String,val label:String,val username:String)
    fun secrets():List<SecretHeader> = readableDatabase.rawQuery("SELECT id,account_id,label,user_name FROM secret",null).use { c ->
        buildList { while(c.moveToNext()) add(SecretHeader(c.getString(0),c.getString(1),c.getString(2),c.getString(3))) }
    }
    fun revealSecret(id:String):String = readableDatabase.rawQuery("SELECT sealed FROM secret WHERE id=?",arrayOf(id)).use { c ->
        require(c.moveToFirst()); crypto.open(c.getString(0))
    }
    fun deleteSecret(id:String) { writableDatabase.delete("secret","id=?",arrayOf(id)) }
    fun files(search:String=""):List<RemoteFile> {
        val params = if (search.isEmpty()) emptyArray() else arrayOf("%${search.replace("%","\\%").replace("_","\\_")}%")
        val where = if(search.isEmpty()) "" else " WHERE name LIKE ? ESCAPE '\\' "
        return readableDatabase.rawQuery("SELECT account_id,id,name,mime,bytes,web_link,modified FROM remote_file$where ORDER BY modified DESC LIMIT 1500",params).use { c ->
            buildList { while(c.moveToNext()) add(RemoteFile(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4),c.getString(5),c.getString(6))) }
        }
    }
    fun replaceFiles(accountId:String, remote:List<RemoteFile>) {
        val db=writableDatabase;db.beginTransaction()
        try {
            db.delete("remote_file","account_id=?",arrayOf(accountId))
            remote.forEach { f -> db.insertOrThrow("remote_file",null,ContentValues().apply {
                put("account_id",accountId);put("id",f.id);put("name",f.name);put("mime",f.mime)
                put("bytes",f.bytes);put("web_link",f.webLink);put("modified",f.modified)
            }) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun createJob(accountId:String,uri:String,fileName:String,size:Long):String {
        val id=UUID.randomUUID().toString()
        writableDatabase.insertOrThrow("transfer",null,ContentValues().apply {
            put("id",id);put("account_id",accountId);put("uri",uri);put("file_name",fileName)
            put("bytes",size);put("done",0);put("state","queued")
        })
        return id
    }
    /** Older active transfers reserve capacity before younger jobs start. */
    fun earlierPendingBytes(jobId:String, accountId:String):Long = readableDatabase.rawQuery(
        """SELECT COALESCE(SUM(CASE WHEN bytes > done THEN bytes - done ELSE 0 END), 0)
           FROM transfer WHERE account_id=?
           AND rowid < (SELECT rowid FROM transfer WHERE id=?)
           AND state IN ('queued','uploading','retry pending')""",arrayOf(accountId,jobId)).use { c ->
        if(c.moveToFirst()) c.getLong(0).coerceAtLeast(0L) else 0L
    }

    fun job(id:String):Job? = readableDatabase.rawQuery(
        "SELECT id,account_id,uri,file_name,bytes,done,session_sealed,state FROM transfer WHERE id=?",arrayOf(id)).use { c ->
        if(c.moveToFirst()) Job(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4),c.getLong(5),c.getString(6),c.getString(7)) else null
    }
    fun jobs():List<Job> = readableDatabase.rawQuery("SELECT id,account_id,uri,file_name,bytes,done,session_sealed,state FROM transfer ORDER BY rowid DESC LIMIT 100",null).use { c ->
        buildList { while(c.moveToNext()) add(Job(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4),c.getLong(5),c.getString(6),c.getString(7))) }
    }
    fun updateJob(id:String,done:Long,session:String?,state:String) {
        writableDatabase.update("transfer",ContentValues().apply {
            put("done",done);put("state",state)
            if(session!=null) put("session_sealed", if(session.isEmpty()) "" else crypto.seal(session))
        },"id=?",arrayOf(id))
    }
    fun session(job:Job) = if(job.sessionSealed.isEmpty()) "" else crypto.open(job.sessionSealed)

    /** Backup contains NO OAuth tokens or API credentials; includes passwords only inside encrypted export. */
    fun backupJson():JSONObject {
        fun accountJson(a:Account)=JSONObject().apply {
            put("email",a.email);put("alias",a.alias);put("category",a.category);put("status",a.status)
            put("notes",a.notes);put("reserved",a.reserved);put("source_id",a.id)
        }
        val a=JSONArray();accounts().forEach { a.put(accountJson(it)) }
        val p=JSONArray();platforms().forEach { x -> p.put(JSONObject().apply {
            put("source_id",x.id);put("host",x.host);put("title",x.title);put("category",x.category)
        }) }
        val l=JSONArray();associations().forEach { x -> l.put(JSONObject().apply {
            put("account_id",x.accountId);put("platform_id",x.platformId);put("evidence",x.evidence);put("note",x.note)
        }) }
        val s=JSONArray();secrets().forEach { x -> s.put(JSONObject().apply {
            put("account_id",x.accountId);put("label",x.label);put("username",x.username);put("password",revealSecret(x.id))
        }) }
        return JSONObject().put("schema",1).put("accounts",a).put("platforms",p).put("associations",l).put("secrets",s)
    }
    fun restoreJson(json:JSONObject) {
        require(json.getInt("schema")==1)
        val accountMap=mutableMapOf<String,String>();val platformMap=mutableMapOf<String,String>()
        val db=writableDatabase
        db.beginTransaction()
        try {
            val a=json.getJSONArray("accounts")
            for(i in 0 until a.length()) {
                val obj=a.getJSONObject(i)
                val id=accounts().firstOrNull{it.email==obj.getString("email") }?.id
                    ?:addAccount(obj.getString("email"),obj.getString("alias"),obj.getString("category"),obj.optLong("reserved",0))
                accountMap[obj.getString("source_id")]=id
                updateAccount(id,obj.getString("alias"),obj.getString("category"),obj.optString("status","active"),obj.optString("notes",""),obj.optLong("reserved",0))
            }
            val p=json.getJSONArray("platforms")
            for(i in 0 until p.length()) {
                val obj=p.getJSONObject(i)
                platformMap[obj.getString("source_id")]=addPlatform(obj.getString("host"),obj.getString("title"),obj.getString("category"))
            }
            val l=json.getJSONArray("associations")
            for(i in 0 until l.length()) {
                val obj=l.getJSONObject(i)
                val aid=accountMap[obj.getString("account_id")]?:continue
                val pid=platformMap[obj.getString("platform_id")]?:continue
                linkAccountPlatform(aid,pid,obj.optString("evidence","unknown"),obj.optString("note",""))
            }
            val s=json.getJSONArray("secrets")
            for(i in 0 until s.length()) {
                val obj=s.getJSONObject(i)
                val aid=accountMap[obj.getString("account_id")]?:continue
                val already=secrets().any { it.accountId==aid && it.label==obj.getString("label") && it.username==obj.getString("username") }
                if(!already) storeSecret(aid,obj.getString("label"),obj.getString("username"),obj.getString("password"))
            }
            db.setTransactionSuccessful()
        } finally {db.endTransaction()}
    }
}
