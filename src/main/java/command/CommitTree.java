package command;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.zip.Deflater;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;


public class CommitTree implements Command {
    @Override
    public void execute(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("usage: git commit-tree <tree> -m <message> [-p <parent>...]");
            System.exit(1);
        }

        String treeSha = args[0];
        List<String> parentShas = new ArrayList<>();
        String message = null;

        for (int i = 1; i < args.length; i++) {
            if ("-p".equals(args[i])) {
                if (i + 1 >= args.length) {
                    System.err.println("fatal: -p requires a parent commit");
                    System.exit(1);
                }
                parentShas.add(args[++i]);
            } else if ("-m".equals(args[i])) {
                if (i + 1 >= args.length) {
                    System.err.println("fatal: -m requires a message");
                    System.exit(1);
                }
                message = args[++i];
            } else {
                System.err.println("fatal: unknown option: " + args[i]);
                System.exit(1);
            }
        }

        if (message == null || message.isEmpty()) {
            System.err.println("fatal: commit message required");
            System.exit(1);
        }

        // Build commit content
        StringBuilder content = new StringBuilder();
        content.append("tree ").append(treeSha).append("\n");

        for (String parent : parentShas) {
            content.append("parent ").append(parent).append("\n");
        }

        // Author/committer info (CodeCrafters accepts any valid format)
        String author = "user <user@example.com> " + (System.currentTimeMillis() / 1000) + " +0000";
        content.append("author ").append(author).append("\n");
        content.append("committer ").append(author).append("\n");
        content.append("\n");  // empty line before message
        content.append(message).append("\n");

        byte[] commitData = content.toString().getBytes(StandardCharsets.UTF_8);

        // Header: "commit <size>\0"
        String header = "commit " + commitData.length + "\0";
        byte[] fullObject = concat(header.getBytes(StandardCharsets.UTF_8), commitData);

        // Compute SHA-1
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] shaBytes = sha1.digest(fullObject);
        String commitSha = toHex(shaBytes);

        // Write compressed object
        String dir = commitSha.substring(0, 2);
        String file = commitSha.substring(2);
        Path objPath = Path.of(".git/objects", dir, file);
        Files.createDirectories(objPath.getParent());

        Deflater deflater = new Deflater();
        deflater.setInput(fullObject);
        deflater.finish();

        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        while (!deflater.finished()) {
            int count = deflater.deflate(buf);
            compressed.write(buf, 0, count);
        }
        deflater.end();

        Files.write(objPath, compressed.toByteArray());

        // Print the new commit SHA (no newline!)
        System.out.print(commitSha);
    }
    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }
}
