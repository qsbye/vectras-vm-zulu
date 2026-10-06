package com.qsbye.alpinedesktop;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * 零依赖 tar.gz 解包器：支持常规文件、目录、符号链接、硬链接，
 * 兼容 ustar / GNU long name / pax 扩展头。
 */
public final class TarExtractor {

    public interface Callback {
        void onProgress(long compressedRead, long compressedTotal, String currentName);
    }

    private static final int BLOCK = 512;

    public static void extract(AssetManager assets, String assetName, File destDir, Callback cb)
            throws Exception {
        long total;
        try (AssetFileDescriptor afd = assets.openFd(assetName)) {
            total = afd.getLength();
        }

        Path dest = destDir.toPath().toAbsolutePath().normalize();
        Files.createDirectories(dest);

        try (CountingInputStream counting =
                     new CountingInputStream(new BufferedInputStream(
                             assets.open(assetName, AssetManager.ACCESS_STREAMING), 64 * 1024));
             GZIPInputStream gz = new GZIPInputStream(counting, 64 * 1024)) {

            String pendingName = null;
            String pendingLink = null;
            byte[] header = new byte[BLOCK];

            while (readFully(gz, header, BLOCK) == BLOCK) {
                if (isEmptyBlock(header)) {
                    break;
                }
                String baseName = readString(header, 0, 100);
                String prefix = readString(header, 345, 155);
                String name = (prefix.isEmpty() ? baseName : prefix + "/" + baseName);
                long size = parseOctal(header, 124, 12);
                char type = (char) header[156];
                String linkName = readString(header, 157, 100);

                // GNU long name / long link
                if (type == 'L' || type == 'K') {
                    byte[] body = new byte[(int) size];
                    readFully(gz, body, body.length);
                    skipPadding(gz, size);
                    String s = new String(body, StandardCharsets.UTF_8).replace("\0", "").trim();
                    if (type == 'L') {
                        pendingName = s;
                    } else {
                        pendingLink = s;
                    }
                    continue;
                }

                // pax 扩展头（含可能的 path/linkpath）——解析后跳过
                if (type == 'x' || type == 'g') {
                    byte[] body = new byte[(int) size];
                    readFully(gz, body, body.length);
                    skipPadding(gz, size);
                    String pax = new String(body, StandardCharsets.UTF_8);
                    for (String rec : pax.split("\n")) {
                        int sp = rec.indexOf(' ');
                        if (sp > 0) {
                            String kv = rec.substring(sp + 1);
                            int eq = kv.indexOf('=');
                            if (eq > 0) {
                                String key = kv.substring(0, eq);
                                String val = kv.substring(eq + 1);
                                if ("path".equals(key)) {
                                    pendingName = val;
                                } else if ("linkpath".equals(key)) {
                                    pendingLink = val;
                                }
                            }
                        }
                    }
                    continue;
                }

                if (pendingName != null) {
                    name = pendingName;
                    pendingName = null;
                }
                if (pendingLink != null) {
                    linkName = pendingLink;
                    pendingLink = null;
                }

                if (name.isEmpty() || name.equals(".") || name.equals("./")) {
                    skipEntry(gz, size);
                    continue;
                }

                Path target = resolve(dest, name);
                int mode = (int) parseOctal(header, 100, 8);

                switch (type) {
                    case '0':
                    case 0: {
                        Files.createDirectories(target.getParent());
                        try (InputStream in = new BoundedInputStream(gz, size)) {
                            Files.copy(in, target,
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                        setMode(target, mode);
                        break;
                    }
                    case '5': {
                        Files.createDirectories(target);
                        setMode(target, mode | 0700);
                        break;
                    }
                    case '2': {
                        Files.createDirectories(target.getParent());
                        Files.deleteIfExists(target);
                        Files.createSymbolicLink(target, Paths.get(linkName));
                        break;
                    }
                    case '1': {
                        Files.createDirectories(target.getParent());
                        Path src = resolve(dest, linkName);
                        Files.deleteIfExists(target);
                        try {
                            Files.createLink(target, src);
                        } catch (IOException ignore) {
                            // 个别跨目录硬链接失败不致命
                        }
                        break;
                    }
                    default:
                        // 设备节点等其它类型跳过
                        break;
                }

                skipPadding(gz, size);

                if (cb != null) {
                    cb.onProgress(counting.count, total, name);
                }
            }
        }
    }

    private static void setMode(Path path, int mode) {
        try {
            Set<PosixFilePermission> perms = new HashSet<>();
            int bits = mode & 0777;
            String s = String.format("%03o", bits);
            for (int i = 0; i < 3; i++) {
                int v = s.charAt(i) - '0';
                PosixFilePermission[] trip =
                        i == 0 ? new PosixFilePermission[]{
                                PosixFilePermission.OWNER_READ,
                                PosixFilePermission.OWNER_WRITE,
                                PosixFilePermission.OWNER_EXECUTE}
                        : i == 1 ? new PosixFilePermission[]{
                                PosixFilePermission.GROUP_READ,
                                PosixFilePermission.GROUP_WRITE,
                                PosixFilePermission.GROUP_EXECUTE}
                        : new PosixFilePermission[]{
                                PosixFilePermission.OTHERS_READ,
                                PosixFilePermission.OTHERS_WRITE,
                                PosixFilePermission.OTHERS_EXECUTE};
                if ((v & 4) != 0) perms.add(trip[0]);
                if ((v & 2) != 0) perms.add(trip[1]);
                if ((v & 1) != 0) perms.add(trip[2]);
            }
            Files.setPosixFilePermissions(path, perms);
        } catch (Exception ignore) {
            // 非 POSIX 文件系统或符号链接：忽略
        }
    }

    private static Path resolve(Path dest, String name) {
        String clean = name;
        while (clean.startsWith("./")) {
            clean = clean.substring(2);
        }
        while (clean.startsWith("/")) {
            clean = clean.substring(1);
        }
        Path p = dest.resolve(clean).normalize();
        if (!p.startsWith(dest)) {
            throw new SecurityException("非法归档路径: " + name);
        }
        return p;
    }

    private static void skipEntry(InputStream in, long size) throws IOException {
        long skip = size;
        while (skip > 0) {
            long n = in.skip(skip);
            if (n <= 0) {
                if (in.read() < 0) {
                    break;
                }
                n = 1;
            }
            skip -= n;
        }
        skipPadding(in, size);
    }

    private static void skipPadding(InputStream in, long size) throws IOException {
        long pad = (BLOCK - (size % BLOCK)) % BLOCK;
        while (pad-- > 0) {
            if (in.read() < 0) {
                break;
            }
        }
    }

    private static int readFully(InputStream in, byte[] buf, int len) throws IOException {
        int off = 0;
        while (off < len) {
            int n = in.read(buf, off, len - off);
            if (n < 0) {
                break;
            }
            off += n;
        }
        return off;
    }

    private static boolean isEmptyBlock(byte[] b) {
        for (byte x : b) {
            if (x != 0) {
                return false;
            }
        }
        return true;
    }

    private static String readString(byte[] buf, int off, int len) {
        int end = off;
        int limit = off + len;
        while (end < limit && buf[end] != 0) {
            end++;
        }
        return new String(buf, off, end - off, StandardCharsets.UTF_8).trim();
    }

    private static long parseOctal(byte[] buf, int off, int len) {
        long v = 0;
        for (int i = off; i < off + len; i++) {
            byte b = buf[i];
            if (b == 0 || b == ' ') {
                continue;
            }
            if (b >= '0' && b <= '7') {
                v = v * 8 + (b - '0');
            }
        }
        return v;
    }

    private static final class CountingInputStream extends FilterInputStream {
        long count;

        CountingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int n = super.read();
            if (n > 0) {
                count++;
            }
            return n;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) {
                count += n;
            }
            return n;
        }
    }

    private static final class BoundedInputStream extends InputStream {
        private final InputStream in;
        private long remain;

        BoundedInputStream(InputStream in, long remain) {
            this.in = in;
            this.remain = remain;
        }

        @Override
        public int read() throws IOException {
            if (remain <= 0) {
                return -1;
            }
            int n = in.read();
            if (n >= 0) {
                remain--;
            }
            return n;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remain <= 0) {
                return -1;
            }
            int n = in.read(b, off, (int) Math.min(len, remain));
            if (n > 0) {
                remain -= n;
            }
            return n;
        }
    }

    private TarExtractor() { }
}
