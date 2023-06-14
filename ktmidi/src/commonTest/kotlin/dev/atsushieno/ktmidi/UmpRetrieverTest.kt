package dev.atsushieno.ktmidi

import dev.atsushieno.ktmidi.ci.DeviceDetails
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

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

    // Flex Data messages

    @Test
    fun testTempo() {
        val tempo1 = Ump(0xD0100000.toInt(), 0x02FAF080, 0, 0)
        assertEquals(50_000_000, tempo1.tempo, "tempo")
    }

    @Test
    fun testTimeSignature() {
        val ts1 = Ump(0xD010_0001.toInt(), 0x0304_0500, 0, 0)
        assertEquals(3, ts1.timeSignatureNumerator, "timeSignatureNumerator")
        assertEquals(4, ts1.timeSignatureDenominator, "timeSignatureDenominator")
        assertEquals(5, ts1.timeSignatureNumberOf32thNotes, "timeSignatureNumberOf32thNotes")
    }

    @Test
    fun testMetronome() {
        val metronome1 = Ump(0xD010_0002.toInt(), 0x0304_0401, 0x02030000, 0)
        assertEquals(3, metronome1.metronomeClocksPerPrimaryClick, "metronomeClocksPerPrimaryClick")
        assertEquals(4, metronome1.metronomeBarAccent1, "metronomeBarAccent1")
        assertEquals(4, metronome1.metronomeBarAccent2, "metronomeBarAccent2")
        assertEquals(1, metronome1.metronomeBarAccent3, "metronomeBarAccent3")
        assertEquals(2, metronome1.metronomeSubDivisionClick1, "metronomeSubDivisionClick1")
        assertEquals(3, metronome1.metronomeSubDivisionClick2, "metronomeSubDivisionClick2")
    }

    @Test
    fun testKeySignature() {
        val ks1 = Ump(0xD000_0005.toInt(), 0x2600_0000, 0, 0)
            UmpFactory.keySignature(0, 0, 0, ChordSharpFlatsField.DOUBLE_SHARP, TonicNoteField.F) // F is 5(in ABCDEFG... not 3 in CDEFGAB !)
        assertEquals(ChordSharpFlatsField.DOUBLE_SHARP, ks1.keySignatureSharpsFlats, "keySignatureSharpsFlats")
        assertEquals(TonicNoteField.F, ks1.keySignatureTonicNote, "keySignatureTonicNote")
    }

    @Test
    fun testChordName() {
        val chordName1 = Ump(0xD000_0006.toInt(), 0x1601_1101, 0x0203_0000, 0x1301_0102)
        assertEquals(ChordSharpFlatsField.SHARP, chordName1.chordNameSharpsFlats, "chordNameSharpsFlats")
        assertEquals(TonicNoteField.F, chordName1.chordNameChordTonic, "chordNameChordTonic")
        assertEquals(ChordTypeField.MAJOR, chordName1.chordNameChordType, "chordNameChordType")
        assertEquals(ChordAlterationType.ADD_DEGREE + 1U, chordName1.chordNameAlter1, "chordNameAlter1")
        assertEquals(1U, chordName1.chordNameAlter2, "chordNameAlter2")
        assertEquals(2U, chordName1.chordNameAlter3, "chordNameAlter3")
        assertEquals(3U, chordName1.chordNameAlter4, "chordNameAlter4")
        assertEquals(ChordSharpFlatsField.SHARP, chordName1.chordNameBassSharpsFlats, "chordNameBassSharpsFlats")
        assertEquals(TonicNoteField.C, chordName1.chordNameBassNote, "chordNameBassNote")
        assertEquals(ChordTypeField.MAJOR, chordName1.chordNameBassChordType, "chordNameBassChordType")
        assertEquals(1U, chordName1.chordNameBassAlter1, "chordNameBassAlter1")
        assertEquals(2U, chordName1.chordNameBassAlter2, "chordNameBassAlter2")
    }

    @Test
    fun testChordName2() {
        val chordName2 = Ump(0xDF1E_0006.toInt(), 0xE71B_2121.toInt(), 0x3203_0000, 0xF314_3002L.toInt())
        assertEquals(ChordSharpFlatsField.DOUBLE_FLAT, chordName2.chordNameSharpsFlats, "chordNameSharpsFlats")
        assertEquals(TonicNoteField.G, chordName2.chordNameChordTonic, "chordNameChordTonic")
        assertEquals(ChordTypeField.SEVENTH_SUSPENDED_4TH, chordName2.chordNameChordType, "chordNameChordType")
        assertEquals(ChordAlterationType.SUBTRACT_DEGREE + 1U, chordName2.chordNameAlter1, "chordNameAlter1")
        assertEquals(0x21U, chordName2.chordNameAlter2, "chordNameAlter2")
        assertEquals(0x32U, chordName2.chordNameAlter3, "chordNameAlter3")
        assertEquals(3U, chordName2.chordNameAlter4, "chordNameAlter4")
        assertEquals(ChordSharpFlatsField.FLAT, chordName2.chordNameBassSharpsFlats, "chordNameBassSharpsFlats")
        assertEquals(TonicNoteField.C, chordName2.chordNameBassNote, "chordNameBassNote")
        assertEquals(ChordTypeField.DIMINISHED_7TH, chordName2.chordNameBassChordType, "chordNameBassChordType")
        assertEquals(ChordAlterationType.RAISE_DEGREE + 0U, chordName2.chordNameBassAlter1, "chordNameBassAlter1")
        assertEquals(2U, chordName2.chordNameBassAlter2, "chordNameBassAlter2")
    }

    @Test
    fun testMetadataText() {
        val text1 = listOf(Ump(0xD000_0100.toInt(), 0x5445_5354, 0x2053_5452, 0x494e_4700))
        assertEquals("TEST STRING", UmpRetriever.getFlexDataText(text1.iterator()))
    }
    @Test
    fun testMetadataText2() {
        // Text can end without \0.
        val text2 = listOf(Ump(0xD000_0101.toInt(), 0x5445_5354, 0x2053_5452, 0x494e_4731))
        assertEquals("TEST STRING1", UmpRetriever.getFlexDataText(text2.iterator()))
    }
    @Test
    fun testMetadataText3() {
        // multiple packets.
        val text3 = listOf(Ump(0xD045_0100.toInt(), 0x5465_7374, 0x2053_7472, 0x696e_6720),
            Ump(0xD085_0100.toInt(), 0x5468_6174, 0x2053_7061, 0x6e73_204d),
            Ump(0xD0C5_0100.toInt(), 0x6f72_652e, 0, 0)
        )
        assertEquals(PerformanceTextStatus.UNKNOWN, text3[0].flexDataStatus, "flexDataStatus")
        assertEquals(FlexDataStatusBank.METADATA_TEXT, text3[0].flexDataStatusBank, "flexDataStatusBank")
        assertEquals("Test String That Spans More.", UmpRetriever.getFlexDataText(text3.iterator()))
    }

    @Test
    fun performanceText() {
        // contains \0.
        // LAMESPEC: does not this mean the rest of lyrics after the melisma ignored?
        val text1 = listOf(Ump(0xD005_0201.toInt(), 0x4120_6d65, 0x6c69_736d, 0x6100_6168))
        assertEquals(PerformanceTextStatus.LYRICS, text1[0].flexDataStatus, "flexDataStatus")
        assertEquals(FlexDataStatusBank.PERFORMANCE_TEXT, text1[0].flexDataStatusBank, "flexDataStatusBank")
        assertEquals("A melisma\u0000ah", UmpRetriever.getFlexDataText(text1.iterator()))
    }

    // UMP Stream messages

    @Test
    fun testEndpointDiscovery() {
        val ed1 = Ump(0xF000_0101L.toInt(), 13, 0, 0)
        assertEquals(1, ed1.endpointDiscoveryUmpVersionMajor, "endpointDiscoveryUmpVersionMajor")
        assertEquals(1, ed1.endpointDiscoveryUmpVersionMinor, "endpointDiscoveryUmpVersionMinor")
        assertEquals(13, ed1.endpointDiscoveryFilterBitmap, "endpointDiscoveryFilterBitmap")
    }

    @Test
    fun testEndpointInfoNotification() {
        val en1 = Ump(0xF001_0101L.toInt(), 0x8200_0301L.toInt(), 0, 0)
        assertEquals(1, en1.endpointInfoUmpVersionMajor, "endpointInfoUmpVersionMajor")
        assertEquals(1, en1.endpointInfoUmpVersionMinor, "endpointInfoUmpVersionMinor")
        assertEquals(true, en1.endpointInfoStaticFunctionBlocks, "endpointInfoStaticFunctionBlocks")
        assertEquals(2, en1.endpointInfoFunctionBlockCount, "endpointInfoFunctionBlockCount")
        assertEquals(true, en1.endpointInfoMidi2Capable, "endpointInfoMidi2Capable")
        assertEquals(true, en1.endpointInfoMidi1Capable, "endpointInfoMidi1Capable")
        assertEquals(false, en1.endpointInfoSupportsRxJR, "endpointInfoSupportsRxJR")
        assertEquals(true, en1.endpointInfoSupportsTxJR, "endpointInfoSupportsTxJR")
    }

    @Test
    fun testDeviceIdentityNotification() {
        val dn1 = Ump(0xF002_0000L.toInt(), 0x0012_3456, 0x789A_7654, 0x3210_6543)
        val device = dn1.deviceIdentity
        val reference = DeviceDetails(0x123456, 0x789A, 0x7654, 0x32106543)
        assertEquals(reference.manufacturer, device.manufacturer, "manufacturer")
        assertEquals(reference.family, device.family, "family")
        assertEquals(reference.familyModelNumber, device.familyModelNumber, "familyModelNumber")
        assertEquals(reference.softwareRevisionLevel, device.softwareRevisionLevel, "softwareRevisionLevel")
        assertEquals(reference.manufacturer, device.manufacturer, "manufacturer")
    }

    @Test
    fun testEndpointNameNotification() {
        val en1 = listOf(Ump(0xF003_456eL.toInt(), 0x6470_6f69, 0x6e74_4e61, 0x6d65_3132))
        assertEquals("EndpointName12", UmpRetriever.getEndpointName(en1.iterator()))
    }

    @Test
    fun testEndpointNameNotification2() {
        val en2 = listOf(
            Ump(0xF403_456eL.toInt(), 0x6470_6f69, 0x6e74_4e61, 0x6d65_3132),
            Ump(0xFC03_3300L.toInt(), 0, 0))
        assertEquals("EndpointName123", UmpRetriever.getEndpointName(en2.iterator()))
    }

    @Test
    fun testProductInstanceIdNotification() {
        val pn1 = listOf(
            Ump(0xF404_5072L.toInt(), 0x6f64_7563, 0x744e_616d, 0x6520_3132),
            Ump(0xFC04_3300L.toInt(), 0, 0, 0))
        assertEquals("ProductName 123", UmpRetriever.getProductInstanceId(pn1.iterator()))
    }

    @Test
    fun testStreamConfigRequest() {
        val req1 = Ump(0xF005_0302L.toInt(), 0, 0, 0)
        assertEquals(3, req1.streamConfigProtocol, "streamConfigProtocol")
        assertEquals(true, req1.streamConfigSupportsRxJR, "streamConfigSupportsRxJR")
        assertEquals(false, req1.streamConfigSupportsTxJR, "streamConfigSupportsTxJR")
    }

    @Test
    fun testStreamConfigNotification() {
        val req1 = Ump(0xF006_0302L.toInt(), 0, 0, 0)
        assertEquals(3, req1.streamConfigProtocol, "streamConfigProtocol")
        assertEquals(true, req1.streamConfigSupportsRxJR, "streamConfigSupportsRxJR")
        assertEquals(false, req1.streamConfigSupportsTxJR, "streamConfigSupportsTxJR")
    }

    @Test
    fun testFunctionBlockDiscovery() {
        val d1 = Ump(0xF010_0503L.toInt(), 0, 0, 0)
        assertEquals(5, d1.functionBlockCount, "functionBlockCount")
        assertEquals(3, d1.functionBlockDiscoveryFilter, "functionBlockDiscoveryFilter")
    }

    @Test
    fun testFunctionBlockInfoNotification() {
        val fb1 = Ump(0xF011_8539L.toInt(), 0x000301FF, 0, 0)
        assertEquals(true, fb1.functionBlockActive, "functionBlockActive")
        assertEquals(5, fb1.functionBlockCount, "functionBlockCount")
        assertEquals(FunctionBlockUiHint.BOTH, fb1.functionBlockUiHint, "functionBlockUiHint")
        assertEquals(FunctionBlockMidi1Bandwidth.UP_TO_31250BPS, fb1.functionBlockMidi1Port, "functionBlockMidi1Port")
        assertEquals(FunctionBlockDirection.INPUT, fb1.functionBlockDirection, "functionBlockDirection")
        assertEquals(0, fb1.functionBlockFirstGroup, "functionBlockFirstGroup")
        assertEquals(3, fb1.functionBlockGroupCount, "functionBlockGroupCount")
        assertEquals(1, fb1.functionBlockCIVersion, "functionBlockCIVersion")
        assertEquals(255, fb1.functionBlockMaxSysEx8, "functionBlockMaxSysEx8")
    }

    @Test
    fun testFunctionBlockNameNotification() {
        val fn1 = listOf(Ump(0xF012_0746L.toInt(), 0x756e_6374, 0x696f_6e4e, 0x616d_6531))
        assertEquals("FunctionName1", UmpRetriever.getFunctionBlockName(fn1.iterator()))
    }
    @Test
    fun testFunctionBlockNameNotification2() {
        val fn1 = listOf(
            Ump(0xF412_0746L.toInt(), 0x756e_6374, 0x696f_6e4e, 0x616d_6531),
            Ump(0xFC12_0732L.toInt(), 0, 0, 0)
        )
        assertEquals("FunctionName12", UmpRetriever.getFunctionBlockName(fn1.iterator()))
    }
}