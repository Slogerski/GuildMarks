package pl.guildmark;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;

import java.awt.image.BufferedImage;
import java.util.concurrent.locks.LockSupport;

public final class WindowsClipboardImage {
    private static final int CF_DIB = 8;
    private static final int CF_HDROP = 15;
    private static final int CF_DIBV5 = 17;
    private static final int BI_RGB = 0;
    private static final int BI_BITFIELDS = 3;

    private interface User32 extends StdCallLibrary {
        User32 INSTANCE = Native.load("user32", User32.class);
        boolean OpenClipboard(Pointer owner);
        boolean CloseClipboard();
        boolean IsClipboardFormatAvailable(int format);
        Pointer GetClipboardData(int format);
    }

    private interface Kernel32 extends StdCallLibrary {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);
        Pointer GlobalLock(Pointer memory);
        boolean GlobalUnlock(Pointer memory);
        long GlobalSize(Pointer memory);
    }

    private interface Shell32 extends StdCallLibrary {
        Shell32 INSTANCE = Native.load("shell32", Shell32.class);
        int DragQueryFileW(Pointer drop, int fileIndex, char[] fileName, int fileNameLength);
    }

    private WindowsClipboardImage() {}

    public static BufferedImage readImage() {
        if (!isWindows() || !openClipboard()) return null;
        try {
            int format = User32.INSTANCE.IsClipboardFormatAvailable(CF_DIBV5) ? CF_DIBV5
                : User32.INSTANCE.IsClipboardFormatAvailable(CF_DIB) ? CF_DIB : 0;
            if (format == 0) return null;
            Pointer handle = User32.INSTANCE.GetClipboardData(format);
            if (handle == null) return null;
            Pointer data = Kernel32.INSTANCE.GlobalLock(handle);
            if (data == null) return null;
            try { return decodeDib(data, Kernel32.INSTANCE.GlobalSize(handle)); }
            finally { Kernel32.INSTANCE.GlobalUnlock(handle); }
        } finally { User32.INSTANCE.CloseClipboard(); }
    }

    public static String readFilePath() {
        if (!isWindows() || !openClipboard()) return null;
        try {
            if (!User32.INSTANCE.IsClipboardFormatAvailable(CF_HDROP)) return null;
            Pointer handle = User32.INSTANCE.GetClipboardData(CF_HDROP);
            if (handle == null) return null;
            int length = Shell32.INSTANCE.DragQueryFileW(handle, 0, null, 0);
            if (length <= 0 || length > 32_767) return null;
            char[] path = new char[length + 1];
            int written = Shell32.INSTANCE.DragQueryFileW(handle, 0, path, path.length);
            return written <= 0 ? null : new String(path, 0, written);
        } finally { User32.INSTANCE.CloseClipboard(); }
    }

    private static BufferedImage decodeDib(Pointer data, long memorySize) {
        if (memorySize < 40) return null;
        int headerSize = data.getInt(0), width = data.getInt(4), signedHeight = data.getInt(8);
        if (signedHeight == Integer.MIN_VALUE) return null;
        int height = Math.abs(signedHeight), bits = data.getShort(14) & 0xFFFF, compression = data.getInt(16);
        if (headerSize < 40 || headerSize > memorySize || width <= 0 || height <= 0 || width > 4096 || height > 4096 || (long)width * height > 16_000_000L) return null;
        if (!((bits == 24 || bits == 32) && (compression == BI_RGB || compression == BI_BITFIELDS))) return null;
        long pixelOffset = headerSize;
        if (headerSize == 40 && compression == BI_BITFIELDS) pixelOffset += 12;
        int colorsUsed = data.getInt(32);
        if (colorsUsed < 0) return null;
        if (colorsUsed > 0) pixelOffset += (long)colorsUsed * 4L;
        int stride = ((width * bits + 31) / 32) * 4;
        if (pixelOffset < 0 || pixelOffset + (long)stride * height > memorySize) return null;
        boolean bottomUp = signedHeight > 0;
        boolean hasAlpha = false;
        if (bits == 32) {
            for (int y = 0; y < height && !hasAlpha; y++) for (int x = 0; x < width; x++) {
                if ((data.getByte(pixelOffset + (long)y * stride + x * 4L + 3) & 0xFF) != 0) { hasAlpha = true; break; }
            }
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            int sourceY = bottomUp ? height - 1 - y : y;
            long row = pixelOffset + (long)sourceY * stride;
            for (int x = 0; x < width; x++) {
                long pixel = row + (long)x * (bits / 8);
                int b = data.getByte(pixel) & 0xFF, g = data.getByte(pixel + 1) & 0xFF, r = data.getByte(pixel + 2) & 0xFF;
                int a = bits == 32 && hasAlpha ? data.getByte(pixel + 3) & 0xFF : 255;
                image.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return image;
    }

    private static boolean openClipboard() {
        for (int attempt = 0; attempt < 5; attempt++) {
            if (User32.INSTANCE.OpenClipboard(Pointer.NULL)) return true;
            LockSupport.parkNanos(2_000_000L);
        }
        return false;
    }

    private static boolean isWindows() { return System.getProperty("os.name", "").toLowerCase().contains("win"); }
}
