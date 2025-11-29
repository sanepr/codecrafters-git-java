package command;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public class ListTreeCommand {
    public static void lsTree(String[] args) throws Exception {
        boolean nameOnly = false;
        String treeSha = null;

        // Parse arguments: support "--name-only" in any position
        for (int i = 1; i < args.length; i++) {
            if ("--name-only".equals(args[i])) {
                nameOnly = true;
            } else {
                if (treeSha != null) {
                    System.err.println("fatal: too many arguments");
                    System.exit(128);
                }
                treeSha = args[i];
            }
        }

        if (treeSha == null || treeSha.isEmpty()) {
            System.err.println("usage: git ls-tree [--name-only] <tree-sha>");
            System.exit(128);
        }

        // Resolve object
        String dir = treeSha.substring(0, 2);
        String file = treeSha.substring(2);
        Path objectPath = Path.of(".git/objects", dir, file);

        if (!Files.exists(objectPath)) {
            System.err.println("fatal: not a valid object name " + treeSha);
            System.exit(128);
        }

        // Decompress
        byte[] compressed = Files.readAllBytes(objectPath);
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        while (!inflater.finished()) {
            int count = inflater.inflate(buffer);
            if (count == 0) break;
            out.write(buffer, 0, count);
        }
        inflater.end();
        byte[] data = out.toByteArray();

        // Parse tree entries
        int pos = 0;
        while (pos < data.length) {
            // Read mode (e.g. "100644 ")
            int spaceIdx = indexOf(data, pos, (byte) ' ');
            if (spaceIdx == -1) throw new RuntimeException("corrupted tree object");

            // We don't need mode when --name-only
            pos = spaceIdx + 1;

            // Read filename until NUL
            int nulIdx = indexOf(data, pos, (byte) 0);
            if (nulIdx == -1) throw new RuntimeException("corrupted tree object");

            String name = new String(data, pos, nulIdx - pos, StandardCharsets.UTF_8);
            pos = nulIdx + 1;

            // Skip 20-byte SHA-1
            if (pos + 20 > data.length) throw new RuntimeException("corrupted tree object");
            pos += 20;

            // OUTPUT
            if (nameOnly) {
                System.out.println(name);                    // Only filename + newline
            } else {
                // Full format: mode type sha\tname
                String mode = new String(data, spaceIdx - 6, 6, StandardCharsets.UTF_8); // 6 digits
                String type = mode.startsWith("100") ? "blob" : "tree";

                StringBuilder shaHex = new StringBuilder();
                for (int i = pos - 20; i < pos; i++) {
                    shaHex.append(String.format("%02x", data[i] & 0xff));
                }

                System.out.printf("%s %s %s\t%s%n", mode, type, shaHex, name);
            }
        }
    }

    // Helper to avoid manual loops
    private static int indexOf(byte[] array, int start, byte target) {
        for (int i = start; i < array.length; i++) {
            if (array[i] == target) return i;
        }
        return -1;
    }
}