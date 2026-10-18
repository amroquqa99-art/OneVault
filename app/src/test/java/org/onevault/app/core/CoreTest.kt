package org.onevault.app.core

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.random.Random

/** JVM unit tests: no Google/TeraBox credentials and no Android emulator needed. */
class CoreTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun safeQuotaRespectsOtherServicesAndPendingTransfers() {
        val budget = StorageBudget(limit=15_000,used=10_000,reserved=2_000,pending=1_500)
        assertEquals(1_500L,budget.safe)
        assertTrue(budget.accepts(1_500))
        assertFalse(budget.accepts(1_501))
        assertEquals(0L,StorageBudget(10,20,2).safe)
        assertEquals(0L,StorageBudget(Long.MAX_VALUE,0,Long.MAX_VALUE,Long.MAX_VALUE).safe)
    }

    @Test fun activeTransfersMustRespectEarlierReservations() {
        val quota=StorageBudget(limit=20_000,used=9_000,reserved=3_000)
        assertTrue(TransferPolicy.safeForNewUpload(quota,5_000,2_000))
        assertFalse(TransferPolicy.safeForNewUpload(quota,7_000,2_000))
        assertFalse(TransferPolicy.safeForNewUpload(quota,1,Long.MAX_VALUE))
        assertFalse(TransferPolicy.safeForNewUpload(StorageBudget(0,0,0),1,0))
        assertEquals(0L,StorageBudget(Long.MAX_VALUE,0,Long.MAX_VALUE,Long.MAX_VALUE).safe)
    }

    @Test fun googleRangeResponsesMustBeValidated() {
        assertEquals(262144L,ResumableRanges.acknowledgedBytes("bytes=0-262143",524288))
        assertNull(ResumableRanges.acknowledgedBytes(null,524288))
        for(header in listOf("bytes=1-30","bytes=0-524288","garbage","bytes=0--1")) {
            try {ResumableRanges.acknowledgedBytes(header,524288);fail("Accepted invalid: $header")}
            catch (expected: IllegalArgumentException) { }
        }
    }

    @Test fun importedMailCannotDegradeConfirmedRegistration() {
        assertEquals("confirmed",RegistrationEvidence.keepStronger("confirmed","probable"))
        assertEquals("confirmed",RegistrationEvidence.keepStronger("confirmed","unknown"))
        assertEquals("probable",RegistrationEvidence.keepStronger("unknown","probable"))
    }

    @Test fun splitThenAssemblePreservesBytesAndHash() {
        val input=folder.newFile("original.bin")
        input.writeBytes(Random(20).nextBytes(600_123))
        val parts=folder.newFolder("parts")
        val manifest=ChunkCodec.split(input,parts,256*1024)
        assertEquals(3,manifest.parts.size)
        val reconstructed=File(folder.root,"reconstructed.bin")
        ChunkCodec.assemble(manifest.parts,parts,reconstructed,manifest.sha256)
        assertEquals(manifest.sha256,ChunkCodec.sha256(reconstructed))
        assertArrayEquals(input.readBytes(),reconstructed.readBytes())
        try {ChunkCodec.assemble(manifest.parts,parts,reconstructed,manifest.sha256);fail("Must not overwrite existing file")}
        catch (expected: IllegalArgumentException) { }
        assertArrayEquals(input.readBytes(),reconstructed.readBytes())
        try {ChunkCodec.split(input,parts,256*1024);fail("Must not overwrite existing chunks")}
        catch (expected: IllegalArgumentException) { }
    }

    @Test fun tamperedChunkNeverPublishesOutput() {
        val input=folder.newFile("source.bin")
        input.writeBytes(Random(7).nextBytes(300_111))
        val pieces=folder.newFolder("chunks")
        val manifest=ChunkCodec.split(input,pieces,256*1024)
        File(pieces,manifest.parts[0].filename).appendText("corruption")
        val out=File(folder.root,"should-not-exist.bin")
        try {
            ChunkCodec.assemble(manifest.parts,pieces,out,manifest.sha256)
            fail("Expected integrity verification to fail")
        } catch (expected: IllegalArgumentException) {
            assertFalse(out.exists())
            assertFalse(File(folder.root,"should-not-exist.bin.partial").exists())
        }
    }

    @Test fun boundedReadsAndInventory() {
        val data=ByteArray(123) { it.toByte() }
        assertArrayEquals(data,BoundedIo.readAtMost(data.inputStream(),123))
        try { BoundedIo.readAtMost(data.inputStream(),100); fail("Expected bound violation") }
        catch (expected: IllegalArgumentException) { }
        val evidence=listOf(
            IdentityInsights.Registration("a","1","Productivity","confirmed"),
            IdentityInsights.Registration("a","2","Productivity","probable"),
            IdentityInsights.Registration("b","3","Development","unknown"))
        assertEquals(2,IdentityInsights.forAccount("a",evidence).total)
        assertEquals(1,IdentityInsights.categoryBreakdown("a",evidence).size)
        assertEquals(1,IdentityInsights.categoryBreakdown("a",evidence)[0].confirmed)
    }

    @Test fun passwordManagerMetadataNeverStoresSecrets() {
        val csv="name,url,username,password,note\nA,https://github.com/login,user@gmail.com,secret1234,my note\n" 
        val imported=PasswordExportInspector.inspect(csv.reader(),setOf("user@gmail.com"))
        assertEquals(1,imported.candidates.size)
        assertEquals("github.com",imported.candidates[0].host)
        assertFalse(imported.toString().contains("secret1234"))
        assertFalse(imported.toString().contains("my note"))
    }

    @Test fun normalizePlatformHosts() {
        assertEquals("github.com",PlatformNames.normalize(" https://WWW.GITHUB.COM/login "))
        assertEquals("Development",PlatformClassifier.classify("accounts.github.com"))
        assertEquals("Uncategorized",PlatformClassifier.classify("fakegithub.com"))
        assertEquals("Productivity",PlatformClassifier.classify("notion.so"))
    }
}
