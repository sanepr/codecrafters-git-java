package command;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.zip.Deflater;

public class HashObjectCommand {
    public static void hashObjectCommand(String[] args) {
        boolean writeToRepo = false;
        String filePath = null;

        int i = 1;
        while (i < args.length) {
            if ("-w".equals(args[i])) {
                writeToRepo = true;
                i++;
            } else {
                filePath = args[i];
                i++;
            }
        }

        if (filePath == null) {
            System.err.print("Usage: git hash-object [-w] <file>");
            System.exit(1);
        }

        try {
            byte[] fileContent = Files.readAllBytes(Path.of(filePath));

            String header = "blob " + fileContent.length + "\0";
            byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);

            byte[] fullContent = new byte[headerBytes.length + fileContent.length];
            System.arraycopy(headerBytes, 0, fullContent, 0, headerBytes.length);
            System.arraycopy(fileContent, 0, fullContent, headerBytes.length, fileContent.length);

            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] hashBytes = sha1.digest(fullContent);
            StringBuilder hashHex = new StringBuilder();

            for (byte b : hashBytes) {
                hashHex.append(String.format("%02x", b));
            }

            String sha = hashHex.toString();

            if (writeToRepo) {
                String dir = sha.substring(0, 2);
                String file = sha.substring(2);
                Path objectDir = Path.of(".git/objects", dir);
                Path objectFile = objectDir.resolve(file);

                Files.createDirectories(objectDir);

                Deflater deflater = new Deflater();
                deflater.setInput(fullContent);
                deflater.finish();

                ByteArrayOutputStream compressOut = new ByteArrayOutputStream();
                byte[] buffer = new byte[2014];

                while (!deflater.finished()) {
                    int count = deflater.deflate(buffer);
                    compressOut.write(buffer, 0, count);
                }

                deflater.end();

                Files.write(objectFile, compressOut.toByteArray());
            }

            System.out.print(sha);

        } catch (Exception e) {
            System.err.print("Fatal: could not hash object");
            System.exit(1);
        }
    }
}
