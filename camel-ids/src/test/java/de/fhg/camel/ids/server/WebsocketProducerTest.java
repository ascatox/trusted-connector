/*-
 * ========================LICENSE_START=================================
 * Camel IDS Component
 * %%
 * Copyright (C) 2017 Fraunhofer AISEC
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.fhg.camel.ids.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
@Ignore
public class WebsocketProducerTest {

    private static final String MESSAGE = "MESSAGE";
    private static final String SESSION_KEY = "random-session-key";

    @Mock
    private WebsocketEndpoint endpoint;
    @Mock
    private WebsocketStore store;
    @Mock
    private Session session;
    @Mock
    private DefaultWebsocket defaultWebsocket1;
    @Mock
    private DefaultWebsocket defaultWebsocket2;
    @Mock
    private Exchange exchange;
    @Mock
    private Message inMessage;
    @Mock
    private RemoteEndpoint remoteEndpoint;

    private IOException exception = new IOException("BAD NEWS EVERYONE!");
    private WebsocketProducer websocketProducer;
    private Collection<DefaultWebsocket> sockets;

    @Before
    public void setUp() throws Exception {
        websocketProducer = new WebsocketProducer(endpoint);
        websocketProducer.setStore(store);
        sockets = Arrays.asList(defaultWebsocket1, defaultWebsocket2);
    }

    @Test
    public void testProcessSingleMessage() throws Exception {
        when(exchange.getIn()).thenReturn(inMessage);
        when(inMessage.getMandatoryBody()).thenReturn(MESSAGE);
        when(inMessage.getHeader(WebsocketConstants.SEND_TO_ALL, false, Boolean.class)).thenReturn(false);
        when(inMessage.getHeader(WebsocketConstants.CONNECTION_KEY, String.class)).thenReturn(SESSION_KEY);
        when(store.get(SESSION_KEY)).thenReturn(defaultWebsocket1);
        when(defaultWebsocket1.getSession()).thenReturn(session);
        when(session.isOpen()).thenReturn(true);
        when(session.getRemote()).thenReturn(remoteEndpoint);

        websocketProducer.process(exchange);

        InOrder inOrder = inOrder(endpoint, store, session, defaultWebsocket1, defaultWebsocket2, exchange, inMessage, remoteEndpoint);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(inMessage, times(1)).getMandatoryBody();
        inOrder.verify(inMessage, times(1)).getHeader(WebsocketConstants.SEND_TO_ALL, false, Boolean.class);
        inOrder.verify(inMessage, times(1)).getHeader(WebsocketConstants.CONNECTION_KEY, String.class);
        inOrder.verify(store, times(1)).get(SESSION_KEY);
        inOrder.verify(defaultWebsocket1, times(1)).getSession();
        inOrder.verify(session, times(1)).isOpen();
        inOrder.verify(defaultWebsocket1, times(1)).getSession();
        inOrder.verify(remoteEndpoint, times(1)).sendString(MESSAGE);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessSingleMessageWithException() throws Exception {
        when(exchange.getIn()).thenReturn(inMessage);
        when(inMessage.getMandatoryBody()).thenReturn(MESSAGE);
        when(inMessage.getHeader(WebsocketConstants.SEND_TO_ALL, false, Boolean.class)).thenReturn(false);
        when(inMessage.getHeader(WebsocketConstants.CONNECTION_KEY, String.class)).thenReturn(SESSION_KEY);
        when(store.get(SESSION_KEY)).thenReturn(defaultWebsocket1);
        when(defaultWebsocket1.getSession()).thenReturn(session);
        when(session.isOpen()).thenReturn(true);
        when(session.getRemote()).thenReturn(remoteEndpoint);
        doThrow(exception).when(remoteEndpoint).sendString(MESSAGE);

        try {
            websocketProducer.process(exchange);
            fail("Exception expected");
        } catch (IOException ioe) {
            assertEquals(exception, ioe);
        }

        InOrder inOrder = inOrder(endpoint, store, session, defaultWebsocket1, defaultWebsocket2, exchange, inMessage, remoteEndpoint);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(inMessage, times(1)).getMandatoryBody();
        inOrder.verify(inMessage, times(1)).getHeader(WebsocketConstants.SEND_TO_ALL, false, Boolean.class);
        inOrder.verify(inMessage, times(1)).getHeader(WebsocketConstants.CONNECTION_KEY, String.class);
        inOrder.verify(store, times(1)).get(SESSION_KEY);
        inOrder.verify(defaultWebsocket1, times(1)).getSession();
        inOrder.verify(session, times(1)).isOpen();
        inOrder.verify(defaultWebsocket1, times(1)).getSession();
        inOrder.verify(remoteEndpoint, times(1)).sendString(MESSAGE);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessMultipleMessages() throws Exception {
        when(exchange.getIn()).thenReturn(inMessage);
        when(inMessage.getMandatoryBody()).thenReturn(MESSAGE);
        when(inMessage.getHeader(WebsocketConstants.SEND_TO_ALL, false, Boolean.class)).thenReturn(true);
        when(store.getAll()).thenReturn(sockets);
        when(defaultWebsocket1.getSession()).thenReturn(session);
        when(defaultWebsocket2.getSession()).thenReturn(session);
        when(session.isOpen()).thenReturn(true);
        when(session.getRemote()).thenReturn(remoteEndpoint);

        websocketProducer.process(exchange);

        InOrder inOrder = inOrder(endpoint, store, session, defaultWebsocket1, defaultWebsocket2, exchange, inMessage, remoteEndpoint);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(inMessage, times(1)).getMandatoryBody();
        inOrder.verify(inMessage, times(1)).getHeader(WebsocketConstants.SEND_TO_ALL, false, Boolean.class);
        inOrder.verify(store, times(1)).getAll();
        inOrder.verify(defaultWebsocket1, times(1)).getSession();
        inOrder.verify(session, times(1)).isOpen();
        inOrder.verify(defaultWebsocket1, times(1)).getSession();
        inOrder.verify(remoteEndpoint, times(1)).sendString(MESSAGE);
        inOrder.verify(defaultWebsocket2, times(1)).getSession();
        inOrder.verify(session, times(1)).isOpen();
        inOrder.verify(defaultWebsocket2, times(1)).getSession();
        inOrder.verify(remoteEndpoint, times(1)).sendString(MESSAGE);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessMultipleMessagesWithException() throws Exception {
        when(exchange.getIn()).thenReturn(inMessage);
        when(inMessage.getMandatoryBody()).thenReturn(MESSAGE);
        when(inMessage.getHeader(WebsocketConstants.SEND_TO_ALL, false, Boolean.class)).thenReturn(true);
        when(store.getAll()).thenReturn(sockets);
        when(defaultWebsocket1.getSession()).thenReturn(session);
        when(defaultWebsocket2.getSession()).thenReturn(session);
        when(session.getRemote()).thenReturn(remoteEndpoint);
        doThrow(exception).when(remoteEndpoint).sendString(MESSAGE);
        when(session.isOpen()).thenReturn(true);

        try {
            websocketProducer.process(exchange);
            fail("Exception expected");
        } catch (Exception e) {
            assertEquals(exception, e.getCause());
        }

        InOrder inOrder = inOrder(endpoint, store, session, defaultWebsocket1, defaultWebsocket2, exchange, inMessage, remoteEndpoint);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(inMessage, times(1)).getMandatoryBody();
        inOrder.verify(inMessage, times(1)).getHeader(WebsocketConstants.SEND_TO_ALL, false, Boolean.class);
        inOrder.verify(store, times(1)).getAll();
        inOrder.verify(defaultWebsocket1, times(1)).getSession();
        inOrder.verify(session, times(1)).isOpen();
        inOrder.verify(defaultWebsocket1, times(1)).getSession();
        inOrder.verify(remoteEndpoint, times(1)).sendString(MESSAGE);
        inOrder.verify(defaultWebsocket2, times(1)).getSession();
        inOrder.verify(session, times(1)).isOpen();
        inOrder.verify(defaultWebsocket2, times(1)).getSession();
        inOrder.verify(remoteEndpoint, times(1)).sendString(MESSAGE);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessSingleMessageNoConnectionKey() throws Exception {
        when(exchange.getIn()).thenReturn(inMessage);
        when(inMessage.getBody(String.class)).thenReturn(MESSAGE);
        when(inMessage.getHeader(WebsocketConstants.SEND_TO_ALL, false, Boolean.class)).thenReturn(false);
        when(inMessage.getHeader(WebsocketConstants.CONNECTION_KEY, String.class)).thenReturn(null);

        try {
            websocketProducer.process(exchange);
            fail("Exception expected");
        } catch (Exception e) {
            assertEquals(IllegalArgumentException.class, e.getClass());
            assertNotNull(e.getMessage());
            assertNull(e.getCause());
        }

        InOrder inOrder = inOrder(endpoint, store, session, defaultWebsocket1, defaultWebsocket2, exchange, inMessage);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(inMessage, times(1)).getMandatoryBody();
        inOrder.verify(inMessage, times(1)).getHeader(WebsocketConstants.SEND_TO_ALL, false, Boolean.class);
        inOrder.verify(inMessage, times(1)).getHeader(WebsocketConstants.CONNECTION_KEY, String.class);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testSendMessage() throws Exception {
        when(defaultWebsocket1.getSession()).thenReturn(session);
        when(session.isOpen()).thenReturn(true);
        when(session.getRemote()).thenReturn(remoteEndpoint);

        websocketProducer.sendMessage(defaultWebsocket1, MESSAGE);

        InOrder inOrder = inOrder(endpoint, store, session, defaultWebsocket1, defaultWebsocket2, exchange, inMessage, remoteEndpoint);
        inOrder.verify(defaultWebsocket1, times(1)).getSession();
        inOrder.verify(session, times(1)).isOpen();
        inOrder.verify(defaultWebsocket1, times(1)).getSession();
        inOrder.verify(remoteEndpoint, times(1)).sendString(MESSAGE);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testSendMessageConnetionIsClosed() throws Exception {
        when(defaultWebsocket1.getSession()).thenReturn(session);
        when(session.isOpen()).thenReturn(false);
        when(session.getRemote()).thenReturn(remoteEndpoint);

        websocketProducer.sendMessage(defaultWebsocket1, MESSAGE);

        InOrder inOrder = inOrder(endpoint, store, session, defaultWebsocket1, defaultWebsocket2, exchange, inMessage, remoteEndpoint);
        inOrder.verify(defaultWebsocket1, times(1)).getSession();
        inOrder.verify(session, times(1)).isOpen();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testSendMessageWithException() throws Exception {
        when(defaultWebsocket1.getSession()).thenReturn(session);
        when(session.isOpen()).thenReturn(true);
        when(session.getRemote()).thenReturn(remoteEndpoint);
        doThrow(exception).when(remoteEndpoint).sendString(MESSAGE);

        try {
            websocketProducer.sendMessage(defaultWebsocket1, MESSAGE);
            fail("Exception expected");
        } catch (IOException ioe) {
            assertEquals(exception, ioe);
        }

        InOrder inOrder = inOrder(endpoint, store, session, defaultWebsocket1, defaultWebsocket2, exchange, inMessage, remoteEndpoint);
        inOrder.verify(defaultWebsocket1, times(1)).getSession();
        inOrder.verify(session, times(1)).isOpen();
        inOrder.verify(defaultWebsocket1, times(1)).getSession();
        inOrder.verify(remoteEndpoint, times(1)).sendString(MESSAGE);
        inOrder.verifyNoMoreInteractions();
    }
}
