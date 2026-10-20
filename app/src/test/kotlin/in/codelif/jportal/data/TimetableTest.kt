package `in`.codelif.jportal.data

import `in`.codelif.jportal.domain.BatchPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Base64

class TimetableTest {
    // written by cbor2 the way generate_cache.py writes it: two classes, one batch, one of them an elective
    private val fixture = Base64.getDecoder().decode(
        "H4sIAAAAAAACAzVPS07DMBSME8pvyS67XCBIToF0nShFFRWqaITY2snzJ8pPtonEsl2SYwD34ii4adnNvBnNm/lhrFMNMYgPoLTsWjaEBrTh2ijZcj3K" +
            "tGt6WVuxwvcJxukqxhF9firTl/COrOkypstF/ahIL4JcQKc+qughwVG6neO5yNZZmq9eM7JhMEBr9H70fPfNdz8d5HpngbOfnY+Oj3599+bi0vWur9Bu" +
            "xovuXWnQuy8qS0kNFCLE0aJsSQNVcptbHlguNTS2qW02GUtL48lDYtYLcgj4tvcjxpPCNwccYAk1FEYO1oI4JaYQp2+UHSPsrP/CDvoDDODKRSQBAAA=",
    )

    @Test
    fun `cdn cbor decodes into the tree`() {
        val t = decodeTimetable(fixture)
        assertEquals("v-test", t.version)
        val b = t.batch(BatchPath("btech-128", "sem7", "phase1", "f7"))
        assertNotNull(b)
        val (core, elective) = b!!.classes
        assertEquals(DayOfWeek.THURSDAY, core.day)
        assertEquals(LocalTime.of(10, 0), core.start)
        assertEquals(LocalTime.of(10, 50), core.end)
        assertEquals("Compilers", core.subject)
        assertEquals("CR-4", core.venue)
        assertNull(core.category)
        assertFalse(core.elective)
        assertEquals(listOf("F7", "F8"), core.batches)
        assertEquals(DayOfWeek.MONDAY, elective.day)
        assertEquals("ELECTIVE", elective.category)
        assertTrue(elective.elective)
        assertEquals("P", elective.type)
        assertNull(t.batch(BatchPath("btech-128", "sem7", "phase1", "f9")))
    }

    @Test(expected = UnknownFormat::class)
    fun `a newer format is refused, not misread`() {
        // same fixture with format bumped to 2, flipped in the plain cbor before gzipping
        val raw = java.util.zip.GZIPInputStream(fixture.inputStream()).readBytes()
        val i = raw.indexOfFirst { it == 0x66.toByte() } // "format" key header, text of length 6
        raw[i + 7] = 0x02
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(out).use { it.write(raw) }
        decodeTimetable(out.toByteArray())
    }
}
