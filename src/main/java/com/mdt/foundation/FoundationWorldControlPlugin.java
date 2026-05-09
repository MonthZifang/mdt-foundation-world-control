package com.mdt.foundation;

import arc.Events;
import arc.util.CommandHandler;
import arc.util.Log;
import com.mdt.foundation.api.ActionRequest;
import com.mdt.foundation.api.ActionResult;
import com.mdt.foundation.api.FoundationWorldControlApi;
import com.mdt.foundation.config.PluginConfiguration;
import com.mdt.foundation.http.HttpApiServer;
import com.mdt.foundation.service.WorldControlService;
import java.net.BindException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import mindustry.game.EventType.DisposeEvent;
import mindustry.mod.Plugin;

public final class FoundationWorldControlPlugin extends Plugin {
    private static final Path DATA_DIRECTORY = Paths.get("config", "mods", "config", "mdt-foundation-world-control");

    private static volatile FoundationWorldControlApi api;

    private Thread shutdownHook;
    private PluginConfiguration configuration;
    private WorldControlService service;
    private HttpApiServer httpApiServer;
    private boolean apiRunning;

    public static FoundationWorldControlApi getApi() {
        return api;
    }

    @Override
    public void init() {
        try {
            configuration = PluginConfiguration.load(DATA_DIRECTORY);
            service = new WorldControlService(configuration);
            api = service;
            registerSharedServices();
            apiRunning = false;

            if (configuration.isApiEnabled()) {
                try {
                    httpApiServer = new HttpApiServer(configuration, service);
                    httpApiServer.start();
                    apiRunning = true;
                    registerShutdownHook();
                    Events.on(DisposeEvent.class, event -> shutdownHttpApiServer());
                } catch (BindException bindException) {
                    httpApiServer = null;
                    Log.warn("FoundationWorldControl API port already in use. Running without HTTP API. host=@ port=@",
                        configuration.getApiHost(), configuration.getApiPort());
                }
            }

            Log.info(
                "FoundationWorldControl loaded. apiEnabled=@ apiRunning=@ host=@ port=@ config=@",
                configuration.isApiEnabled(),
                apiRunning,
                configuration.getApiHost(),
                configuration.getApiPort(),
                configuration.getConfigFile()
            );
        } catch (Exception exception) {
            throw new RuntimeException("FoundationWorldControl init failed.", exception);
        }
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("fwc-status", "Show plugin status.", args -> {
            Log.info(
                "apiEnabled=@ apiRunning=@ host=@ port=@ tokenRequired=@ config=@",
                configuration.isApiEnabled(),
                apiRunning,
                configuration.getApiHost(),
                configuration.getApiPort(),
                configuration.isApiRequireToken(),
                configuration.getConfigFile()
            );
        });

        handler.register("fwc-exec", "<operation> [key=value...]", "Execute a world control action.", args -> {
            Map<String, String> parameters = new LinkedHashMap<String, String>();
            for (int index = 1; index < args.length; index++) {
                String argument = args[index];
                int split = argument.indexOf('=');
                if (split <= 0) {
                    continue;
                }
                parameters.put(argument.substring(0, split), argument.substring(split + 1));
            }
            ActionResult result = service.execute(ActionRequest.of(args[0], parameters));
            Log.info("success=@ operation=@ message=@ data=@", result.isSuccess(), result.getOperation(), result.getMessage(), result.getData());
        });
    }

    private void shutdownHttpApiServer() {
        if (httpApiServer != null) {
            httpApiServer.close();
            httpApiServer = null;
        }
        apiRunning = false;
        unregisterSharedServices();
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                // JVM is already shutting down.
            }
            shutdownHook = null;
        }
    }

    private void registerShutdownHook() {
        if (shutdownHook != null) {
            return;
        }
        shutdownHook = new Thread(new Runnable() {
            @Override
            public void run() {
                shutdownHttpApiServer();
            }
        }, "mdt-foundation-world-control-shutdown");
        shutdownHook.setDaemon(true);
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    private void registerSharedServices() {
        registerSharedService("mdt.foundation.api", api);
        registerSharedService("com.mdt.foundation.api.FoundationWorldControlApi", api);
    }

    private void unregisterSharedServices() {
        unregisterSharedService("mdt.foundation.api");
        unregisterSharedService("com.mdt.foundation.api.FoundationWorldControlApi");
    }

    private void registerSharedService(String key, Object service) {
        try {
            Class<?> hub = Class.forName("mdt.ServeMdtPlugin");
            hub.getMethod("registerSharedService", String.class, Object.class).invoke(null, key, service);
        } catch (Exception ignored) {
            // Core hub is optional.
        }
    }

    private void unregisterSharedService(String key) {
        try {
            Class<?> hub = Class.forName("mdt.ServeMdtPlugin");
            hub.getMethod("unregisterSharedService", String.class).invoke(null, key);
        } catch (Exception ignored) {
            // Core hub is optional.
        }
    }
}
