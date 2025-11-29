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
        Path root = Path.of(".");
        List<TreeEntry> entries = buildTreeEntries(root, root);

        // Sort entries: directories first, then files (Git requires this order!)
        entries.sort((a, b) -> {
            boolean aIsDir = a.path.endsWith("/");
            boolean bIsDir = b.path.endsWith("/");
            if (aIsDir && !bIsDir) return -1;
            if (!aIsDir && bIsDir) return 1;
            return a.path.compareTo(b.path);
        });

        // Build tree content
        ByteArrayOutputStream treeContent = new ByteArrayOutputStream();
        for (TreeEntry e : entries) {
            String name = e.path.contains("/") ? e.path.substring(e.path.lastIndexOf("/", e.path.length() - 2) + 1) : e.path;
            if (e.isDirectory) name = name.substring(0, name.length() - 1); // remove trailing '/'

            treeContent.write((e.mode + " " + name).getBytes(StandardCharsets.UTF_8));
            treeContent.write(0);
            treeContent.write(e.shaBytes);
        }

        byte[] content = treeContent.toByteArray();
        String header = "tree " + content.length + "\0";
        byte[] full = concat(header.getBytes(StandardCharsets.UTF_8), content);

        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        String treeSha = toHex(sha1.digest(full));

        // Write object
        Path objPath = Path.of(".git/objects", treeSha.substring(0,2), treeSha.substring(2));
        Files.createDirectories(objPath.getParent());

        Deflater deflater = new Deflater();
        deflater.setInput(full);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        while (!deflater.finished()) {
            int n = deflater.deflate(buf);
            out.write(buf, 0, n);
        }
        deflater.end();
        Files.write(objPath, out.toByteArray());

        System.out.print(treeSha);
    }

    // Recursively build tree entries (this is the key!)
    private static List<TreeEntry> buildTreeEntries(Path root, Path dir) throws Exception {
        List<TreeEntry> entries = new ArrayList<>();

        try (var stream = Files.list(dir)) {
            List<Path> children = stream
                    .filter(p -> !p.getFileName().toString().equals(".git"))
                    .sorted() // important for consistent order
                    .toList();

            for (Path child : children) {
                String relPath = root.relativize(child).toString().replace(File.separatorChar, '/');
                if (Files.isDirectory(child)) {
                    relPath += "/";
                    List<TreeEntry> subEntries = buildTreeEntries(root, child);
                    // Build subtree object
                    ByteArrayOutputStream subContent = new ByteArrayOutputStream();
                    for (TreeEntry se : subEntries) {
                        String name = se.path.substring(se.path.lastIndexOf("/", se.path.length() - 2) + 1);
                        if (se.isDirectory) name = name.substring(0, name.length() - 1);
                        subContent.write((se.mode + " " + name).getBytes(StandardCharsets.UTF_8));
                        subContent.write(0);
                        subContent.write(se.shaBytes);
                    }
                    byte[] subData = subContent.toByteArray();
                    String subHeader = "tree " + subData.length + "\0";
                    byte[] fullSub = concat(subHeader.getBytes(StandardCharsets.UTF_8), subData);
                    byte[] subSha = MessageDigest.getInstance("SHA-1").digest(fullSub);
                    entries.add(new TreeEntry("40000", subSha, relPath, true));
                } else {
                    byte[] data = Files.readAllBytes(child);
                    String header = "blob " + data.length + "\0";
                    byte[] full = concat(header.getBytes(StandardCharsets.UTF_8), data);
                    byte[] sha = MessageDigest.getInstance("SHA-1").digest(full);
                    String mode = Files.isExecutable(child) ? "100755" : "100644";
                    entries.add(new TreeEntry(mode, sha, relPath, false));
                }
            }
        }
        return entries;
    }

    record TreeEntry(String mode, byte[] shaBytes, String path, boolean isDirectory) {
        TreeEntry(String mode, byte[] shaBytes, String path) {
            this(mode, shaBytes, path, false);
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
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }
}
