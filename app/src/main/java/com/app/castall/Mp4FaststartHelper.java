package com.app.castall;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Some MP4 files store their "moov" box (the index describing how to decode
 * the rest of the file) AFTER the "mdat" box (the actual compressed audio/
 * video bytes) instead of before it. That's valid MP4 and plays fine on
 * Chromecast/Android TV, but several Samsung DLNA TVs refuse to play such
 * files at all ("unsupported format") — their renderer apparently only
 * looks for moov near the start of the stream.
 *
 * Android's MediaMuxer has no option to control where it writes moov (it's
 * always written last), so fixing this means directly rewriting the file's
 * box layout: read moov's raw bytes, patch the byte offsets its sample
 * tables point to (since moving moov earlier shifts everything after it
 * forward by moov's own size), and write ftyp + patched-moov + mdat in that
 * order. This is the same technique tools like "qt-faststart" use — no
 * re-encoding, no quality loss, just rearranging + patching a small
 * metadata box.
 */
final class Mp4FaststartHelper {

    private static final String TAG = "Mp4FaststartHelper";

    // Box types whose body is itself a sequence of boxes, and therefore need
    // to be recursed into when looking for stco/co64 tables inside moov.
    private static final Set<String> CONTAINER_BOX_TYPES = new HashSet<>();
    static {
        CONTAINER_BOX_TYPES.add("moov");
        CONTAINER_BOX_TYPES.add("trak");
        CONTAINER_BOX_TYPES.add("mdia");
        CONTAINER_BOX_TYPES.add("minf");
        CONTAINER_BOX_TYPES.add("stbl");
        CONTAINER_BOX_TYPES.add("dinf");
        CONTAINER_BOX_TYPES.add("edts");
        CONTAINER_BOX_TYPES.add("mvex");
        CONTAINER_BOX_TYPES.add("udta");
    }

    private Mp4FaststartHelper() {
    }

    private static final class BoxInfo {
        final long offset;
        final long size;
        BoxInfo(long offset, long size) {
            this.offset = offset;
            this.size = size;
        }
    }

    /**
     * Reads just the top-level box headers (skipping over each box's body
     * without reading it) to figure out whether "moov" appears before
     * "mdat". Cheap even for very large files, since only a handful of
     * 8-16 byte headers are actually read.
     */
    static boolean needsRemux(Context context, Uri uri) {
        ContentResolver resolver = context.getContentResolver();
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) return false;
            long moovAt = -1;
            long mdatAt = -1;
            long position = 0;
            for (int boxesChecked = 0; boxesChecked < 20; boxesChecked++) {
                byte[] header = new byte[8];
                if (readFully(in, header, 8) < 8) break;
                long boxSize = readUInt32(header, 0);
                String boxType = new String(header, 4, 4, StandardCharsets.US_ASCII);
                long headerSize = 8;
                if (boxSize == 1) {
                    byte[] ext = new byte[8];
                    if (readFully(in, ext, 8) < 8) break;
                    boxSize = readUInt64(ext, 0);
                    headerSize = 16;
                } else if (boxSize == 0) {
                    if ("moov".equals(boxType) && moovAt < 0) moovAt = position;
                    if ("mdat".equals(boxType) && mdatAt < 0) mdatAt = position;
                    break;
                }
                if ("moov".equals(boxType) && moovAt < 0) moovAt = position;
                if ("mdat".equals(boxType) && mdatAt < 0) mdatAt = position;
                if (moovAt >= 0 && mdatAt >= 0) break;

                long toSkip = boxSize - headerSize;
                if (toSkip < 0 || !skipFully(in, toSkip)) break;
                position += boxSize;
            }
            if (moovAt < 0) return true; // couldn't find it nearby; safer to remux
            if (mdatAt < 0) return false;
            return mdatAt < moovAt;
        } catch (IOException e) {
            Log.w(TAG, "Could not inspect MP4 box order for " + uri, e);
            return false;
        }
    }

    /**
     * Rewrites {@code sourceUri} into {@code outputFile} with moov moved
     * before mdat. Must be called off the main thread. Throws IOException
     * (and leaves no usable output file) if the source doesn't match the
     * simple, common layout this can safely handle — callers should fall
     * back to casting the original file in that case.
     */
    static void remux(Context context, Uri sourceUri, java.io.File outputFile) throws IOException {
        ContentResolver resolver = context.getContentResolver();

        BoxInfo moov = null;
        BoxInfo mdat = null;
        long fileEnd = 0;
        try (InputStream in = resolver.openInputStream(sourceUri)) {
            if (in == null) throw new IOException("Could not open source for scanning");
            long position = 0;
            while (true) {
                byte[] header = new byte[8];
                int read = readFully(in, header, 8);
                if (read < 8) break;
                long boxSize = readUInt32(header, 0);
                String boxType = new String(header, 4, 4, StandardCharsets.US_ASCII);
                long headerSize = 8;
                if (boxSize == 1) {
                    byte[] ext = new byte[8];
                    if (readFully(in, ext, 8) < 8) break;
                    boxSize = readUInt64(ext, 0);
                    headerSize = 16;
                } else if (boxSize == 0) {
                    // Extends to EOF — only sensible for the last box.
                    if ("moov".equals(boxType)) moov = new BoxInfo(position, -1);
                    if ("mdat".equals(boxType)) mdat = new BoxInfo(position, -1);
                    break;
                }
                if ("moov".equals(boxType)) {
                    if (moov != null) throw new IOException("Multiple moov boxes; not attempting remux");
                    moov = new BoxInfo(position, boxSize);
                }
                if ("mdat".equals(boxType)) {
                    if (mdat != null) throw new IOException("Multiple mdat boxes; not attempting remux");
                    mdat = new BoxInfo(position, boxSize);
                }
                long toSkip = boxSize - headerSize;
                if (toSkip < 0 || !skipFully(in, toSkip)) break;
                position += boxSize;
                fileEnd = position;
            }
        }

        if (moov == null || mdat == null) {
            throw new IOException("Could not locate both moov and mdat boxes");
        }
        if (moov.size < 0 || mdat.offset >= moov.offset) {
            throw new IOException("File layout not eligible for remux (already ok, or box extends to EOF)");
        }

        // Read moov's raw bytes fully into memory — it's metadata only,
        // typically a few KB to low MB even for long videos.
        byte[] moovBytes = new byte[(int) moov.size];
        try (InputStream in = resolver.openInputStream(sourceUri)) {
            if (in == null) throw new IOException("Could not reopen source to read moov");
            if (!skipFully(in, moov.offset)) throw new IOException("Could not seek to moov");
            if (readFully(in, moovBytes, moovBytes.length) < moovBytes.length) {
                throw new IOException("Could not read full moov box");
            }
        }

        // Moving moov to before mdat shifts mdat (and anything else that
        // was already after the insertion point) forward by moov's size.
        long shiftAmount = moov.size;
        boolean patched = patchChunkOffsets(moovBytes, 8, moovBytes.length, shiftAmount);
        if (!patched) {
            throw new IOException("Failed to patch chunk offset tables safely");
        }

        try (InputStream in = resolver.openInputStream(sourceUri);
             OutputStream out = new java.io.FileOutputStream(outputFile)) {
            if (in == null) throw new IOException("Could not reopen source to write output");

            byte[] buffer = new byte[1 << 20]; // 1 MB

            // 1) Everything before mdat, unchanged (ftyp, and any small
            //    boxes like free/wide that came before it).
            copyExact(in, out, buffer, mdat.offset);

            // 2) Patched moov, inserted here.
            out.write(moovBytes);

            // 3) mdat itself, plus anything else that was between mdat and
            //    moov's original position, unchanged, streamed in chunks
            //    so large videos never need to fit in memory at once.
            long middleLength = moov.offset - mdat.offset;
            copyExact(in, out, buffer, middleLength);

            // 4) Anything after moov's original position (rare — usually
            //    nothing, since moov is normally the last box).
            long afterMoov = fileEnd - (moov.offset + moov.size);
            if (afterMoov > 0) {
                copyExact(in, out, buffer, afterMoov);
            }
        }
    }

    /** Copies exactly {@code length} bytes from {@code in} to {@code out}. */
    private static void copyExact(InputStream in, OutputStream out, byte[] buffer, long length) throws IOException {
        long remaining = length;
        while (remaining > 0) {
            int chunk = (int) Math.min(buffer.length, remaining);
            int read = in.read(buffer, 0, chunk);
            if (read < 0) throw new IOException("Unexpected end of source while copying");
            out.write(buffer, 0, read);
            remaining -= read;
        }
    }

    /**
     * Recursively walks boxes in {@code buf[start, end)}, descending into
     * known container types, and adds {@code shiftAmount} to every offset
     * value found in stco/co64 tables it finds along the way.
     *
     * @return false if a patched 32-bit (stco) offset would overflow, in
     *         which case the caller should abort rather than write a
     *         corrupt file.
     */
    private static boolean patchChunkOffsets(byte[] buf, int start, int end, long shiftAmount) {
        int pos = start;
        while (pos + 8 <= end) {
            long boxSize = readUInt32(buf, pos);
            String boxType = new String(buf, pos + 4, 4, StandardCharsets.US_ASCII);
            int headerSize = 8;
            long actualSize = boxSize;
            if (boxSize == 1) {
                if (pos + 16 > end) return false;
                actualSize = readUInt64(buf, pos + 8);
                headerSize = 16;
            } else if (boxSize == 0) {
                actualSize = end - pos; // extends to end of parent
            }
            if (actualSize < headerSize || pos + actualSize > end) {
                return false; // malformed relative to what we expect; bail out safely
            }
            int contentStart = pos + headerSize;
            int contentEnd = (int) (pos + actualSize);

            if (CONTAINER_BOX_TYPES.contains(boxType)) {
                if (!patchChunkOffsets(buf, contentStart, contentEnd, shiftAmount)) return false;
            } else if ("stco".equals(boxType)) {
                // version(1) + flags(3) + entry_count(4) + entries(4 bytes each)
                if (contentStart + 8 > contentEnd) return false;
                long entryCount = readUInt32(buf, contentStart + 4);
                int entriesStart = contentStart + 8;
                for (long i = 0; i < entryCount; i++) {
                    int entryPos = (int) (entriesStart + i * 4);
                    if (entryPos + 4 > contentEnd) return false;
                    long original = readUInt32(buf, entryPos);
                    long shifted = original + shiftAmount;
                    if (shifted > 0xFFFFFFFFL) return false; // would overflow 32-bit field
                    writeUInt32(buf, entryPos, shifted);
                }
            } else if ("co64".equals(boxType)) {
                if (contentStart + 8 > contentEnd) return false;
                long entryCount = readUInt32(buf, contentStart + 4);
                int entriesStart = contentStart + 8;
                for (long i = 0; i < entryCount; i++) {
                    int entryPos = (int) (entriesStart + i * 8);
                    if (entryPos + 8 > contentEnd) return false;
                    long original = readUInt64(buf, entryPos);
                    writeUInt64(buf, entryPos, original + shiftAmount);
                }
            }
            pos += actualSize;
        }
        return true;
    }

    private static int readFully(InputStream in, byte[] buffer, int length) throws IOException {
        int total = 0;
        while (total < length) {
            int read = in.read(buffer, total, length - total);
            if (read < 0) break;
            total += read;
        }
        return total;
    }

    private static boolean skipFully(InputStream in, long length) throws IOException {
        long remaining = length;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped <= 0) {
                // Some stream implementations can return 0 from skip() near
                // EOF without actually being at EOF; fall back to reading.
                if (in.read() < 0) return false;
                skipped = 1;
            }
            remaining -= skipped;
        }
        return true;
    }

    private static long readUInt32(byte[] b, int offset) {
        return ((b[offset] & 0xFFL) << 24) | ((b[offset + 1] & 0xFFL) << 16)
                | ((b[offset + 2] & 0xFFL) << 8) | (b[offset + 3] & 0xFFL);
    }

    private static long readUInt64(byte[] b, int offset) {
        return (readUInt32(b, offset) << 32) | readUInt32(b, offset + 4);
    }

    private static void writeUInt32(byte[] b, int offset, long value) {
        b[offset] = (byte) ((value >> 24) & 0xFF);
        b[offset + 1] = (byte) ((value >> 16) & 0xFF);
        b[offset + 2] = (byte) ((value >> 8) & 0xFF);
        b[offset + 3] = (byte) (value & 0xFF);
    }

    private static void writeUInt64(byte[] b, int offset, long value) {
        writeUInt32(b, offset, value >>> 32);
        writeUInt32(b, offset + 4, value & 0xFFFFFFFFL);
    }
}
