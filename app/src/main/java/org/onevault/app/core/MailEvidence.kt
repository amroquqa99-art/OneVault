package org.onevault.app.core

import java.io.Reader

/**
 * Conservative metadata-only scan of user-exported EML or MBOX files.
 * Only genuine message headers are inspected; body text cannot forge registration evidence.
 * Findings are hints, never authoritative proof of an active external account.
 */
object MailEvidence {
    data class Finding(val host:String,val from:String,val subject:String,val confidence:Evidence)
    private val sender = Regex("[A-Za-z0-9._%+\\-]+@([A-Za-z0-9.\\-]+\\.[A-Za-z]{2,})")
    private val keywords = Regex(
        "(?i)(welcome|verify your|verification|confirm your|activate your|account created|sign.?up|registration|reset your password|password reset|"+
            "تأكيد حساب|تفعيل حساب|التحقق من|تسجيل حساب|إنشاء حساب|أهلا بك|مرحباً بك|مرحبًا بك)")
    private val ignored = setOf("gmail.com","googlemail.com","google.com","outlook.com","yahoo.com")
    private const val MAX_HEADER = 1024

    /** Reads line-by-line; MBOX envelopes (From + space) mark distinct messages. */
    fun scan(reader:Reader,onFinding:(Finding)->Unit):Int {
        var from="";var subject="";var inHeaders=true;var seenHeader=false;var lastKey="";var count=0
        fun finishHeader() {
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
                when {
                    line.startsWith("From ") -> {
                        if(inHeaders && seenHeader) finishHeader()
                        from="";subject="";inHeaders=true;seenHeader=false;lastKey=""
                    }
                    !inHeaders -> Unit // Ignore message body, including forged From:/Subject: lines.
                    line.isEmpty() -> {
                        if(seenHeader) finishHeader()
                        inHeaders=false;lastKey=""
                    }
                    (line[0]==' ' || line[0]=='\t') && seenHeader -> {
                        when(lastKey) {
                            "from" -> from=(from+" "+line.trim()).take(MAX_HEADER)
                            "subject" -> subject=(subject+" "+line.trim()).take(MAX_HEADER)
                        }
                    }
                    line.contains(':') -> {
                        seenHeader=true
                        val key=line.substringBefore(':').lowercase()
                        lastKey=key
                        when(key) {
                            "from" -> from=line.substringAfter(':').trim().take(MAX_HEADER)
                            "subject" -> subject=line.substringAfter(':').trim().take(MAX_HEADER)
                        }
                    }
                }
            }
        }
        if(inHeaders && seenHeader) finishHeader()
        return count
    }
}
