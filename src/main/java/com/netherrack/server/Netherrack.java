package com.netherrack.server;

import com.netherrack.server.command.AvailableCommands;
import com.netherrack.server.command.CommandManager;
import com.netherrack.server.command.ConsoleCommandSender;
import com.netherrack.server.command.DeopCommand;
import com.netherrack.server.command.GamemodeCommand;
import com.netherrack.server.command.HelpCommand;
import com.netherrack.server.command.OpCommand;
import com.netherrack.server.command.StopCommand;
import com.netherrack.server.command.VersionCommand;
import com.netherrack.server.entity.ItemEntities;
import com.netherrack.server.network.RakNetServer;
import com.netherrack.server.player.GameModes;
import com.netherrack.server.player.Permission;
import com.netherrack.server.player.Permissions;
import com.netherrack.server.player.Player;
import com.netherrack.server.player.PlayerManager;
import com.netherrack.server.setup.Lang;
import com.netherrack.server.setup.SetupWizard;
import com.netherrack.server.util.AnsiSupport;
import com.netherrack.server.util.Logger;
import com.netherrack.server.util.NettyLogBridge;
import com.netherrack.server.world.World;
import com.netherrack.server.world.WorldManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import org.cloudburstmc.protocol.bedrock.data.GameType;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Netherrack {

    public static final String VERSION = "0.1.0";

    /** 20 ticks a second. */
    private static final long TICK_MILLIS = 50;

    private final ServerConfig config;
    private final RakNetServer rakNetServer;
    private final PlayerManager playerManager = new PlayerManager();
    private final WorldManager worldManager;
    private final CommandManager commandManager;
    private World world;
    private ItemEntities itemEntities;
    private Permissions permissions;
    private ScheduledExecutorService ticker;
    private volatile boolean running = true;

    public Netherrack() {
        this.config = new ServerConfig(Path.of("server.properties"));
        this.rakNetServer = new RakNetServer(config);
        this.worldManager = new WorldManager();

        this.commandManager = new CommandManager();
        commandManager.register(new StopCommand());
        commandManager.register(new VersionCommand());
        commandManager.register(new HelpCommand());
        commandManager.register(new GamemodeCommand());
        commandManager.register(new OpCommand());
        commandManager.register(new DeopCommand());
    }

    public ServerConfig getConfig() {
        return config;
    }

    public PlayerManager getPlayerManager() {
        return playerManager;
    }

    public Permissions getPermissions() {
        return permissions;
    }

    /**
     * The command level operators get: op-permission-level from server.properties, 0 to 4.
     * See Command.getPermissionLevel() - at 4, operators may also stop the server.
     */
    public int getOpPermissionLevel() {
        return Math.max(0, Math.min(4, config.getInt("op-permission-level", 2)));
    }

    /** The command level that goes with a permission. */
    public int commandLevelFor(Permission permission) {
        return permission == Permission.OPERATOR ? getOpPermissionLevel() : 0;
    }

    /**
     * Gives an online player a permission, telling their client what they may now do -
     * including which commands to suggest.
     */
    public void applyPermission(Player player, Permission permission) {
        player.setPermission(permission, commandLevelFor(permission));
        player.sendAbilities();
        player.getSession().sendPacket(AvailableCommands.forLevel(commandManager, player.getCommandLevel()));
    }

    public ItemEntities getItemEntities() {
        return itemEntities;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public static void main(String[] args) {
        NettyLogBridge.install();
        AnsiSupport.init();
        new Netherrack().start();
    }

    private void start() {
        Logger.openLogFile(Path.of("logs", "latest.log"));

        // Printed before a language is known either way, same as the wizard's own first
        // screen - there's nothing to translate it into yet.
        Logger.info("Starting Netherrack server version " + VERSION);

        String language = Lang.DEFAULT_CODE;
        if (!Files.exists(Path.of("server.properties"))) {
            Logger.info("First-time setup detected. Launching setup wizard...");
            String chosen = new SetupWizard().run();
            if (chosen == null) {
                Logger.error("Setup wizard did not accept the license. Startup has been cancelled.");
                System.exit(1);
                return;
            }
            language = chosen;
        }

        // Set before config.load() too, so messages like "server.properties not found"
        // (logged from inside that call, on a first run) are already in the right language.
        Lang.current = new Lang(language);

        config.load(language);
        Lang.current = new Lang(config.get("language", language));
        Lang lang = Lang.current;

        permissions = Permissions.load(Permissions.FILE, defaultPermission());

        NettyLogBridge.setDebug(config.getBoolean("debug", false));
        if (NettyLogBridge.isDebug()) {
            Logger.warn(lang.get("server.debug_enabled"));
        }

        String ip = config.get("server-ip", "0.0.0.0");
        int port = config.getInt("server-port", 19132);
        String levelName = config.get("level-name", "world");
        int maxPlayers = config.getInt("max-players", 20);

        sleep(150);
        Logger.info(lang.get("server.preparing_level", levelName));
        world = worldManager.loadOrCreate(levelName);
        world.getChunk(0, 0);

        sleep(150);
        Logger.info(lang.get("server.opening", ip, port));
        itemEntities = new ItemEntities(world, playerManager);
        startTicking();
        rakNetServer.start(world, playerManager, this);

        sleep(150);
        Logger.info(lang.get("server.early_build"));

        sleep(300);
        Logger.info(lang.get("server.preparing_spawn"));

        sleep(300);
        Logger.info(lang.get("server.thread_running"));
        Logger.info(lang.get("server.done", maxPlayers));

        runConsoleLoop();
    }

    private void runConsoleLoop() {
        Logger.bindConsoleThread(Thread.currentThread());
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            while (running) {
                System.out.print("> ");
                System.out.flush();
                Logger.notePromptShown();
                String line = reader.readLine();
                Logger.notePromptConsumed();
                if (line == null) {
                    continue;
                }
                commandManager.dispatch(this, ConsoleCommandSender.INSTANCE, line.trim());
            }
        } catch (Exception e) {
            Logger.error("Console loop terminated: " + e.getMessage());
        }
    }

    public void shutdown() {
        Logger.info(Lang.current.get("server.stopping"));
        sleep(200);
        Logger.info(Lang.current.get("server.saving_level", config.get("level-name", "world")));
        rakNetServer.stop();
        ticker.shutdownNow();
        sleep(200);
        Logger.info(Lang.current.get("server.closed"));
        running = false;
        System.exit(0);
    }

    /** The game mode players join in, and "default" stands for: "gamemode" in server.properties. */
    public GameType getDefaultGameMode() {
        String configured = config.get("gamemode", "survival");
        GameType gameMode = GameModes.parse(configured);
        if (gameMode == null) {
            Logger.warn("Unknown gamemode \"" + configured + "\" in server.properties, using survival.");
            return GameType.SURVIVAL;
        }
        return gameMode;
    }

    /** default-player-permission-level from server.properties: what players without a permissions.json entry get. */
    private Permission defaultPermission() {
        String configured = config.get("default-player-permission-level", "member");
        Permission permission = Permission.fromName(configured);
        if (permission == null) {
            Logger.warn("Unknown default-player-permission-level \"" + configured
                    + "\" in server.properties, using member. Expected visitor, member or operator.");
            return Permission.MEMBER;
        }
        return permission;
    }

    /**
     * Runs the world 20 times a second, as vanilla does: everything that changes on its
     * own over time, such as dropped items falling and being picked up. A tick that throws
     * is logged and the next one runs anyway - an uncaught exception would otherwise stop
     * the schedule for good.
     */
    private void startTicking() {
        ticker = Executors.newSingleThreadScheduledExecutor(runnable -> new Thread(runnable, "Server tick"));
        ticker.scheduleAtFixedRate(() -> {
            try {
                itemEntities.tick();
            } catch (Exception e) {
                Logger.error("Error during the server tick: " + e);
            }
        }, TICK_MILLIS, TICK_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
