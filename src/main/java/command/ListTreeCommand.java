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
    public static void lsTree(String[] args) {
        boolean nameOnly = false;
        String treeSha = null;

        // Parse options
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
            System.err.println("usage: git ls-tree [--name-only] <tree sha>");
            System.exit(128);
        }

        // Resolve object path
        String dir = treeSha.substring(0, 2);
        String file = treeSha.substring(2);
        Path objectPath = Path.of(".git/objects", dir, file);

        if (!Files.exists(objectPath)) {
            System.err.println("fatal: not a valid object name " + treeSha);
            System.exit(128);
        }

        // Decompress tree object
        byte[] compressed = null;
        try {
            compressed = Files.readAllBytes(objectPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        while (!inflater.finished()) {
            int count = 0;
            try {
                count = inflater.inflate(buf);
            } catch (DataFormatException e) {
                throw new RuntimeException(e);
            }
            if (count == 0) break;
            out.write(buf, 0, count);
        }
        inflater.end();
        byte[] data = out.toByteArray();

        // Parse tree entries
        int pos = 0;
        while (pos < data.length) {
            // mode + space
            int spaceIdx = -1;
            for (int i = pos; i < data.length; i++) {
                if (data[i] == ' ') {
                    spaceIdx = i;
                    break;
                }
            }
            if (spaceIdx == -1) throw new RuntimeException("corrupted tree");

            String mode = new String(data, pos, spaceIdx - pos, StandardCharsets.UTF_8);
            pos = spaceIdx + 1;

            // filename until NUL
            int nulIdx = -1;
            for (int i = pos; i < data.length; i++) {
                if (data[i] == 0) {
                    nulIdx = i;
                    break;
                }
            }
            if (nulIdx == -1) throw new RuntimeException("corrupted tree");

            String name = new String(data, pos, nulIdx - pos, StandardCharsets.UTF_8);
            pos = nulIdx + 1;

            // skip 20-byte SHA-1
            if (pos + 20 > data.length) throw new RuntimeException("corrupted tree");
            pos += 20;

            // Output
            if (nameOnly) {
                System.out.println(name);               // <-- only filename + newline
            } else {
                String type = mode.startsWith("100") ? "blob" :
                        mode.startsWith("040") ? "tree" : "commit";

                StringBuilder shaHex = new StringBuilder();
                for (int j = pos - 20; j < pos; j++) {
                    shaHex.append(String.format("%02x", data[j] & 0xff));
                }

                System.out.printf("%s %s %s\t%s%n", mode, type, shaHex, name);
            }
        }
    }
}