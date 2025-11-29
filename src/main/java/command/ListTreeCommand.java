package command;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.Inflater;

public class ListTreeCommand implements Command {
    @Override
    public void execute(String[] args) throws Exception {
        boolean nameOnly = false;
        String treeSha = null;

        for (String arg : args) {
            if ("--name-only".equals(arg)) {
                nameOnly = true;
            } else if (treeSha == null) {
                treeSha = arg;
            } else {
                System.err.println("fatal: too many arguments");
                System.exit(128);
            }
        }

        if (treeSha == null) {
            System.err.println("fatal: tree sha required");
            System.exit(128);
        }

        String dir = treeSha.substring(0, 2);
        String file = treeSha.substring(2);
        Path objectPath = Path.of(".git/objects", dir, file);

        if (!Files.exists(objectPath)) {
            System.err.println("fatal: not a valid object name " + treeSha);
            System.exit(128);
        }

        // Decompress tree object
        byte[] compressed = Files.readAllBytes(objectPath);
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        while (!inflater.finished()) {
            int count = inflater.inflate(buffer);
            if (count == 0 && inflater.needsInput()) break;
            out.write(buffer, 0, count);
        }
        inflater.end();
        byte[] data = out.toByteArray();

        // Skip tree header: "tree <size>\0"
        int pos = 0;
        while (pos < data.length && data[pos] != 0) pos++;
        if (pos >= data.length) throw new RuntimeException("corrupted tree object");
        pos++; // Skip null byte

        // Parse tree entries
        while (pos < data.length) {
            // Read mode until space
            int spacePos = -1;
            for (int i = pos; i < data.length; i++) {
                if (data[i] == ' ') {
                    spacePos = i;
                    break;
                }
            }
            if (spacePos == -1) throw new RuntimeException("corrupted tree object");
            String mode = new String(data, pos, spacePos - pos, StandardCharsets.UTF_8);
            pos = spacePos + 1;

            // Read filename until NUL byte
            int nulPos = -1;
            for (int i = pos; i < data.length; i++) {
                if (data[i] == 0) {
                    nulPos = i;
                    break;
                }
            }
            if (nulPos == -1) throw new RuntimeException("corrupted tree object");
            String filename = new String(data, pos, nulPos - pos, StandardCharsets.UTF_8);
            pos = nulPos + 1;

            // Read 20-byte SHA-1
            if (pos + 20 > data.length) throw new RuntimeException("corrupted tree object");
            byte[] shaBytes = Arrays.copyOfRange(data, pos, pos + 20);
            pos += 20;

            // Output
            if (nameOnly) {
                System.out.println(filename); // Only filename for --name-only
            } else {
                String type = mode.startsWith("100") ? "blob" : "tree";
                StringBuilder shaHex = new StringBuilder();
                for (byte b : shaBytes) {
                    shaHex.append(String.format("%02x", b & 0xff));
                }
                System.out.printf("%s %s %s\t%s%n", mode, type, shaHex, filename);
            }
        }

    }
}