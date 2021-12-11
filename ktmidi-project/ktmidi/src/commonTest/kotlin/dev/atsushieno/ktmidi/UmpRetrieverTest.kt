package dev.atsushieno.ktmidi

import kotlin.test.Test
import kotlin.test.assertContentEquals

class UmpRetrieverTest {
    @Test
    fun getSysex7Data1() {
        val pl1 = mutableListOf<Ump>()
        val src1 = listOf<Byte>(0, 0, 1, 2, 3, 4)
        UmpFactory.sysex7Process(0, src1) { l, _ -> pl1.add(Ump(l)) }
        val iter1 = pl1.iterator()
        val actual1 = UmpRetriever.getSysex7Data(iter1)
        assertContentEquals(src1, actual1, "bytes 1")
    }
    @Test
    fun getSysex7Data2() {
        val pl2 = mutableListOf<Ump>()
        val src2 = listOf<Byte>(0, 0, 1, 2, 3, 4, 5)
        UmpFactory.sysex7Process(0, src2) { l, _ -> pl2.add(Ump(l)) }
        val iter2 = pl2.iterator()
        val actual2 = UmpRetriever.getSysex7Data(iter2)
        assertContentEquals(src2, actual2, "bytes 2")

        // more tests (not just in_one_ump) are in UmpFactoryTest.kt.
    }

    @Test
    fun getSysex8Data() {
        val pl1 = mutableListOf<Ump>()
        val src1 = listOf<Byte>(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13)
        UmpFactory.sysex8Process(0, src1, 0) { l1, l2, _ -> pl1.add(Ump(l1, l2)) }
        val actual1 = UmpRetriever.getSysex8Data(pl1.iterator())
        assertContentEquals(src1, actual1, "bytes 1")

        // more tests (not just in_one_ump) are in UmpFactoryTest.kt.
    }
}