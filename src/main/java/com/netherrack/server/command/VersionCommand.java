package com.netherrack.server.command;

import com.netherrack.server.Netherrack;
import com.netherrack.server.VersionInfo;
import com.netherrack.server.setup.Lang;

import java.util.List;

public class VersionCommand implements Command {

    @Override
    public String getName() {
        return "version";
    }

    @Override
    public List<String> getAliases() {
        return List.of("ver");
    }

    /** Vanilla has no version command, so there's no description of it for the client to word. */
    @Override
    public String getDescription() {
        return Lang.current.get("command.description.version");
    }

    /**
     * Four lines: what the server is, its version and git commit, and the Java and operating
     * system it runs on. Sent as text, worded here, rather than as keys: a git commit is
     * often all digits, and a key's parameter that looks like a number is passed on as one,
     * which would drop a leading zero from it.
     */
    @Override
    public void execute(Netherrack server, CommandSender sender, String[] args) {
        sender.sendText(Lang.current.get("command.version.software", VersionInfo.NAME));
        sender.sendText(Lang.current.get("command.version.version", VersionInfo.VERSION, VersionInfo.GIT_COMMIT));
        sender.sendText(Lang.current.get("command.version.java", VersionInfo.JAVA_VERSION));
        sender.sendText(Lang.current.get("command.version.os", VersionInfo.OPERATING_SYSTEM));
    }
}
