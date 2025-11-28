package command;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public class CatCommand {
    public static void catFileCommand(String[] args) {
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
}
