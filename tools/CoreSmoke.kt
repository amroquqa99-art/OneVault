import org.onevault.app.core.*
import java.io.File
import kotlin.random.Random

fun main() {
    val budget = StorageBudget(15_000, 11_000, 2_000, 1_000)
    check(budget.safe == 1_000L)
    check(!budget.accepts(1_001))
    check(StorageBudget(Long.MAX_VALUE, 0, Long.MAX_VALUE, Long.MAX_VALUE).safe == 0L)
    check(TransferPolicy.safeForNewUpload(StorageBudget(10_000, 2_000, 2_000), 2_000, 3_000))
    check(!TransferPolicy.safeForNewUpload(StorageBudget(10_000, 2_000, 2_000), 4_000, 3_000))
    check(!TransferPolicy.safeForNewUpload(StorageBudget(0, 0, 0), 1, 0))
    check(ResumableRanges.acknowledgedBytes("bytes=0-262143", 524288)==262144L)
    check(ResumableRanges.acknowledgedBytes(null, 524288)==null)
    try {ResumableRanges.acknowledgedBytes("bytes=1-262143", 524288);error("Invalid range accepted")}
    catch (expected: IllegalArgumentException) { }
    try {ResumableRanges.acknowledgedBytes("bytes=0-524288", 524288);error("Out of bounds accepted")}
    catch (expected: IllegalArgumentException) { }
    check(RegistrationEvidence.keepStronger("confirmed","probable")=="confirmed")
    check(RegistrationEvidence.keepStronger("unknown","probable")=="probable")
    check(budget.accepts(1_000))
    check(PlatformNames.normalize(" HTTPS://WWW.GitHub.Com/login ") == "github.com")
    check(PlatformClassifier.classify("accounts.github.com") == "Development")
    check(PlatformClassifier.classify("mygithub.com") == "Uncategorized")
    check(PlatformClassifier.classify("notion.so") == "Productivity")
    val csv=CsvTools.parse("email,alias\nfoo@gmail.com,\"Hello, World\"\n")
    check(csv[1][1]=="Hello, World")
    val mail="From user Tue Oct 18 12:00:00 2026\nFrom: hello@accounts.github.com\nSubject: Welcome to GitHub\n\nbody\n"
    val findings=mutableListOf<MailEvidence.Finding>()
    MailEvidence.scan(mail.reader()){findings+=it}
    check(findings.size==1 && findings.first().host=="github.com")
    val forged="From: service@legit.test\nSubject: Your statement\n\n" +
        "From: scam@accounts.fakeplatform.net\nSubject: Welcome to FakePlatform\n"
    val falseFindings=mutableListOf<MailEvidence.Finding>()
    MailEvidence.scan(forged.reader()){falseFindings+=it}
    check(falseFindings.isEmpty()) { "Message bodies must never be parsed as registration headers" }
    val pwCsv="""name,url,username,password,note
"My Site",https://accounts.example.org/login,foo@gmail.com,"sensitive,password",private
"Another",https://github.com/login,foo@gmail.com,hunter2,do-not-store
"Duplicate",https://github.com,foo@gmail.com,other,secret
"Unknown",https://elsewhere.com,other@example.org,plaintext,comment
"""
    val pwMetadata=PasswordExportInspector.inspect(pwCsv.reader(),setOf("foo@gmail.com"))
    check(pwMetadata.candidates.size==2 && pwMetadata.unmatchedRows==1)
    check(pwMetadata.candidates.map{it.host}.toSet()==setOf("accounts.example.org","github.com"))
    check(pwMetadata.toString().contains("hunter2").not() && pwMetadata.toString().contains("sensitive,password").not())
    check(BoundedIo.limit(byteArrayOf(1,2,3).inputStream(),4).readBytes().size==3)
    try {BoundedIo.limit(byteArrayOf(1,2,3).inputStream(),2).readBytes();error("Bounded stream bypass")}
    catch (expected: IllegalArgumentException) { }
    val raw = byteArrayOf(1,2,3,4,5,6,7)
    check(BoundedIo.readAtMost(raw.inputStream(), 7).contentEquals(raw))
    try { BoundedIo.readAtMost(raw.inputStream(), 6); error("Bounded input limit bypass") }
    catch (expected: IllegalArgumentException) { }
    val registrations = listOf(
        IdentityInsights.Registration("a","github","Development","confirmed"),
        IdentityInsights.Registration("a","notion","Productivity","probable"),
        IdentityInsights.Registration("a","notion","Productivity","probable"),
        IdentityInsights.Registration("b","github","Development","confirmed")
    )
    val insight=IdentityInsights.forAccount("a",registrations)
    check(insight.total==2 && insight.confirmed==1 && insight.probable==1)
    check(IdentityInsights.categoryBreakdown("a",registrations).size==2)
    val dir = kotlin.io.path.createTempDirectory("onevault-smoke-").toFile()
    try {
        val source = File(dir, "source.bin")
        source.writeBytes(Random(123).nextBytes(4 * 1024 * 1024 + 213))
        val chunks = File(dir, "parts")
        val manifest = ChunkCodec.split(source, chunks, 256 * 1024)
        check(manifest.parts.size > 4)
        val restored = File(dir, "restored.bin")
        ChunkCodec.assemble(manifest.parts, chunks, restored, manifest.sha256)
        check(source.readBytes().contentEquals(restored.readBytes()))
        // Refuse to overwrite either prior chunk data or a completed output.
        val originalCopy=restored.readBytes()
        try {ChunkCodec.assemble(manifest.parts,chunks,restored,manifest.sha256);error("Overwrote output")}
        catch(expected: IllegalArgumentException) { }
        check(originalCopy.contentEquals(restored.readBytes()))
        try {ChunkCodec.split(source,chunks,256*1024);error("Overwrote chunks")}
        catch(expected: IllegalArgumentException) { }
        check(File(chunks,manifest.parts[0].filename).exists())
        File(chunks, manifest.parts[0].filename).appendText("tamper")
        try {
            ChunkCodec.assemble(manifest.parts, chunks, File(dir, "broken.bin"), manifest.sha256)
            error("Corruption detection failed")
        } catch (expected: IllegalArgumentException) { }
        println("PASS quota boundaries / transfer reservations / upload ranges / evidence preservation / categories / CSV / mail / inventory / bounded I/O / chunk integrity / overwrite prevention")
    } finally { dir.deleteRecursively() }
}
