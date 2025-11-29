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
        List<TreeEntry> entries = buildTreeEntries(root, root);

        // Sort: directories first, then by name
        entries.sort((a, b) -> {
            if (a.isDir != b.isDir) {
                return a.isDir ? -1 : 1;
            }
            return a.name.compareTo(b.name);
        });

        // Build tree content: "mode name\0sha"
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        for (TreeEntry e : entries) {
            content.write((e.mode + " " + e.name).getBytes(StandardCharsets.UTF_8));
            content.write(0);
            content.write(e.shaBytes);
        }

        byte[] treeData = content.toByteArray();
        String header = "tree " + treeData.length + "\0";
        byte[] fullObject = concat(header.getBytes(StandardCharsets.UTF_8), treeData);

        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        String treeSha = toHex(sha1.digest(fullObject));

        // Write to .git/objects/
        Path objPath = Path.of(".git/objects", treeSha.substring(0, 2), treeSha.substring(2));
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
        System.out.print(treeSha);
    }

    // Recursive builder — this is the magic
    private static List<TreeEntry> buildTreeEntries(Path root, Path currentDir) throws Exception {
        List<TreeEntry> result = new ArrayList<>();

        try (var files = Files.list(currentDir)) {
            var children = files
                    .filter(p -> !p.getFileName().toString().equals(".git"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();

            for (Path child : children) {
                String name = child.getFileName().toString();

                if (Files.isDirectory(child)) {
                    List<TreeEntry> subEntries = buildTreeEntries(root, child);
                    if (!subEntries.isEmpty()) {
                        // Build subtree object
                        ByteArrayOutputStream subContent = new ByteArrayOutputStream();
                        for (TreeEntry se : subEntries) {
                            subContent.write((se.mode + " " + se.name).getBytes(StandardCharsets.UTF_8));
                            subContent.write(0);
                            subContent.write(se.shaBytes);
                        }
                        byte[] subData = subContent.toByteArray();
                        String subHeader = "tree " + subData.length + "\0";
                        byte[] fullSub = concat(subHeader.getBytes(StandardCharsets.UTF_8), subData);
                        byte[] subSha = MessageDigest.getInstance("SHA-1").digest(fullSub);
                        result.add(new TreeEntry("40000", subSha, name, true));
                    }
                } else {
                    byte[] data = Files.readAllBytes(child);
                    String blobHeader = "blob " + data.length + "\0";
                    byte[] blobFull = concat(blobHeader.getBytes(StandardCharsets.UTF_8), data);
                    byte[] blobSha = MessageDigest.getInstance("SHA-1").digest(blobFull);
                    String mode = Files.isExecutable(child) ? "100755" : "100644";
                    result.add(new TreeEntry(mode, blobSha, name, false));
                }
            }
        }
        return result;
    }

    // Record to hold entry data
    record TreeEntry(String mode, byte[] shaBytes, String name, boolean isDir) {
        TreeEntry(String mode, byte[] shaBytes, String name) {
            this(mode, shaBytes, name, false);
        }
    }

    // Helper methods
    private static byte[] concat(byte[] a, byte[] b) {
        byte[] c = new byte[a.length + b.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xFF));
        return sb.toString();
    }
}
