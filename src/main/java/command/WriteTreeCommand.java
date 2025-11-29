package command;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.Deflater;

public class WriteTreeCommand implements Command{
    public void execute(String[] args) throws Exception {
        List<TreeEntry> entries = buildTreeEntries(Path.of("."));

        // CRITICAL: Sort by the EXACT byte string Git uses: "mode name"
        entries.sort(Comparator.comparing(e -> e.mode + " " + e.name));

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

//        Files.write(objPath, compressed.toByteArray(), StandardOpenOption.CREATE);
        Files.write(objPath, compressed.toByteArray());
        System.out.print(treeSha);
    }

    private static List<TreeEntry> buildTreeEntries(Path dir) throws Exception {
        List<TreeEntry> entries = new ArrayList<>();

        try (var stream = Files.list(dir)) {
            var children = stream
                    .filter(p -> !p.getFileName().toString().equals(".git"))
                    .toList();

            for (Path child : children) {
                String name = child.getFileName().toString();

//                if (Files.isDirectory(child)) {
//                    List<TreeEntry> subEntries = buildTreeEntries(child);
//                    if (subEntries.isEmpty()) continue;
//
//                    ByteArrayOutputStream subContent = new ByteArrayOutputStream();
//                    for (TreeEntry se : subEntries) {
//                        subContent.write((se.mode + " " + se.name).getBytes(StandardCharsets.UTF_8));
//                        subContent.write(0);
//                        subContent.write(se.shaBytes);
//                    }
//
//                    byte[] subData = subContent.toByteArray();
//                    String subHeader = "tree " + subData.length + "\0";
//                    byte[] fullSub = concat(subHeader.getBytes(StandardCharsets.UTF_8), subData);
//                    byte[] subSha = MessageDigest.getInstance("SHA-1").digest(fullSub);
//
//                    entries.add(new TreeEntry("40000", subSha, name));
                if (Files.isDirectory(child)) {
                    List<TreeEntry> subEntries = buildTreeEntries(child);

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

                    entries.add(new TreeEntry("40000", subSha, name));

                } else {
                    byte[] data = Files.readAllBytes(child);
                    String blobHeader = "blob " + data.length + "\0";
                    byte[] blobFull = concat(blobHeader.getBytes(StandardCharsets.UTF_8), data);
                    byte[] blobSha = MessageDigest.getInstance("SHA-1").digest(blobFull);

                    entries.add(new TreeEntry("100644", blobSha, name));
                }
            }
        }
        return entries;
    }

    record TreeEntry(String mode, byte[] shaBytes, String name) {
    }

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