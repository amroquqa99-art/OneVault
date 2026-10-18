import org.onevault.app.core.*
import java.io.File
import kotlin.random.Random

fun main() {
    val budget = StorageBudget(15_000, 11_000, 2_000, 1_000)
    check(budget.safe == 1_000L)
    check(!budget.accepts(1_001))
    check(budget.accepts(1_000))
    check(PlatformNames.normalize(" HTTPS://WWW.GitHub.Com/login ") == "github.com")
    val csv=CsvTools.parse("email,alias\nfoo@gmail.com,\"Hello, World\"\n")
    check(csv[1][1]=="Hello, World")
    val mail="From user Tue Oct 18 12:00:00 2026\nFrom: hello@accounts.github.com\nSubject: Welcome to GitHub\n\nbody\n"
    val findings=mutableListOf<MailEvidence.Finding>()
    MailEvidence.scan(mail.reader()){findings+=it}
    check(findings.size==1 && findings.first().host=="github.com")
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
        File(chunks, manifest.parts[0].filename).appendText("tamper")
        try {
            ChunkCodec.assemble(manifest.parts, chunks, File(dir, "broken.bin"), manifest.sha256)
            error("Corruption detection failed")
        } catch (expected: IllegalArgumentException) { }
        println("PASS quota policy / CSV / email evidence / naming / chunks / corruption detection")
    } finally { dir.deleteRecursively() }
}
