import command.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class Git {
    private static final Map<String, Supplier<Command>> COMMANDS = new HashMap<>();

    static {
        register("init",          InitCommand::new);
        register("hash-object",   HashObjectCommand::new);
        register("cat-file",      CatCommand::new);
        register("write-tree",    WriteTreeCommand::new);
        register("ls-tree",    ListTreeCommand::new);
        register("commit-tree",    CommitTree::new);
    }

    private static void register(String name, Supplier<Command> supplier) {
        COMMANDS.put(name, supplier);
    }

    public static void run(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: git <command> [<args>]");
            System.exit(1);
        }
        Command cmd = COMMANDS.getOrDefault(args[0], () -> {
            System.err.println("git: '" + args[0] + "' is not a git command.");
            System.exit(0);
            return null;
        }).get();

        cmd.execute(Arrays.copyOfRange(args, 1, args.length));
    }
}