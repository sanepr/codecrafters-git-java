package command;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.Inflater;

public class ListTreeCommand {
    public static void lsTree(String[] args)  {
        if (args.length != 2) {
            System.err.println("usage: git ls-tree <tree sha>");
            System.exit(0);
        }

        String treeSha = args[1];

        // Validate SHA length (full 40 chars or short)
        if (!treeSha.matches("^[0-9a-fA-F]{4,40}$")) {
            System.err.println("fatal: not a valid object name " + treeSha);
            System.exit(128);
        }

        String dir = treeSha.substring(0, 2);
        String file = treeSha.substring(2);
        Path objectPath = Path.of(".git/objects", dir, file);

        if (!Files.exists(objectPath)) {
            System.err.println("fatal: not a valid object name " + treeSha);
            System.exit(128);
        }

        try (Inflater inflater = new Inflater()) {
            // Decompress the tree object
            byte[] compressed = Files.readAllBytes(objectPath);
            ;
            inflater.setInput(compressed);
            ByteArrayOutputStream decompressed = new ByteArrayOutputStream();

            byte[] buffer = new byte[1024];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0) break;
                decompressed.write(buffer, 0, count);
            }
            inflater.end();

            byte[] treeData = decompressed.toByteArray();

            // Parse tree object: entries are: "mode path\0sha1"
            int pos = 0;
            while (pos < treeData.length) {
                // Read mode (e.g., "100644 ")
                int spaceIdx = -1;
                for (int i = pos; i < treeData.length; i++) {
                    if (treeData[i] == ' ') {
                        spaceIdx = i;
                        break;
                    }
                }
                if (spaceIdx == -1) throw new RuntimeException("Malformed tree");

                String mode = new String(treeData, pos, spaceIdx - pos, StandardCharsets.UTF_8);
                pos = spaceIdx + 1;

                // Read path until null byte
                int nullIdx = -1;
                for (int i = pos; i < treeData.length; i++) {
                    if (treeData[i] == 0) {
                        nullIdx = i;
                        break;
                    }
                }
                if (nullIdx == -1) throw new RuntimeException("Malformed tree");

                String path = new String(treeData, pos, nullIdx - pos, StandardCharsets.UTF_8);
                pos = nullIdx + 1;

                // Read 20-byte SHA-1 (binary)
                if (pos + 20 > treeData.length) throw new RuntimeException("Malformed tree");
                byte[] shaBytes = Arrays.copyOfRange(treeData, pos, pos + 20);
                pos += 20;

                // Convert SHA-1 bytes to hex
                StringBuilder shaHex = new StringBuilder();
                for (byte b : shaBytes) {
                    shaHex.append(String.format("%02x", b));
                }

                // Determine type from mode
                String type = mode.startsWith("100") ? "blob" :
                        mode.startsWith("040") ? "tree" : "commit";

                // Output: mode type sha    path
                System.out.printf("%s %s %s\t%s%n", mode, type, shaHex.toString(), path);
            }
        } catch (Exception e) {
            System.err.print("Fatal: could not tree object");
            System.exit(0);
        }
    }
}
