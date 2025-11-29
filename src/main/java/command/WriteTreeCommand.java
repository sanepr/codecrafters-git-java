package command;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.Deflater;

public class WriteTreeCommand {
    public static void writeTree() throws Exception {
        Path root = Path.of(".");
        List<TreeEntry> entries = new ArrayList<>();

        // Walk the filesystem and collect all regular files (skip .git)
        try (var stream = Files.walk(root)) {
            stream
                    .filter(Files::isRegularFile)
                    .filter(p -> !p.startsWith(".git"))
                    .forEach(file -> {
                        try {
                            String relativePath = root.relativize(file).toString();
                            if (File.separatorChar != '/') {
                                relativePath = relativePath.replace(File.separatorChar, '/');
                            }

                            byte[] content = Files.readAllBytes(file);
                            String header = "blob " + content.length + "\0";
                            byte[] full = concat(header.getBytes(StandardCharsets.UTF_8), content);

                            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
                            byte[] shaBytes = sha1.digest(full);
                            String shaHex = toHex(shaBytes);

                            // mode: 100644 for normal files, 100755 for executables
                            String mode = Files.isExecutable(file) ? "100755" : "100644";

                            entries.add(new TreeEntry(mode, shaHex, relativePath));
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to process file: " + file, e);
                        }
                    });
        }

        // Sort entries by path (Git requires this!)
        entries.sort(Comparator.comparing(e -> e.path));

        // Build tree object content
        ByteArrayOutputStream treeContent = new ByteArrayOutputStream();
        for (TreeEntry entry : entries) {
            String path = entry.path;
            String filename = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;

            // Write: "mode filename\0" + 20-byte SHA-1
            treeContent.write((entry.mode + " " + filename).getBytes(StandardCharsets.UTF_8));
            treeContent.write((byte) 0);
            treeContent.write(fromHex(entry.sha));
        }

        byte[] treeData = treeContent.toByteArray();

        // Compute final tree SHA-1
        String treeHeader = "tree " + treeData.length + "\0";
        byte[] fullTree = concat(treeHeader.getBytes(StandardCharsets.UTF_8), treeData);
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] treeShaBytes = sha1.digest(fullTree);
        String treeSha = toHex(treeShaBytes);

        // Write compressed object to .git/objects/
        String dir = treeSha.substring(0, 2);
        String file = treeSha.substring(2);
        Path objectPath = Path.of(".git/objects", dir, file);
        Files.createDirectories(objectPath.getParent());

        Deflater deflater = new Deflater();
        deflater.setInput(fullTree);
        deflater.finish();

        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        while (!deflater.finished()) {
            int count = deflater.deflate(buf);
            compressed.write(buf, 0, count);
        }
        deflater.end();

        Files.write(objectPath, compressed.toByteArray());

        // Print the SHA-1 (no newline!)
        System.out.print(treeSha);
    }

    // Helper record
    record TreeEntry(String mode, String sha, String path) {}

    // Helper: byte[] + byte[]
    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    // Helper: SHA hex → bytes
    private static byte[] fromHex(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    // Helper: bytes → hex string
    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
