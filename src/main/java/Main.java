import command.*;

public class Main {
    public static void main(String[] args) {
        // You can use print statements as follows for debugging, they'll be visible when running tests.
        System.err.println("Logs from your program will appear here!");
        try {
            Git.run(args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

//
//        final String command = args[0];
//        try {
//            switch (command) {
//                case "init" -> InitCommand.initCommand();
//                case "cat-file" -> CatCommand.catFileCommand(args);
//                case "hash-object" -> HashObjectCommand.hashObjectCommand(args);
//                case "ls-tree" -> ListTreeCommand.lsTree(args);
//                case "write-tree" -> WriteTreeCommand.writeTree();
//                default -> System.out.println("Unknown command: " + command);
//            }
//        } catch (Exception ex) {
//            throw new RuntimeException(ex);
//        }
    }
}
