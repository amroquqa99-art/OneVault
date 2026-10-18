package org.onevault.app.core

import java.io.Reader

/** Import of user-exported MBOX/EML metadata; never uses Gmail credentials or reads message bodies. */
object MailEvidence {
    data class Finding(val host:String,val from:String,val subject:String,val confidence:Evidence)
    private val sender = Regex("[A-Za-z0-9._%+\\-]+@([A-Za-z0-9.\\-]+\\.[A-Za-z]{2,})")
    private val keywords = Regex(
        "(?i)(welcome|verify your|verification|confirm your|activate your|account created|sign.?up|registration|reset your password|password reset|"+
            "تأكيد حساب|تفعيل حساب|التحقق من|تسجيل حساب|إنشاء حساب|أهلا بك|مرحباً بك|مرحبًا بك)")
    private val ignored = setOf("gmail.com","googlemail.com","google.com","outlook.com","yahoo.com")
    /** Mail headers only, handles unfolded multi-line From/Subject and per-message boundaries. */
    fun scan(reader:Reader,onFinding:(Finding)->Unit):Int {
        var from="";var subject="";var header=false;var lastKey="";var count=0
        fun finish() {
            if(!keywords.containsMatchIn(subject))return
            val domain=sender.find(from)?.groupValues?.get(1)?.lowercase()?:return
            val parts=domain.split('.')
            if(parts.size<2)return
            val host=parts.takeLast(2).joinToString(".")
            if(host in ignored || host.endsWith(".invalid"))return
            onFinding(Finding(host,from.take(160),subject.take(200),Evidence.PROBABLE))
            count++
        }
        reader.buffered().useLines { lines ->
            lines.forEach { line ->
                if(line.startsWith("From ")) {
                    if(header)finish()
                    from="";subject="";header=true;lastKey=""
                } else if(line.startsWith("From:",true)) {
                    if(!header)header=true
                    from=line.substringAfter(':').trim();lastKey="from"
                } else if(line.startsWith("Subject:",true)) {
                    if(!header)header=true
                    subject=line.substringAfter(':').trim();lastKey="subject"
                } else if(line.isNotEmpty() && (line[0]==' '||line[0]=='\t') && header) {
                    when(lastKey) {"from"->from+=" "+line.trim();"subject"->subject+=" "+line.trim()}
                } else if(line.isEmpty() && header) {
                    finish();header=false;lastKey=""
                } else if(header && line.contains(':'))lastKey=""
            }
        }
        if(header)finish()
        return count
    }
}
