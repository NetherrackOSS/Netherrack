package com.netherrack.example.hello;

import com.netherrack.api.command.Command;
import com.netherrack.api.command.CommandSender;
import com.netherrack.api.entity.Player;
import com.netherrack.api.form.SimpleForm;
import com.netherrack.api.permission.Permission;
import com.netherrack.api.permission.PermissionDefault;
import com.netherrack.api.plugin.PluginBase;
import com.netherrack.api.world.BlockPosition;
import com.netherrack.api.world.BlockSnapshot;
import com.netherrack.api.world.Location;

import java.util.Locale;

/**
 * A small plugin that shows the lifecycle callbacks, listeners, commands, a permission node, a
 * scheduled task and the player/world API.
 * <p>
 * The greeting is kept in the plugin's config.yml, so the first start writes a file that can be
 * edited without rebuilding the jar. The listener demonstrates the events a plugin can hear, and
 * /where reads a player's position, game mode and the block beneath them, then teleports or copies
 * that block when asked.
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

    /** Registered through plugin.yml; returning false would show the command's usage line. */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("where")) {
            return where(sender, args);
        }
        if (command.getName().equals("testform")) {
            return testForm(sender);
        }
        String target = args.length == 0 ? sender.getName() : String.join(" ", args);
        sender.sendMessage(greeting() + " " + target);
        return true;
    }

    /** /where [up|copy] shows where the player is; the options demonstrate teleporting and setBlock. */
    private boolean where(CommandSender sender, String[] args) {
        Player player = sender.getPlayer();
        if (player == null) {
            sender.sendMessage("Only players can use /where.");
            return true;
        }
        Location location = player.getLocation();
        if (args.length > 0 && args[0].equalsIgnoreCase("up")) {
            player.teleport(new Location(location.getX(), location.getY() + 5, location.getZ(),
                    location.getYaw(), location.getPitch()));
            location = player.getLocation();
        }
        BlockPosition feet = location.toBlockPosition();
        BlockPosition underfoot = new BlockPosition(feet.getX(), feet.getY() - 1, feet.getZ());
        BlockSnapshot below = player.getWorld().getBlock(underfoot);
        if (args.length > 0 && args[0].equalsIgnoreCase("copy")) {
            BlockPosition beside = new BlockPosition(underfoot.getX() + 1, underfoot.getY(), underfoot.getZ());
            player.getWorld().setBlock(beside, below);
            sender.sendMessage("Copied " + (below == null ? "air" : below) + " to " + beside + ".");
        }
        sender.sendMessage("Position: " + location + "; game mode: "
                + player.getGameMode().name().toLowerCase(Locale.ROOT));
        sender.sendMessage("Standing on: " + (below == null ? "air" : below));
        return true;
    }

    /** /testform opens the smallest possible Bedrock form: one title, one message, one button. */
    private boolean testForm(CommandSender sender) {
        Player player = sender.getPlayer();
        if (player == null) {
            sender.sendMessage("Only players can use /testform.");
            return true;
        }
        SimpleForm form = new SimpleForm("Test", "Test")
                .addButton("Test")
                .onSubmit((submitter, response) -> submitter.sendMessage(
                        "You clicked " + response.getButtonText() + "."))
                .onClose(submitter -> submitter.sendMessage("You closed the test form."));
        if (player.showForm(form) == -1) {
            sender.sendMessage("You already have a form open.");
        }
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
