/*
 * JBoss, Home of Professional Open Source
 * Copyright 2006, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. 
 * See the copyright.txt in the distribution for a full listing 
 * of individual contributors.
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 * 
 * (C) 2005-2006,
 * @author JBoss Inc.
 */
/*
 * RestaurantServiceAT.java
 *
 * Copyright (c) 2003, 2004 Arjuna Technologies Ltd
 *
 * $Id: RestaurantServiceAT.java,v 1.3 2004/12/01 16:26:44 kconner Exp $
 *
 */
package org.jboss.narayana.quickstarts.restat.restaurant;

import org.jboss.narayana.txframework.api.annotation.lifecycle.at.Commit;
import org.jboss.narayana.txframework.api.annotation.lifecycle.at.Prepare;
import org.jboss.narayana.txframework.api.annotation.lifecycle.at.Rollback;
import org.jboss.narayana.txframework.api.annotation.service.ServiceRequest;
import org.jboss.narayana.txframework.api.annotation.transaction.Transactional;
import org.jboss.narayana.txframework.api.management.TXDataMap;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

/**
 * An adapter class that exposes the RestaurantManager business API as a transactional Web Service.
 *
 * @author paul.robinson@redhat.com, 2012-01-04
 */
@Path("/restaurant")
@Stateless
@Transactional
public class RestaurantServiceATImpl {

    private MockRestaurantManager mockRestaurantManager = MockRestaurantManager.getSingletonInstance();

    @Inject
    TXDataMap<String, String> dataControl;

    private static final String BOOKING_ID_KEY = "BOOKING_ID_KEY";

    /**
     * Book a number of seats in the restaurant. Enrols a Participant, then passes the call through to the business logic.
     */
    @POST
    @Produces("text/plain")
    @ServiceRequest
    public Response makeBooking() {

        System.out.println("[SERVICE] Restaurant service invoked to make a booking");

        // invoke the backend business logic:
        System.out.println("[SERVICE] Invoking the back-end business logic");
        String bookingId = mockRestaurantManager.makeBooking();
        dataControl.put(BOOKING_ID_KEY, bookingId);

        return Response.ok().build();
    }

    /**
     * obtain the number of existing bookings
     *
     * @return the number of current bookings
     */
    @GET
    @Produces("text/plain")
    @Path("getBookingCount")
    public Response getBookingCount() {

        Integer bookingCount = mockRestaurantManager.getBookingCount();
        return Response.ok(bookingCount).build();
    }

    /**
     * Reset the booking count to zero
     * <p/>
     * Note: To simplify this example, this method is not part of the compensation logic, so will not be undone if the AT is
     * compensated. It can also be invoked outside of an active AT.
     */
    @GET
    @Produces("text/plain")
    @Path("reset")
    public Response reset() {

        mockRestaurantManager.reset();
        return Response.ok().build();
    }


    /**
     * Invokes the prepare step of the business logic, reporting activity and outcome.
     *
     * @return Prepared where possible, Aborted where necessary.
     */
    @Prepare
    public Boolean prepare() {

        String bookingId = dataControl.get(BOOKING_ID_KEY);

        // Log the event and invoke the prepare operation
        // on the back-end logic.
        System.out.println("[SERVICE] Prepare called on participant, about to prepare the back-end resource");
        boolean success = mockRestaurantManager.prepare(bookingId);

        if (success) {
            System.out.println("[SERVICE] back-end resource prepared, participant votes prepared");
        } else {
            System.out.println("[SERVICE] back-end resource failed to prepare, participant votes aborted");
        }
        return success;
    }

    /**
     * Invokes the commit step of the business logic.
     */
    @Commit
    public void commit() {

        String bookingId = dataControl.get(BOOKING_ID_KEY);
        // Log the event and invoke the commit operation
        // on the backend business logic.
        System.out.println("[SERVICE] all participants voted 'prepared', so coordinator tells the participant to commit");
        mockRestaurantManager.commit(bookingId);
    }

    /**
     * Invokes the rollback operation on the business logic.
     */
    @Rollback
    public void rollback() {

        String bookingId = dataControl.get(BOOKING_ID_KEY);
        // Log the event and invoke the rollback operation
        // on the backend business logic.
        System.out.println("[SERVICE] one or more participants voted 'aborted' or a failure occurred, so coordinator tells the participant to rollback");
        mockRestaurantManager.rollback(bookingId);
    }
}
