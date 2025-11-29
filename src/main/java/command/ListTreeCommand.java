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

        // Parse flags and SHA
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

        // Load and decompress tree object
        String dir = treeSha.substring(0, 2);
        String file = treeSha.substring(2);
        Path objectPath = Path.of(".git/objects", dir, file);

        if (!Files.exists(objectPath)) {
            System.err.println("fatal: not a valid object name " + treeSha);
            System.exit(128);
        }

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
            // Skip mode: read until space
            int spacePos = -1;
            for (int i = pos; i < data.length; i++) {
                if (data[i] == ' ') {
                    spacePos = i;
                    break;
                }
            }
            if (spacePos == -1) throw new RuntimeException("corrupted tree");
            pos = spacePos + 1;  // skip space

            // Read filename until NUL byte
            int nulPos = -1;
            for (int i = pos; i < data.length; i++) {
                if (data[i] == 0) {
                    nulPos = i;
                    break;
                }
            }
            if (nulPos == -1) throw new RuntimeException("corrupted tree");

            String filename = new String(data, pos, nulPos - pos, StandardCharsets.UTF_8);
            pos = nulPos + 1;

            // Skip 20-byte SHA-1
            pos += 20;

            // OUTPUT: ONLY filename when --name-only
            if (nameOnly) {
                System.out.println(filename);
            } else {
                // Full format: mode type sha\tfilename
                String mode = new String(data, spacePos - 6, 6, StandardCharsets.UTF_8);
                String type = mode.startsWith("100") ? "blob" : "tree";

                StringBuilder shaHex = new StringBuilder();
                for (int i = pos - 20; i < pos; i++) {
                    shaHex.append(String.format("%02x", data[i] & 0xff));
                }

                System.out.printf("%s %s %s\t%s%n", mode, type, shaHex, filename);
            }
        }
    }
}