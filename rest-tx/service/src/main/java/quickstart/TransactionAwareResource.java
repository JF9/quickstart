/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc. and/or its affiliates,
 * and individual contributors as indicated by the @author tags.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
 * (C) 2011,
 * @author JBoss, by Red Hat.
 */
package quickstart;

import java.net.HttpURLConnection;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.jboss.jbossts.star.provider.HttpResponseException;
import org.jboss.jbossts.star.util.TxMediaType;
import org.jboss.jbossts.star.util.TxStatus;
import org.jboss.jbossts.star.util.TxSupport;

/**
 * An example of how a REST resource can act as a participant in a REST Atomic transaction.
 * For a complete implementation of a participant please refer to the test suite, in particular the inner class:
 * org.jboss.jbossts.star.test.BaseTest$TransactionalResource which implements all the responsibilities of a participant
 *
 * The example sends a service request which is handled by the method someServiceRequest. The request includes the
 * URL for registering durable participants within the transaction. This naive implementation assumes every request
 * with a valid enlistment URL is a request a new unit of transactional work and enlists a new URL into the transaction.
 * Thus if a client makes two http requests to the method someServiceRequest then the participant is enlisted twice
 * into the transaction but with different completion URLs. This facilitates the demonstration of 2 phase commit
 * processing.
 */
@Path(TransactionAwareResource.PSEGMENT)
public class TransactionAwareResource {
    public static final String PSEGMENT = "service";
    public static String FAIL_COMMIT;
    private static int workId = 1;
    private static int commitCnt = 0;

    @GET
    public Response someServiceRequest(@Context UriInfo info, @QueryParam("enlistURL") String enlistUrl) {
        if (enlistUrl == null || enlistUrl.length() == 0)
            return Response.ok("non transactional request").build();

        String serviceURL = info.getBaseUri() + info.getPath();

        String linkHeader = new TxSupport().makeTwoPhaseAwareParticipantLinkHeader(
                serviceURL, false, String.valueOf(workId), null);

        System.out.println("Service: Enlisting " + linkHeader);

        try {
            new TxSupport().enlistParticipant(enlistUrl, linkHeader);
            return Response.ok(Integer.toString(workId++)).build();
        } catch (HttpResponseException e){
            return Response.status(e.getActualResponse()).build();
        }
    }

    @GET
    @Path("query")
    public Response someServiceRequest() {
        return Response.ok(Integer.toString(commitCnt)).build();
    }

    /*
     * this method handles PUT requests to the url that the participant gave to the REST Atomic Transactions
     * implementation (in the someServiceRequest method). This is the endpoint that the transaction manager interacts
     * with when it needs participants to prepare/commit/rollback their transactional work.
     */
    @PUT
    @Path("{wId}/terminator")
    public Response terminate(@PathParam("wId") @DefaultValue("")String wId, String content) {
        System.out.println("Service: PUT request to terminate url: wId=" + wId + ", status:=" + content);
        TxStatus status = TxSupport.toTxStatus(content);

        if (status.isPrepare()) {
            System.out.println("Service: preparing");
        } else if (status.isCommit()) {
            if (wId.equals(FAIL_COMMIT)) {
                System.out.println("Service: Halting VM during commit of work unit wId=" + wId);
                Runtime.getRuntime().halt(1);
            }
            commitCnt += 1;
            System.out.println("Service: committing");
        } else if (status.isAbort()) {
            System.out.println("Service: aborting");
        } else {
            return Response.status(HttpURLConnection.HTTP_BAD_REQUEST).build();
        }

        return Response.ok(TxSupport.toStatusContent(status.name())).build();
    }

    @HEAD
    @Path("{pId}/participant")
    public Response getTerminator(@Context UriInfo info, @PathParam("pId") @DefaultValue("")String wId) {
        String serviceURL = info.getBaseUri() + info.getPath();
        String linkHeader = new TxSupport().makeTwoPhaseAwareParticipantLinkHeader(serviceURL, false, wId, null);

        return Response.ok().header("Link", linkHeader).build();
    }
}
