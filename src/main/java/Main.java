import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class Main {
    public static void main(String[] args) {
        // You can use print statements as follows for debugging, they'll be visible when running tests.
        System.err.println("Logs from your program will appear here!");

        final String command = args[0];

        switch (command) {
            case "init" -> {
                final File root = new File(".git");
                new File(root, "objects").mkdirs();
                new File(root, "refs").mkdirs();
                final File head = new File(root, "HEAD");

                try {
                    head.createNewFile();
                    Files.write(head.toPath(), "ref: refs/heads/main\n".getBytes());
                    System.out.println("Initialized git directory");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            case "cat-file" -> {
                final String objectHash = args[2];
                final String objectFolder = objectHash.substring(0, 2);
                final String objectFilename = objectHash.substring(2);

                try (Inflater inflater = new Inflater()) {
                    byte[] data = Files.readAllBytes(Paths.get(".git/objects/" + objectFolder + "/" + objectFilename));
                    inflater.setInput(data);

                    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length)) {
                        byte[] buffer = new byte[1024];
                        while (!inflater.finished()) {
                            int count = inflater.inflate(buffer);
                            outputStream.write(buffer, 0, count);
                        }

                        String decompressedString = outputStream.toString("UTF-8");
                        System.out.print(decompressedString.substring(decompressedString.indexOf("\0") + 1));

                    } catch (DataFormatException e) {
                        throw new RuntimeException(e);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }
            case "hash-object" -> {
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

                    String header = "blob" + fileContent.length + "\0";
                    byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);

                    byte[] fullContent = new byte[headerBytes.length + fileContent.length];
                    System.arraycopy(headerBytes, 0, fullContent, 0, headerBytes.length);
                    System.arraycopy(fileContent, 0, fullContent, headerBytes.length, fileContent.length);

                    MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
                    byte[] hashBytes = sha1.digest(fullContent);
                    StringBuilder hashHex = new StringBuilder();

                    for (byte b: hashBytes) {
                        hashHex.append(String.format("%02x", b));
                    }

                    String sha = hashHex.toString();

                    if (writeToRepo) {
                        String dir = sha.substring(0,2);
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
//                    e.printStackTrace(System.err);
                    System.exit(1);
                }
            }
            default -> System.out.println("Unknown command: " + command);
        }
    }
}
