package com.hivemq.edge.modules.adapters.impl;

import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Preconditions;
import com.hivemq.edge.modules.adapters.data.ProtocolAdapterDataSample;
import com.hivemq.edge.modules.adapters.model.ProtocolAdapterPollingSampler;
import com.hivemq.edge.modules.adapters.model.impl.ProtocolAdapterPollingSamplerImpl;
import com.hivemq.edge.modules.api.adapters.ModuleServices;
import com.hivemq.edge.modules.api.adapters.ProtocolAdapterInformation;
import com.hivemq.edge.modules.api.adapters.ProtocolAdapterPollingService;
import com.hivemq.edge.modules.api.adapters.ProtocolAdapterPublishBuilder;
import com.hivemq.edge.modules.config.impl.AbstractPollingProtocolAdapterConfig;
import com.hivemq.extension.sdk.api.annotations.NotNull;
import com.hivemq.extension.sdk.api.annotations.Nullable;
import com.hivemq.mqtt.handler.publish.PublishReturnCode;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author Simon L Johnson
 */
public abstract class AbstractPollingProtocolAdapter <T extends AbstractPollingProtocolAdapterConfig, U extends ProtocolAdapterDataSample>
        extends AbstractProtocolAdapter<T> {

    protected @Nullable ProtocolAdapterPollingService protocolAdapterPollingService;

    public AbstractPollingProtocolAdapter(
            final ProtocolAdapterInformation adapterInformation,
            final T adapterConfig,
            final MetricRegistry metricRegistry) {
        super(adapterInformation, adapterConfig, metricRegistry);
    }

    protected void bindServices(final @NotNull ModuleServices moduleServices){
        Preconditions.checkNotNull(moduleServices);
        super.bindServices(moduleServices);
        if(protocolAdapterPollingService == null){
            protocolAdapterPollingService = moduleServices.protocolAdapterPollingService();
        }
    }

    @Override
    public CompletableFuture<Void> stop() {
        return super.stop().whenComplete((v, t) ->
                protocolAdapterPollingService.getPollingJobsForAdapter(getId()).stream().forEach(
                    protocolAdapterPollingService::stopPolling));
    }

    protected CompletableFuture<PublishReturnCode> captureDataSample(final @NotNull U sample){
        Preconditions.checkNotNull(sample);
        Preconditions.checkNotNull(sample.getData());
        Preconditions.checkNotNull(sample.getTopic());
        Preconditions.checkArgument(sample.getQos() <= 2 && sample.getQos() >= 0, "QoS needs to be a valid Quality-Of-Service value (0,1,2)");
        try {
            final ProtocolAdapterPublishBuilder publishBuilder = adapterPublishService.publish()
                    .withTopic(sample.getTopic())
                    .withPayload(convertToJson(sample))
                    .withQoS(sample.getQos());
            final CompletableFuture<PublishReturnCode> publishFuture = publishBuilder.send();
            publishFuture.thenAccept(publishReturnCode -> protocolAdapterMetricsHelper.incrementReadPublishSuccess())
                    .exceptionally(throwable -> {
                        protocolAdapterMetricsHelper.incrementReadPublishFailure();
                        if (sample.getQos() == 0) {
                            protocolAdapterMetricsHelper.incrementReadPublishQos0Success();
                        } else if (sample.getQos() == 1) {
                            protocolAdapterMetricsHelper.incrementReadPublishQos1Success();
                        } else if (sample.getQos() == 2) {
                            protocolAdapterMetricsHelper.incrementReadPublishQos2Success();
                        }
                        log.warn("Error Publishing Adapter Payload", throwable); return null;
                    });
            return publishFuture;
        } catch(Exception e){
            return CompletableFuture.failedFuture(e);
        }
    }

    protected void startPolling(final @NotNull Sampler sampler) {
        Preconditions.checkNotNull(sampler);
        protocolAdapterPollingService.schedulePolling(this, sampler);
    }

    /**
     * Method is invoked by the sampling engine on the schedule determined by the configuration
     * supplied.
     */
    protected abstract CompletableFuture<U> onSamplerInvoked(T config) ;

    /**
     * Hook Method is invoked by the sampling engine when the scheduling engine is removing the
     * sampler from being managed
     */
    protected void onSamplerClosed(final @NotNull ProtocolAdapterPollingSampler sampler) {
        //-- Override me
    }

    /**
     * Hook Method is invoked by the sampling engine when the sampler throws an exception. It contains
     * details of whether the sampler will continue or be removed from the scheduler along with
     * the cause of the error.
     */
    protected void onSamplerError(final @NotNull ProtocolAdapterPollingSampler sampler, final @NotNull Throwable exception, boolean continuing) {
        setErrorConnectionStatus(exception);
        if(!continuing){
            stop();
        }
    }

    protected class Sampler extends ProtocolAdapterPollingSamplerImpl<U> {

        protected final T config;

        public Sampler(final @NotNull T config) {
            super(AbstractPollingProtocolAdapter.this.getId(), config.getPollingIntervalMillis(),
                    config.getPollingIntervalMillis(),
                    TimeUnit.MILLISECONDS,
                    config.getMaxPollingErrorsBeforeRemoval());
            this.config = config;
        }

        @Override
        public CompletableFuture<U> execute() {
            if(Thread.currentThread().isInterrupted()){
                return CompletableFuture.failedFuture(new InterruptedException());
            }
            CompletableFuture<U> data = onSamplerInvoked(config);
            data.thenApply(d -> captureDataSample(d));
            return data;
        }

        @Override
        public void close() {
            super.close();
            onSamplerClosed(this);
        }

        @Override
        public void error(@NotNull final Throwable t, final boolean continuing) {
            onSamplerError(this,t,continuing);
        }
    }
}
