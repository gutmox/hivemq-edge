package com.hivemq.api.resources.impl;

import com.hivemq.api.AbstractApi;
import com.hivemq.api.model.events.EventList;
import com.hivemq.api.resources.EventApi;
import com.hivemq.edge.modules.api.events.EventService;
import com.hivemq.extension.sdk.api.annotations.NotNull;

import javax.inject.Inject;
import javax.ws.rs.core.Response;

/**
 * @author Simon L Johnson
 */
public class EventResourceImpl extends AbstractApi implements EventApi {

    private final @NotNull EventService eventService;

    @Inject
    public EventResourceImpl(final @NotNull EventService eventService) {
        this.eventService = eventService;
    }

    @Override
    public Response listEvents(final Integer limit, final Long timestamp) {
        return Response.ok(new EventList(eventService.readEvents(timestamp, limit))).build();
    }
}
