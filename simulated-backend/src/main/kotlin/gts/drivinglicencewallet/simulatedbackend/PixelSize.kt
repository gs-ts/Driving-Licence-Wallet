package gts.drivinglicencewallet.simulatedbackend

/** A photo's size in pixels. */
internal data class PixelSize(val width: Int, val height: Int) {

    companion object {

        /**
         * Reads a JPEG's size from its frame header, without decoding the image.
         * Returns null if [bytes] aren't a JPEG, or if there's no baseline or progressive frame header.
         */
        fun fromJpeg(bytes: ByteArray): PixelSize? {
            val frameHeader = if (bytes.startsWithJpegSignature()) findFrameHeader(bytes = bytes) else null
            return frameHeader?.let { offset ->
                PixelSize(
                    width = bytes.unsignedShortAt(index = offset + WIDTH_OFFSET),
                    height = bytes.unsignedShortAt(index = offset + HEIGHT_OFFSET)
                )
            }
        }
    }
}

/**
 * Walks the segments after the start-of-image marker. Each one starts with 0xFF, a marker byte, and its length.
 * Returns where the frame header starts, or null if the headers end without one.
 */
private fun findFrameHeader(bytes: ByteArray): Int? {
    var offset = FIRST_SEGMENT
    while (offset + FRAME_HEADER_LENGTH <= bytes.size && bytes.unsignedByteAt(index = offset) == MARKER_PREFIX) {
        if (bytes.unsignedByteAt(index = offset + 1) in BASELINE_FRAME_MARKER..PROGRESSIVE_FRAME_MARKER) return offset
        // The length counts itself but not the 2 marker bytes.
        offset += 2 + bytes.unsignedShortAt(index = offset + 2)
    }
    return null
}

private fun ByteArray.startsWithJpegSignature(): Boolean =
    take(n = JPEG_SIGNATURE.size).map { byte -> byte.toUByte().toInt() } == JPEG_SIGNATURE

private fun ByteArray.unsignedByteAt(index: Int): Int = this[index].toUByte().toInt()

/** JPEG stores numbers big-endian: the first byte is the high one. */
private fun ByteArray.unsignedShortAt(index: Int): Int = unsignedByteAt(index = index) * BYTE_VALUES + unsignedByteAt(index = index + 1)

private const val MARKER_PREFIX = 0xFF
private const val START_OF_IMAGE_MARKER = 0xD8

/** The start-of-image marker, then the first segment's 0xFF. */
private val JPEG_SIGNATURE = listOf(MARKER_PREFIX, START_OF_IMAGE_MARKER, MARKER_PREFIX)
private const val FIRST_SEGMENT = 2

/** SOF0, baseline: what most cameras write. */
private const val BASELINE_FRAME_MARKER = 0xC0

/** SOF2, progressive. The range from SOF0 to SOF2 also covers SOF1, extended. */
private const val PROGRESSIVE_FRAME_MARKER = 0xC2

/** In a frame header: 2 marker bytes, 2 length bytes and 1 precision byte, then the height and the width, 2 bytes each. */
private const val HEIGHT_OFFSET = 5
private const val WIDTH_OFFSET = 7
private const val FRAME_HEADER_LENGTH = 9
private const val BYTE_VALUES = 256
