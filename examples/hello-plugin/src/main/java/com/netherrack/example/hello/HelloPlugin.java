package com.netherrack.example.hello;

import com.netherrack.api.command.Command;
import com.netherrack.api.command.CommandSender;
import com.netherrack.api.permission.Permission;
import com.netherrack.api.permission.PermissionDefault;
import com.netherrack.api.plugin.PluginBase;

/**
 * A small plugin that shows the lifecycle callbacks, listeners, a command, a permission node and
 * a scheduled task.
 * <p>
 * The greeting is kept in the plugin's config.yml, so the first start writes a file that can be
 * edited without rebuilding the jar. The listener demonstrates the events a plugin can hear.
 */
public final class HelloPlugin extends PluginBase {

    private String greeting = "Hello from HelloPlugin!";

    @Override
    public void onLoad() {
        getLogger().info("HelloPlugin loaded");
    }

    @Override
    public void onEnable() {
        greeting = getConfig().get("greeting", greeting);
        getConfig().set("greeting", greeting);
        saveConfig();
        getLogger().info(greeting + " (running on " + getServer().getVersion() + ")");
        getServer().getPluginManager().registerEvents(new HelloListener(this), this);

        getPermissionManager().registerPermission(
                new Permission("hello.use", "Use /hello", PermissionDefault.ALLOW, this));
        getScheduler().runTask(this, () -> getLogger().info("HelloPlugin's delayed task ran."));
    }

    /** Registers through plugin.yml as /hello or /hi; returning false would show its usage line. */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String target = args.length == 0 ? sender.getName() : String.join(" ", args);
        sender.sendMessage(greeting() + " " + target);
        return true;
    }

    /** The configured greeting, used at startup, in chat and by /hello. */
    String greeting() {
        return greeting;
    }

    @Override
    public void onDisable() {
        getLogger().info("HelloPlugin disabled");
    }
}
