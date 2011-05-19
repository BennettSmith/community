/**
 * Copyright (c) 2002-2011 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.server.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.server.NeoServerWithEmbeddedWebServer;
import org.neo4j.server.ServerBuilder;
import org.neo4j.server.database.DatabaseBlockedException;
import org.neo4j.server.rest.domain.GraphDbHelper;
import org.neo4j.server.rest.domain.JsonHelper;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

public class RetrieveNodeFunctionalTest
{
    private URI nodeUri;

    private NeoServerWithEmbeddedWebServer server;

    private FunctionalTestHelper functionalTestHelper;



    @Before
    public void setupServer() throws IOException, DatabaseBlockedException, URISyntaxException {
        server = ServerBuilder.server().withRandomDatabaseDir().withPassingStartupHealthcheck().build();
        server.start();
        functionalTestHelper = new FunctionalTestHelper(server);
        nodeUri = new URI(functionalTestHelper.nodeUri() + "/" + new GraphDbHelper(server.getDatabase()).createNode());
    }

    @After
    public void stopServer() {
        server.stop();
        server = null;
    }

    @Test
    public void shouldGet200WhenRetrievingNode() throws Exception {
        String uri = nodeUri.toString();
        DocsGenerator.create(
                "Get node",
                "Note that the response contains URI/templates for "
                        + "the available operations for getting properties and relationships." )
                .get( uri );
    }

    @Test
    public void shouldGetContentLengthHeaderWhenRetrievingNode() throws Exception {
        ClientResponse response = retrieveNodeFromService(nodeUri.toString());
        assertNotNull(response.getHeaders().get("Content-Length"));
    }

    @Test
    public void shouldHaveJsonMediaTypeOnResponse() {
        ClientResponse response = retrieveNodeFromService(nodeUri.toString());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getType());
    }

    @Test
    public void shouldHaveJsonDataInResponse() throws Exception {
        ClientResponse response = retrieveNodeFromService(nodeUri.toString());

        Map<String, Object> map = JsonHelper.jsonToMap(response.getEntity(String.class));
        assertTrue(map.containsKey("self"));
    }

    @Test
    public void shouldGet404WhenRetrievingNonExistentNode() throws Exception {
        DocsGenerator.create( "Get non-existent node" )
                .expectedStatus( Response.Status.NOT_FOUND )
                .get( nodeUri + "00000" );
    }

    private ClientResponse retrieveNodeFromService(final String uri) {
        WebResource resource = Client.create().resource(uri);
        ClientResponse response = resource.accept(MediaType.APPLICATION_JSON).get(ClientResponse.class);
        return response;
    }


}
