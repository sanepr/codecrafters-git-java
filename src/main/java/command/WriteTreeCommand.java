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
    public  static void writeTree() throws Exception {
        List<TreeEntry> entries = new ArrayList<>();

        // Walk all files, but SKIP anything under .git/
        try (var stream = Files.walk(Path.of("."))) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> !p.toAbsolutePath().toString().contains("/.git/") &&
                            !p.toAbsolutePath().toString().endsWith(".git"))
                    .forEach(file -> {
                        try {
                            String relativePath = file.toString();
                            if (File.separatorChar != '/') {
                                relativePath = relativePath.replace(File.separatorChar, '/');
                            }

                            byte[] content = Files.readAllBytes(file);
                            String header = "blob " + content.length + "\0";
                            byte[] full = Bytes.concat(header.getBytes(StandardCharsets.UTF_8), content);

                            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
                            byte[] shaBytes = sha1.digest(full);
                            String shaHex = Bytes.toHex(shaBytes);

                            String mode = Files.isExecutable(file) ? "100755" : "100644";

                            // Use only the filename (not full path) in tree entry
                            String filename = file.getFileName().toString();
                            String parentDir = file.getParent() == null ? "" : file.getParent().toString();
                            if (parentDir.equals(".")) parentDir = "";

                            // For subdirectories, we need path like "sub/file.txt"
                            String treePath = parentDir.isEmpty() ? filename :
                                    (parentDir + "/" + filename).replace("./", "");

                            entries.add(new TreeEntry(mode, shaHex, treePath));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        }

        // Sort entries by full path (Git requires lexicographic order)
        entries.sort(Comparator.comparing(e -> e.path));

        // Build tree content: "mode name\0" + 20-byte SHA
        ByteArrayOutputStream treeData = new ByteArrayOutputStream();
        for (TreeEntry e : entries) {
            String name = e.path.contains("/") ?
                    e.path.substring(e.path.lastIndexOf("/") + 1) : e.path;

            treeData.write((e.mode + " " + name).getBytes(StandardCharsets.UTF_8));
            treeData.write(0);
            treeData.write(Bytes.fromHex(e.sha));
        }

        byte[] content = treeData.toByteArray();

        // Final tree object: "tree <size>\0" + content
        String header = "tree " + content.length + "\0";
        byte[] fullObject = Bytes.concat(header.getBytes(StandardCharsets.UTF_8), content);

        // Compute SHA-1
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        String treeSha = Bytes.toHex(sha1.digest(fullObject));

        // Write compressed object
        Path objPath = Path.of(".git/objects", treeSha.substring(0,2), treeSha.substring(2));
        Files.createDirectories(objPath.getParent());

        Deflater deflater = new Deflater();
        deflater.setInput(fullObject);
        deflater.finish();
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        while (!deflater.finished()) {
            int n = deflater.deflate(buf);
            compressed.write(buf, 0, n);
        }
        deflater.end();

        Files.write(objPath, compressed.toByteArray());

        System.out.print(treeSha);  // no newline!
    }

    // Helper record
    record TreeEntry(String mode, String sha, String path) {}

    // Helper class for byte operations
    static class Bytes {
        static byte[] concat(byte[] a, byte[] b) {
            byte[] c = new byte[a.length + b.length];
            System.arraycopy(a, 0, c, 0, a.length);
            System.arraycopy(b, 0, c, a.length, b.length);
            return c;
        }

        static String toHex(byte[] bytes) {
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        }

        static byte[] fromHex(String hex) {
            byte[] bytes = new byte[hex.length() / 2];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) Integer.parseInt(hex.substring(i*2, i*2+2), 16);
            }
            return bytes;
        }
    }
}
