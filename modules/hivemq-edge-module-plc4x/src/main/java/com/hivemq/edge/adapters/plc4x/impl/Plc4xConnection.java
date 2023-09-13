package com.hivemq.edge.adapters.plc4x.impl;

import com.hivemq.edge.HiveMQEdgeConstants;
import com.hivemq.edge.adapters.plc4x.Plc4xException;
import com.hivemq.edge.adapters.plc4x.model.Plc4xAdapterConfig;
import com.hivemq.extension.sdk.api.annotations.NotNull;
import org.apache.plc4x.java.PlcDriverManager;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.exceptions.PlcConnectionException;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.messages.PlcResponse;
import org.apache.plc4x.java.api.messages.PlcSubscriptionEvent;
import org.apache.plc4x.java.api.messages.PlcSubscriptionRequest;
import org.apache.plc4x.java.api.messages.PlcSubscriptionResponse;
import org.apache.plc4x.java.api.model.PlcSubscriptionHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public abstract class Plc4xConnection {

    private static final Logger log = LoggerFactory.getLogger(Plc4xConnection.class);
    private final Object lock = new Object();

    private @NotNull PlcDriverManager plcDriverManager;
    private @NotNull Plc4xAdapterConfig config;
    protected volatile PlcConnection plcConnection;

    public Plc4xConnection(final @NotNull PlcDriverManager plcDriverManager,
                           final @NotNull Plc4xAdapterConfig config, final boolean lazy) throws Plc4xException {
        this.plcDriverManager = plcDriverManager;
        this.config = config;
        if(!validConfiguration(config)){
            if(log.isDebugEnabled()){
                log.debug("Configuration provided to Plc4X connection was considered invalid by implementation");
            }
            throw new Plc4xException("invalid connection configuration, unable to initialize");
        }
        if(!lazy){
            initConnection();
        }
    }

    protected String createConnectionString(final @NotNull Plc4xAdapterConfig config){
        //opcua:tcp://Simons-Laptop.broadband:53530/OPCUA/SimulationServer

        if(config.getResourcePath() != null && !config.getResourcePath().trim().equals("")){
            return String.format("%s://%s:%s/%s",
                    getProtocol().trim(),
                    config.getHost().trim(),
                    config.getPort(),
                    config.getResourcePath().trim());
        } else {
            return String.format("%s://%s:%s",
                    getProtocol().trim(),
                    config.getHost().trim(),
                    config.getPort());
        }

    }

    protected void initConnection() throws Plc4xException {
        try {
            if(plcConnection == null){
                synchronized (lock){
                    if(plcConnection == null){
                        String connectionString = createConnectionString(config);
                        if(log.isInfoEnabled()){
                            log.info("Connecting via plx4j to {}", connectionString);
                        }
                        plcConnection = plcDriverManager.getConnection(connectionString);
                        plcConnection.connect();
                    }
                }
            }
        } catch(PlcConnectionException e){
            if(log.isWarnEnabled()){
                log.warn("Error encountered connecting to external device", e);
            }
            throw new Plc4xException("Error connecting", e);
        }
    }

    public void disconnect() throws Exception {
        synchronized (lock){
            try {
                if(plcConnection != null && plcConnection.isConnected()){
                    plcConnection.close();
                }
            } finally {
                plcConnection = null;
            }
        }
    }

    public boolean isConnected() {
        return plcConnection != null &&
                plcConnection.isConnected();
    }

    public void read(final @NotNull Plc4xAdapterConfig.Subscription subscription, final @NotNull Consumer<PlcReadResponse> responseConsumer) throws Plc4xException{
        initConnection();
        if (!plcConnection.getMetadata().canRead()) {
            throw new Plc4xException("connection type cannot read-blocking");
        }
        if(log.isDebugEnabled()){
            log.debug("Sending direct-read request to connection for {}", subscription.getTagName());
        }
        PlcReadRequest.Builder builder = plcConnection.readRequestBuilder();
        builder.addItem(subscription.getTagName(), initializeQueryForSubscription(subscription));
        PlcReadRequest readRequest = builder.build();
        readRequest.execute().whenComplete((plcResponse, throwable) -> {
            responseConsumer.accept(plcResponse);
        });
    }

    public void subscribe(final @NotNull Plc4xAdapterConfig.Subscription subscription, final @NotNull Consumer<PlcSubscriptionEvent> consumer)
            throws Plc4xException {
        initConnection();
        if (!plcConnection.getMetadata().canSubscribe()) {
            throw new Plc4xException("connection type cannot subscribe");
        }
        if(log.isDebugEnabled()){
            log.debug("Sending subscribe request to connection for {}", subscription.getTagName());
        }
        final PlcSubscriptionRequest.Builder builder = plcConnection.subscriptionRequestBuilder();
        builder.addChangeOfStateField(subscription.getTagName(), initializeQueryForSubscription(subscription));
        PlcSubscriptionRequest subscriptionRequest = builder.build();
        CompletableFuture<PlcSubscriptionResponse> future =
                (CompletableFuture<PlcSubscriptionResponse>) subscriptionRequest.execute();
        future.whenComplete((plcSubscriptionResponse, throwable) -> {
            if(throwable != null){
                log.warn("Connection subscription encountered an error;", throwable);
            } else {
                for (String subscriptionName : plcSubscriptionResponse.getFieldNames()) {
                    final PlcSubscriptionHandle subscriptionHandle =
                            plcSubscriptionResponse.getSubscriptionHandle(subscriptionName);
                    subscriptionHandle.register(consumer);
                }
            }
        });
    }

    /**
     * Use this hook method to modify the query generated used to read|subscribe to the devices
     */
    protected String initializeQueryForSubscription(@NotNull final Plc4xAdapterConfig.Subscription subscription){
        return subscription.getTagName();
    }

    protected boolean validConfiguration(@NotNull final Plc4xAdapterConfig config){
        return config.getResourcePath() != null && config.getHost() != null && config.getPort() > 0 && config.getPort() <
                HiveMQEdgeConstants.MAX_UINT16;
    }

    /**
     * Concrete implementations should provide the protocol with which they are connecting
     */
    protected abstract String getProtocol();

    /**
     *                 //                    event -> {
     *                 ////                        List<Pair<String, byte[]>> l = Plc4xUtils.getDataFromSubscriptionEvent(event);
     *                 ////                        processPlcFieldData(l);
     *                 //                    }
     *                 //            );
     */


//    protected void processPlcFieldData(List<Pair<String, byte[]>> l){
//        for (Pair<String, byte[]> p : l) {
//            if (p.getRight() != null && p.getRight().length > 0) {
//                try {
//                    logger.info("received field {} from plc -> {}", p.getLeft(),
//                            MqttsnWireUtils.toHex(p.getRight()));
//                    receiveExternal(context, p.getLeft(), 1, p.getRight());
//                } catch (Exception e) {
//                    logger.error("error receiving bytes from plc -> {}", p.getLeft(), e);
//                }
//            }
//        }
//    }



//    private static byte[] convert(Byte[] oBytes) {
//        byte[] bytes = new byte[oBytes.length];
//        for(int i = 0; i < oBytes.length; i++) {
//            bytes[i] = oBytes[i];
//        }
//        return bytes;
//    }
}
