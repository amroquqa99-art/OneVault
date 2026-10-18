package org.onevault.app.ui

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.BackoffPolicy
import java.util.concurrent.TimeUnit
import androidx.work.WorkManager
import androidx.work.workDataOf
import org.json.JSONObject
import org.onevault.app.BuildConfig
import org.onevault.app.cloud.GoogleAuth
import org.onevault.app.cloud.GoogleDriveApi
import org.onevault.app.cloud.GoogleTokens
import org.onevault.app.core.BoundedIo
import org.onevault.app.core.IdentityInsights
import org.onevault.app.core.CsvTools
import org.onevault.app.core.MailEvidence
import org.onevault.app.core.PlatformClassifier
import org.onevault.app.core.StorageBudget
import org.onevault.app.data.Account
import org.onevault.app.data.RemoteFile
import org.onevault.app.data.VaultDb
import org.onevault.app.security.PortableBackupCrypto
import org.onevault.app.work.UploadWorker
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private lateinit var db:VaultDb
    private lateinit var auth:GoogleAuth
    private lateinit var root:LinearLayout
    private lateinit var container:LinearLayout
    private var tab="Home"
    private var uploadAccountId:String=""
    private var downloadFile:RemoteFile?=null
    private var exportPassword:CharArray?=null
    private var importPassword:CharArray?=null
    private var evidenceAccountId:String=""

    private val chooseUpload=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null && uploadAccountId.isNotEmpty())queueUpload(uploadAccountId,uri)
    }
    private val chooseDownload=registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val file=downloadFile
        if(uri!=null && file!=null)runNetwork("Download failed") {
            val token=GoogleTokens(this,db).validToken(file.accountId)
            contentResolver.openOutputStream(uri,"wt")!!.use {GoogleDriveApi(token).download(file.id,it)}
            "Downloaded ${file.name}"
        }
        downloadFile=null
    }
    private val chooseEvidence=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null && evidenceAccountId.isNotEmpty()) {
            val id=evidenceAccountId
            runNetwork("Evidence import failed") {
                var findings=0
                val seen=mutableSetOf<String>()
                contentResolver.openInputStream(uri)!!.reader(Charsets.UTF_8).use { reader ->
                    MailEvidence.scan(reader) { evidence ->
                        if(seen.add(evidence.host)) {
                            val platformId=db.addPlatform(evidence.host,evidence.host,PlatformClassifier.classify(evidence.host))
                            db.linkAccountPlatform(id,platformId,"probable","Imported mail header: ${evidence.subject.take(100)}",
                                preserveStrongerEvidence=true)
                            findings++
                        }
                    }
                }
                "Identified $findings unique possible platforms. Review each manually; not proof of current registration."
            }
        }
    }
    private val chooseCsv=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null)runNetwork("CSV import failed") {
            val bytes=contentResolver.openInputStream(uri)!!.use {input->BoundedIo.readAtMost(input,3*1024*1024)}
            require(bytes.size<=3*1024*1024){"CSV is too large"}
            val rows=CsvTools.parse(bytes.toString(Charsets.UTF_8))
            var imported=0
            val existingEmails=db.accounts().mapTo(mutableSetOf()) { it.email.lowercase() }
            rows.forEachIndexed { index,row ->
                if(row.isNotEmpty()) {
                    val email=row[0].trim()
                    if(index==0 && email.equals("email",true))return@forEachIndexed
                    if(android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() && existingEmails.add(email.lowercase())) {
                        db.addAccount(email,row.getOrNull(1)?:email.substringBefore('@'),row.getOrNull(2)?:"General")
                        imported++
                    }
                }
            }
            "Imported $imported accounts"
        }
    }
    private val createBackup=registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pass=exportPassword;exportPassword=null
        if(uri!=null && pass!=null)runNetwork("Export failed") {
            try {
                val data=db.backupJson().toString().toByteArray()
                val sealed=PortableBackupCrypto.encrypt(data,pass)
                contentResolver.openOutputStream(uri,"wt")!!.use {it.write(sealed);it.flush()}
                data.fill(0)
                "Encrypted backup exported"
            } finally {pass.fill('\u0000')}
        } else pass?.fill('\u0000')
    }
    private val openBackup=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val pass=importPassword;importPassword=null
        if(uri!=null && pass!=null)runNetwork("Restore failed") {
            try {
                val bytes=contentResolver.openInputStream(uri)!!.use { BoundedIo.readAtMost(it,8*1024*1024) }
                require(bytes.size<=8*1024*1024){"Backup is too large"}
                val clear=PortableBackupCrypto.decrypt(bytes,pass)
                try {db.restoreJson(JSONObject(String(clear,Charsets.UTF_8)))} finally {clear.fill(0)}
                "Recovery completed. Reconnect Google accounts via OAuth."
            } finally {pass.fill('\u0000')}
        } else pass?.fill('\u0000')
    }

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE,WindowManager.LayoutParams.FLAG_SECURE)
        db=VaultDb(this);auth=GoogleAuth(this,db)
        setup()
        render()
    }
    @Deprecated("Deprecated API in Activity")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==GoogleAuth.OAUTH_REQUEST)auth.receive(requestCode,resultCode,data) { msg ->
            runOnUiThread{toast(msg);render()}
        }
    }
    override fun onDestroy() {auth.close();db.close();super.onDestroy()}
    private fun toast(s:String) = Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    private fun runNetwork(errorTitle:String,block:()->String) {
        thread(name="OneVault-task") {
            val result=try {block()}catch(e:Exception){"$errorTitle: ${e.message?:e.javaClass.simpleName}"}
            runOnUiThread{toast(result);render()}
        }
    }
    private fun setup() {
        root=Ui.column(this).apply {setBackgroundColor(0xFFF3F7F7.toInt())}
        val header=Ui.column(this).apply {
            setPadding(Ui.dp(this@MainActivity,20),Ui.dp(this@MainActivity,16),Ui.dp(this@MainActivity,20),Ui.dp(this@MainActivity,14))
            setBackgroundColor(0xFF102A36.toInt())
            addView(Ui.label(this@MainActivity,"ONEVAULT",22f,true,0xFFFFFFFF.toInt()))
            addView(Ui.label(this@MainActivity,"Your accounts · one secure workspace",12f,false,0xFFBDD0D4.toInt()))
        }
        root.addView(header)
        val nav=HorizontalScrollView(this).apply {isHorizontalScrollBarEnabled=false}
        val menu=LinearLayout(this).apply {setPadding(Ui.dp(this@MainActivity,8),Ui.dp(this@MainActivity,8),Ui.dp(this@MainActivity,8),Ui.dp(this@MainActivity,8))}
        listOf("Home","Accounts","Platforms","Files","Vault","Settings").forEach {screen ->
            menu.addView(Ui.secondaryButton(this,screen) {tab=screen;render()},LinearLayout.LayoutParams(-2,-2).apply{
                setMargins(Ui.dp(this@MainActivity,2),0,Ui.dp(this@MainActivity,2),0)
            })
        }
        nav.addView(menu);root.addView(nav)
        val scroll=ScrollView(this).apply {isFillViewport=true}
        container=Ui.column(this).apply {setPadding(Ui.dp(this@MainActivity,15),Ui.dp(this@MainActivity,16),Ui.dp(this@MainActivity,15),Ui.dp(this@MainActivity,40))}
        scroll.addView(container)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
    }
    private fun render() {
        container.removeAllViews()
        when(tab) {
            "Home" -> home()
            "Accounts" -> accounts()
            "Platforms" -> platforms()
            "Files" -> files()
            "Vault" -> vault()
            "Settings" -> settings()
        }
    }
    private fun title(s:String) {container.addView(Ui.label(this,s,24f,true));container.addView(Ui.space(this,12))}
    private fun home() {
        title("Overview")
        val accounts=db.accounts();val links=db.associations();val files=db.files()
        Ui.card(this,container,"My OneVault") {b ->
            b.addView(Ui.label(this,"${accounts.size} accounts   ·   ${db.platforms().size} platforms",20f,true))
            b.addView(Ui.subtle(this,"${links.size} identity connections  ·  ${files.size} cached cloud files"))
            b.addView(Ui.subtle(this,"${links.count{it.evidence=="confirmed"}} confirmed · ${links.count{it.evidence=="probable"}} probable registrations"))
        }
        val connected=accounts.filter{it.linked};val known=connected.filter{it.quota>0}
        val total=known.sumOf{it.quota};val used=known.sumOf{it.used}
        Ui.card(this,container,"Google Drive storage") {b ->
            b.addView(Ui.label(this,"${Ui.bytes(used)} of ${Ui.bytes(total)}",20f,true))
            b.addView(Ui.subtle(this,"Sum of separately owned quotas (not a pooled Google quota)."))
            b.addView(Ui.subtle(this,"${connected.size} authenticated · ${known.size} quota readings"))
            b.addView(Ui.secondaryButton(this,"Refresh cloud accounts") {syncAll()})
        }
        Ui.card(this,container,"Quick actions") {b ->
            b.addView(Ui.button(this,"Add identity") {accountDialog()})
            b.addView(Ui.space(this,8))
            b.addView(Ui.secondaryButton(this,"Add platform") {platformDialog()})
            b.addView(Ui.space(this,8))
            b.addView(Ui.secondaryButton(this,"Choose file to upload") {chooseTargetForUpload()})
        }
        Ui.card(this,container,"Status") {b ->
            b.addView(Ui.subtle(this,if(BuildConfig.GOOGLE_CLIENT_ID.isBlank())
                "Google login unavailable until OAuth client ID is set in local.properties."
                else "OAuth configured. Link each account individually from Accounts."))
            b.addView(Ui.subtle(this,"TeraBox, Gmail evidence scan and browser link reassembly are future connectors, not enabled."))
        }
    }
    private fun accounts() {
        title("Identity accounts")
        container.addView(Ui.button(this,"+ Add account") {accountDialog()})
        container.addView(Ui.space(this,8))
        container.addView(Ui.secondaryButton(this,"Import accounts CSV") {chooseCsv.launch(arrayOf("text/*","application/octet-stream"))})
        container.addView(Ui.space(this,8))
        container.addView(Ui.secondaryButton(this,"Discover platforms from MBOX/EML export") {
            selectAccount { a ->
                evidenceAccountId=a.id
                chooseEvidence.launch(arrayOf("*/*"))
            }
        })
        container.addView(Ui.space(this,14))
        val allPlatforms=db.platforms().associateBy { it.id }
        val registrations=db.associations().map { relation ->
            IdentityInsights.Registration(relation.accountId,relation.platformId,
                allPlatforms[relation.platformId]?.category?:"Uncategorized",relation.evidence)
        }
        db.accounts().forEach {a ->
            Ui.card(this,container,a.alias) {b ->
                b.addView(Ui.label(this,a.email,14f))
                b.addView(Ui.subtle(this,"${a.category} · ${a.status} · ${if(a.linked)"Google linked" else "Offline registry"}"))
                if(a.quota>0) {
                    val budget=StorageBudget(a.quota,a.used,a.reserved)
                    b.addView(Ui.subtle(this,"${Ui.bytes(a.used)} / ${Ui.bytes(a.quota)} · safe ${Ui.bytes(budget.safe)}"))
                }
                val insight=IdentityInsights.forAccount(a.id,registrations)
                b.addView(Ui.subtle(this,"${insight.total} platforms: ${insight.confirmed} confirmed · ${insight.probable} probable · ${insight.unknown} unknown"))
                val breakdown=IdentityInsights.categoryBreakdown(a.id,registrations).take(4)
                if(breakdown.isNotEmpty()) b.addView(Ui.subtle(this,
                    breakdown.joinToString("  |  ") { "${it.category}: ${it.total}" }))
                b.addView(Ui.secondaryButton(this,"Edit details") {accountDialog(a)})
                b.addView(Ui.space(this,6))
                if(a.linked)b.addView(Ui.secondaryButton(this,"Disconnect Google") {
                    confirm("Disconnect this account?","The local OAuth state is removed. Also revoke the app at myaccount.google.com/connections if you want full revocation.") {
                        db.unlink(a.id);render()
                    }
                }) else b.addView(Ui.button(this,"Connect Google Drive") {
                    try {auth.start(a.id)}catch(e:Exception){toast(e.message?:"Google connection failed")}
                })
                b.addView(Ui.space(this,6))
                b.addView(Ui.secondaryButton(this,"Record platform used") {associateDialog(a)})
            }
        }
    }
    private fun accountDialog(existing:Account?=null) {
        val body=Ui.column(this)
        val email=Ui.field(this,"Email",existing?.email?:"").apply{isEnabled=existing==null}
        val alias=Ui.field(this,"Label / alias",existing?.alias?:"")
        val category=Ui.field(this,"Category: Study, Work, Experiments...",existing?.category?:"General")
        val status=Ui.field(this,"State: active, dormant, archived",existing?.status?:"active")
        val reserved=Ui.field(this,"Reserve GiB",existing?.let{"%.2f".format(it.reserved/1073741824.0)}?:"2")
            .apply {inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL}
        val notes=Ui.field(this,"Notes",existing?.notes?:"")
        listOf(email,alias,category,status,reserved,notes).forEach{body.addView(it);body.addView(Ui.space(this,6))}
        AlertDialog.Builder(this).setTitle(if(existing==null)"Add identity" else "Edit identity")
            .setView(body).setNegativeButton("Cancel",null).setPositiveButton("Save") {_,_->
                try {
                    val bytes=((reserved.text.toString().toDouble())*1073741824.0).toLong()
                    if(existing==null) db.addAccount(email.text.toString(),alias.text.toString(),category.text.toString(),bytes)
                    else db.updateAccount(existing.id,alias.text.toString(),category.text.toString(),status.text.toString(),notes.text.toString(),bytes)
                    render()
                } catch(e:Exception){toast("Could not save: ${e.message}")}
            }.show()
    }
    private fun platforms() {
        title("Platform directory")
        container.addView(Ui.button(this,"+ Record platform") {platformDialog()})
        container.addView(Ui.space(this,14))
        val accounts=db.accounts().associateBy{it.id}
        val links=db.associations().groupBy{it.platformId}
        db.platforms().forEach {p ->
            Ui.card(this,container,p.title) {b ->
                b.addView(Ui.subtle(this,"${p.host} · ${p.category}"))
                val connected=links[p.id].orEmpty()
                if(connected.isEmpty()) b.addView(Ui.subtle(this,"No associated identities"))
                connected.forEach {l ->
                    b.addView(Ui.label(this,"${accounts[l.accountId]?.email?:"Unknown"} · ${l.evidence}",13f))
                }
                b.addView(Ui.secondaryButton(this,"Connect identity") {selectAccount {a->
                    linkDialog(a.id,p.id)
                }})
            }
        }
    }
    private fun platformDialog() {
        val body=Ui.column(this)
        val host=Ui.field(this,"Hostname, e.g. github.com")
        val name=Ui.field(this,"Platform name, e.g. GitHub")
        val category=Ui.field(this,"Category", "Productivity")
        listOf(host,name,category).forEach{body.addView(it);body.addView(Ui.space(this,6))}
        AlertDialog.Builder(this).setTitle("Record platform").setView(body).setNegativeButton("Cancel",null)
            .setPositiveButton("Save") {_,_->
                try {db.addPlatform(host.text.toString(),name.text.toString(),category.text.toString());render()}
                catch(e:Exception){toast(e.message?:"Invalid platform")}
            }.show()
    }
    private fun associateDialog(a:Account) {
        val platforms=db.platforms()
        if(platforms.isEmpty()) {toast("Record at least one platform first");return}
        AlertDialog.Builder(this).setTitle("Platform used by ${a.alias}")
            .setItems(platforms.map{it.title}.toTypedArray()) {_,index->linkDialog(a.id,platforms[index].id)}
            .show()
    }
    private fun linkDialog(accountId:String,platformId:String) {
        val body=Ui.column(this)
        val evidence=Ui.field(this,"Evidence: confirmed, probable, unknown", "confirmed")
        val note=Ui.field(this,"How do you know? (optional)")
        body.addView(evidence);body.addView(note)
        AlertDialog.Builder(this).setTitle("Record account connection").setView(body).setNegativeButton("Cancel",null)
            .setPositiveButton("Save") {_,_->
                try {db.linkAccountPlatform(accountId,platformId,evidence.text.toString().trim().lowercase(),note.text.toString());render()}
                catch(e:Exception){toast(e.message?:"Invalid evidence type")}
            }.show()
    }
    private fun files() {
        title("Unified files")
        container.addView(Ui.button(this,"Upload to Google Drive") {chooseTargetForUpload()})
        container.addView(Ui.space(this,6))
        container.addView(Ui.secondaryButton(this,"Refresh file index") {syncAll()})
        container.addView(Ui.space(this,6))
        val search=Ui.field(this,"Search cached file names")
        container.addView(search)
        val results=Ui.column(this)
        container.addView(results)
        fun populate(q:String) {
            results.removeAllViews()
            val accounts=db.accounts().associateBy{it.id}
            db.files(q).take(120).forEach {f ->
                Ui.card(this,results,f.name) {b ->
                    b.addView(Ui.subtle(this,"${accounts[f.accountId]?.alias?:"Google"} · ${Ui.bytes(f.bytes)} · ${f.mime.substringAfterLast('/')}"))
                    b.addView(Ui.secondaryButton(this,"Details / Open") {fileDialog(f)})
                }
            }
        }
        populate("")
        search.addTextChangedListener(object:android.text.TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,st:Int,c:Int,a:Int){}
            override fun onTextChanged(s:CharSequence?,st:Int,b:Int,c:Int){populate(s?.toString()?:"")}
            override fun afterTextChanged(s:android.text.Editable?){}
        })
        Ui.card(this,container,"Transfer queue") {b ->
            db.jobs().take(12).forEach {j ->b.addView(Ui.subtle(this,"${j.fileName}: ${j.state} · ${Ui.bytes(j.done)} / ${Ui.bytes(j.bytes)}"))}
        }
    }
    private fun fileDialog(f:RemoteFile) {
        val actions=mutableListOf("Download")
        if(f.webLink.isNotBlank()) {actions+="Open in Google Drive";actions+="Copy original Drive link"}
        AlertDialog.Builder(this).setTitle(f.name).setItems(actions.toTypedArray()) {_,index->
            when(actions[index]) {
                "Download" -> {
                    if(f.mime.startsWith("application/vnd.google-apps."))toast("Native Google Docs require export; open in Drive")
                    else {downloadFile=f;chooseDownload.launch(f.name)}
                }
                "Open in Google Drive" -> startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(f.webLink)))
                "Copy original Drive link" -> copy(f.webLink)
            }
        }.show()
    }
    private fun syncAll() {
        runNetwork("Cloud sync failed") {
            val accounts=db.accounts().filter{it.linked};var synced=0
            val failures=mutableListOf<String>()
            accounts.forEach {a ->
                try {
                    val token=GoogleTokens(this,db).validToken(a.id)
                    val api=GoogleDriveApi(token)
                    val quota=api.quota()
                    val files=api.listAll(a.id)
                    db.replaceFiles(a.id,files)
                    db.quota(a.id,quota.first,quota.second)
                    synced++
                } catch(e:Exception){failures+="${a.alias}: ${e.message}"}
            }
            "Synced $synced/${accounts.size}. ${failures.joinToString("; ").take(190)}"
        }
    }
    private fun selectAccount(onSelected:(Account)->Unit) {
        val a=db.accounts()
        if(a.isEmpty()) {toast("Add an account first");return}
        AlertDialog.Builder(this).setTitle("Choose identity")
            .setItems(a.map{it.alias+" · "+it.email}.toTypedArray()) {_,idx->onSelected(a[idx])}.show()
    }
    private fun chooseTargetForUpload() {
        val accounts=db.accounts().filter{it.linked}
        if(accounts.isEmpty()){toast("Connect a Google account first");return}
        AlertDialog.Builder(this).setTitle("Upload to which Google account?")
            .setItems(accounts.map{it.alias+" · "+it.email}.toTypedArray()) {_,idx->
                uploadAccountId=accounts[idx].id
                chooseUpload.launch(arrayOf("*/*"))
            }.show()
    }
    private fun queueUpload(accountId:String,uri:Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
            var name="upload.bin";var size=0L
            contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE),null,null,null)?.use{c->
                if(c.moveToFirst()) {
                    val ni=c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si=c.getColumnIndex(OpenableColumns.SIZE)
                    if(ni>=0)name=c.getString(ni)?:name
                    if(si>=0)size=c.getLong(si)
                }
            }
            require(size>0){"Selected file must report a nonzero size"}
            val jobId=db.createJob(accountId,uri.toString(),name,size)
            val request=OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(workDataOf("job_id" to jobId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(this).enqueueUniqueWork("upload-$jobId",ExistingWorkPolicy.KEEP,request)
            toast("Upload queued. It can resume after an interruption.")
            render()
        }catch(e:Exception){toast("Upload could not start: ${e.message}")}
    }
    private fun vault() {
        title("Encrypted vault")
        container.addView(Ui.subtle(this,"Credentials are encrypted with Android Keystore and never sent to OneVault servers."))
        container.addView(Ui.space(this,8))
        container.addView(Ui.button(this,"+ Store password") {addSecret()})
        container.addView(Ui.space(this,14))
        val accounts=db.accounts().associateBy{it.id}
        db.secrets().forEach{s->
            Ui.card(this,container,s.label) {b->
                b.addView(Ui.subtle(this,"${s.username} · ${accounts[s.accountId]?.alias?:""}"))
                b.addView(Ui.secondaryButton(this,"Reveal (device screen protected)") {
                    confirm("Reveal secret?","Make sure nobody can view your screen.") {
                        try {AlertDialog.Builder(this).setTitle(s.label).setMessage(db.revealSecret(s.id))
                            .setPositiveButton("Close",null).show()}
                        catch(e:Exception){toast("Cannot decrypt: ${e.message}")}
                    }
                })
                b.addView(Ui.space(this,6))
                b.addView(Ui.secondaryButton(this,"Delete secret") {
                    confirm("Delete saved secret?","This permanently deletes the encrypted entry.") {
                        db.deleteSecret(s.id);render()
                    }
                })
            }
        }
    }
    private fun addSecret() {
        selectAccount { a ->
            val body=Ui.column(this)
            val label=Ui.field(this,"Credential name", "Google login")
            val username=Ui.field(this,"Username",a.email)
            val password=Ui.field(this,"Password",password=true)
            listOf(label,username,password).forEach{body.addView(it);body.addView(Ui.space(this,7))}
            AlertDialog.Builder(this).setTitle("Store encrypted credential")
                .setView(body).setNegativeButton("Cancel",null).setPositiveButton("Save") {_,_->
                    try {db.storeSecret(a.id,label.text.toString(),username.text.toString(),password.text.toString());password.text.clear();render()}
                    catch(e:Exception){toast(e.message?:"Cannot store secret")}
                }.show()
        }
    }
    private fun settings() {
        title("Security & recovery")
        Ui.card(this,container,"Encrypted recovery backup") {b->
            b.addView(Ui.subtle(this,"Exports identities, platform links and decrypted-in-memory vault entries, then encrypts everything with a passphrase. OAuth tokens are excluded."))
            b.addView(Ui.button(this,"Export encrypted .ovb") {passDialog("Set export passphrase") {pass->
                exportPassword=pass;createBackup.launch("OneVault-recovery.ovb")
            }})
            b.addView(Ui.space(this,7))
            b.addView(Ui.secondaryButton(this,"Restore encrypted .ovb") {passDialog("Enter backup passphrase") {pass->
                importPassword=pass;openBackup.launch(arrayOf("*/*"))
            }})
        }
        Ui.card(this,container,"Integration status") {b->
            b.addView(Ui.subtle(this,"Google OAuth client: ${if(BuildConfig.GOOGLE_CLIENT_ID.isEmpty())"NOT CONFIGURED" else "CONFIGURED"}"))
            b.addView(Ui.subtle(this,"Google Drive: metadata, quota, app-created upload, file download."))
            b.addView(Ui.subtle(this,"TeraBox: adapter not enabled; requires authorized API access."))
            b.addView(Ui.subtle(this,"Gmail discovery: requires separate restricted scope; not active."))
            b.addView(Ui.subtle(this,"Universal link sharing: requires a trusted live gateway; not active."))
        }
        Ui.card(this,container,"Project") {b->
            b.addView(Ui.label(this,"OneVault ${BuildConfig.VERSION_NAME}",16f,true))
            b.addView(Ui.subtle(this,"Local-first · no analytics · no central account · private-use software"))
        }
    }
    private fun passDialog(title:String,onPass:(CharArray)->Unit) {
        val field=Ui.field(this,"At least 12 characters",password=true)
        AlertDialog.Builder(this).setTitle(title).setView(field).setNegativeButton("Cancel",null)
            .setPositiveButton("Continue") {_,_->
                val pass=field.text.toString().toCharArray()
                field.text.clear()
                if(pass.size<12) {pass.fill('\u0000');toast("Passphrase must have at least 12 characters")}
                else onPass(pass)
            }.show()
    }
    private fun copy(value:String) {
        val clip=ClipData.newPlainText("OneVault link",value)
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
        toast("Copied")
    }
    private fun confirm(title:String,message:String,action:()->Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("Cancel",null)
            .setPositiveButton("Continue") {_,_->action()}.show()
    }
}
