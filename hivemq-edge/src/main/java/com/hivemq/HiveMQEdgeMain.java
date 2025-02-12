/*
 * Copyright 2019-present HiveMQ GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hivemq;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.hivemq.bootstrap.HiveMQExceptionHandlerBootstrap;
import com.hivemq.bootstrap.LoggingBootstrap;
import com.hivemq.bootstrap.ioc.DaggerInjector;
import com.hivemq.bootstrap.ioc.Injector;
import com.hivemq.common.shutdown.ShutdownHooks;
import com.hivemq.configuration.ConfigurationBootstrap;
import com.hivemq.configuration.info.SystemInformation;
import com.hivemq.configuration.info.SystemInformationImpl;
import com.hivemq.configuration.service.ApiConfigurationService;
import com.hivemq.configuration.service.ConfigurationService;
import com.hivemq.embedded.EmbeddedExtension;
import com.hivemq.exceptions.HiveMQEdgeStartupException;
import com.hivemq.extension.sdk.api.annotations.NotNull;
import com.hivemq.extension.sdk.api.annotations.Nullable;
import com.hivemq.http.JaxrsHttpServer;
import com.hivemq.metrics.MetricRegistryLogger;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class HiveMQEdgeMain {
    private static final Logger log = LoggerFactory.getLogger(HiveMQEdgeMain.class);

    private @Nullable ConfigurationService configService;
    private final @NotNull MetricRegistry metricRegistry;
    private final @NotNull SystemInformation systemInformation;

    private @Nullable JaxrsHttpServer jaxrsServer;

    private @Nullable HiveMQEdgeGateway instance;
    private @Nullable Injector injector;
    private Thread shutdownThread;

    public HiveMQEdgeMain(
            final @NotNull SystemInformation systemInformation,
            final @Nullable MetricRegistry metricRegistry,
            final @Nullable ConfigurationService configService) {
        this.metricRegistry = metricRegistry;
        this.systemInformation = systemInformation;
        this.configService = configService;
    }

    public void bootstrap() throws HiveMQEdgeStartupException, InterruptedException {
        // Already bootstrapped.
        if (injector != null) {
            return;
        }

        metricRegistry.addListener(new MetricRegistryLogger());

        LoggingBootstrap.prepareLogging();

        // Embedded has already called init as it is required to read the config file.
        if (!systemInformation.isEmbedded()) {
            log.trace("Initializing HiveMQ home directory");
            //Create SystemInformation this early because logging depends on it
            systemInformation.init();
        }

        log.trace("Initializing Logging");
        LoggingBootstrap.initLogging(systemInformation.getConfigFolder());

        log.trace("Initializing Exception handlers");
        HiveMQExceptionHandlerBootstrap.addUnrecoverableExceptionHandler();

        if (configService == null) {
            log.trace("Initializing configuration");
            configService = ConfigurationBootstrap.bootstrapConfig(systemInformation);
        }

        //ungraceful shutdown does not delete tmp folders, so we clean them up on broker start
        log.trace("Cleaning up temporary folders");
        deleteTmpFolder(systemInformation.getDataFolder());

        log.trace("Initializing injector");
        final long startDagger = System.currentTimeMillis();
        injector = DaggerInjector.builder()
                .configurationService(configService)
                .systemInformation(systemInformation)
                .metricRegistry(metricRegistry)
                .build();
        log.trace("Initialized injector in {}ms", (System.currentTimeMillis() - startDagger));

        log.trace("Initializing classes");
        final long startInit = System.currentTimeMillis();
        injector.initEagerSingletons();
        log.trace("Initialized classes in {}ms", (System.currentTimeMillis() - startInit));

    }

    protected void startGateway(final @Nullable EmbeddedExtension embeddedExtension) throws HiveMQEdgeStartupException {
        if (injector == null) {
            throw new HiveMQEdgeStartupException("invalid startup state");
        }

        final ShutdownHooks shutdownHooks = injector.shutdownHooks();
        if (shutdownHooks.isShuttingDown()) {
            throw new HiveMQEdgeStartupException("User aborted.");
        }

        instance = injector.edgeGateway();
        instance.start(embeddedExtension);

        initializeApiServer(injector);
        startApiServer();
    }

    protected void stopGateway() {
        if (injector == null) {
            return;
        }
        final ShutdownHooks shutdownHooks = injector.shutdownHooks();
        // Already shutdown.
        if (shutdownHooks.isShuttingDown()) {
            return;
        }

        shutdownHooks.runShutdownHooks();

        //clear metrics
        metricRegistry.removeMatching(MetricFilter.ALL);

        //Stop the API Webserver
        stopApiServer();
    }

    protected void initializeApiServer(@NotNull final Injector injector) {
        ApiConfigurationService config = Objects.requireNonNull(configService).apiConfiguration();
        if (jaxrsServer == null && config.isEnabled()) {
            jaxrsServer = injector.apiServer();
        } else {
            log.info("API is DISABLED by configuration");
        }
    }

    protected void startApiServer() {
        //-- This will only have initialized if the config is enabled
        if (jaxrsServer != null && configService != null && configService.apiConfiguration().isEnabled()) {
            jaxrsServer.startServer();
        }
    }

    protected void stopApiServer() {
        if (jaxrsServer != null) {
            jaxrsServer.stopServer();
        }
    }

    protected void afterStart() {
        //hook method
    }

    public void start(final @Nullable EmbeddedExtension embeddedExtension)
            throws InterruptedException, HiveMQEdgeStartupException {
        shutdownThread = new Thread(this::stop, "shutdown-thread");
        Runtime.getRuntime().addShutdownHook(shutdownThread);
        bootstrap();
        startGateway(embeddedExtension);
        afterStart();
    }

    public void stop() {
        stopGateway();
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownThread);
        } catch (IllegalStateException ignored) {
            //ignore
        }
    }

    public static void main(final String @NotNull [] args) throws Exception {
        log.info("Starting HiveMQ Edge...");
        final long startTime = System.nanoTime();
        final HiveMQEdgeMain server = new HiveMQEdgeMain(new SystemInformationImpl(true), new MetricRegistry(), null);
        try {
            server.start(null);
            log.info("Started HiveMQ Edge in {}ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime));
        } catch (final HiveMQEdgeStartupException e) {
            log.error("HiveMQ Edge start was aborted with error.", e);
        }
    }

    public @Nullable Injector getInjector() {
        return injector;
    }

    private static void deleteTmpFolder(final @NotNull File dataFolder) {
        final String tmpFolder = dataFolder.getPath() + File.separator + "tmp";
        try {
            //ungraceful shutdown does not delete tmp folders, so we clean them up on broker start
            FileUtils.deleteDirectory(new File(tmpFolder));
        } catch (IOException e) {
            //No error because it's not business breaking
            log.warn("The temporary folder could not be deleted ({}).", tmpFolder);
            if (log.isDebugEnabled()) {
                log.debug("Original Exception: ", e);
            }
        }
    }

}
