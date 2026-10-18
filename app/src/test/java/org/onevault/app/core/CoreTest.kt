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

    @Test fun normalizePlatformHosts() {
        assertEquals("github.com",PlatformNames.normalize(" https://WWW.GITHUB.COM/login "))
    }
}
