package net.aquiles.carryme;

import net.aquiles.carryme.command.AliasTabCompleteListenerRegistrar;
import net.aquiles.carryme.command.CarryCommandHandler;
import net.aquiles.carryme.compat.CompatibilityManager;
import net.aquiles.carryme.service.CarryRequestService;
import net.aquiles.carryme.service.MessageService;
import net.aquiles.carryme.service.UpdateChecker;
import net.aquiles.carryme.util.DismountListenerRegistrar;
import net.aquiles.carryme.util.PassengerAdapter;
import net.aquiles.carryme.util.PlatformScheduler;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class CarryMe extends JavaPlugin {

    private static final int BSTATS_PLUGIN_ID = 29813;

    private CarryRequestService carryRequestService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        MessageService messageService = new MessageService(this);
        PlatformScheduler platformScheduler = new PlatformScheduler(this);
        PassengerAdapter passengerAdapter = new PassengerAdapter();
        CompatibilityManager compatibilityManager = new CompatibilityManager(this);
        carryRequestService = new CarryRequestService(messageService, compatibilityManager, platformScheduler, passengerAdapter);
        UpdateChecker updateChecker = new UpdateChecker(this, messageService, platformScheduler);
        CarryCommandHandler commandHandler = new CarryCommandHandler(this, messageService, carryRequestService, updateChecker);

        registerCommand("cargar", commandHandler);
        registerCommand("aceptar", commandHandler);
        registerCommand("rechazar", commandHandler);
        registerCommand("soltar", commandHandler);
        registerCommand("carryme", commandHandler);

        registerListener(carryRequestService);
        registerListener(updateChecker);
        registerListener(commandHandler);
        new DismountListenerRegistrar(this, messageService).register();
        new AliasTabCompleteListenerRegistrar(this, commandHandler).register();

        new Metrics(this, BSTATS_PLUGIN_ID);
        updateChecker.checkForUpdates();

        if (platformScheduler.isFoliaRuntime()) {
            getLogger().info("Folia compatibility hooks enabled.");
        }
        getLogger().info("CarryMe enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (carryRequestService != null) {
            carryRequestService.cancelAll();
        }
    }

    private void registerCommand(String name, CarryCommandHandler handler) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            throw new IllegalStateException("Missing command definition for " + name);
        }
        command.setExecutor(handler);
        command.setTabCompleter(handler);
    }

    private void registerListener(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }
}
