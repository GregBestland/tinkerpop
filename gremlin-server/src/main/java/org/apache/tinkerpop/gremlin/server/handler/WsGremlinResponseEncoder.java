/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.tinkerpop.gremlin.server.handler;

import com.codahale.metrics.Meter;
import org.apache.tinkerpop.gremlin.driver.MessageSerializer;
import org.apache.tinkerpop.gremlin.driver.message.ResponseMessage;
import org.apache.tinkerpop.gremlin.driver.message.ResponseStatusCode;
import org.apache.tinkerpop.gremlin.driver.ser.MessageTextSerializer;
import org.apache.tinkerpop.gremlin.server.GremlinServer;
import org.apache.tinkerpop.gremlin.server.op.session.Session;
import org.apache.tinkerpop.gremlin.server.util.MetricManager;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.codahale.metrics.MetricRegistry.name;

/**
 * @author Stephen Mallette (http://stephen.genoprime.com)
 */
@ChannelHandler.Sharable
public class WsGremlinResponseEncoder extends MessageToMessageEncoder<ResponseMessage> {
    private static final Logger logger = LoggerFactory.getLogger(WsGremlinResponseEncoder.class);
    static final Meter errorMeter = MetricManager.INSTANCE.getMeter(name(GremlinServer.class, "errors"));

    @Override
    protected void encode(final ChannelHandlerContext ctx, final ResponseMessage o, final List<Object> objects) throws Exception {
        final MessageSerializer serializer = ctx.channel().attr(StateKey.SERIALIZER).get();
        final boolean useBinary = ctx.channel().attr(StateKey.USE_BINARY).get();
        final Session session = ctx.channel().attr(StateKey.SESSION).get();

        try {
            if (useBinary) {
                final ByteBuf serialized;

                // if the request came in on a session then the serialization must occur in that same thread.
                if (null == session)
                    serialized = serializer.serializeResponseAsBinary(o, ctx.alloc());
                else
                    serialized = session.getExecutor().submit(() -> serializer.serializeResponseAsBinary(o, ctx.alloc())).get();

                if (o.getStatus().getCode().isSuccess())
                    objects.add(new BinaryWebSocketFrame(serialized));
                else {
                    objects.add(new BinaryWebSocketFrame(serialized));
                    final ResponseMessage terminator = ResponseMessage.build(o.getRequestId()).code(ResponseStatusCode.SUCCESS_TERMINATOR).create();
                    objects.add(new BinaryWebSocketFrame(serializer.serializeResponseAsBinary(terminator, ctx.alloc())));
                    errorMeter.mark();
                }
            } else {
                // the expectation is that the GremlinTextRequestDecoder will have placed a MessageTextSerializer
                // instance on the channel.
                final MessageTextSerializer textSerializer = (MessageTextSerializer) serializer;

                final String serialized;

                // if the request came in on a session then the serialization must occur in that same thread.
                if (null == session)
                    serialized = textSerializer.serializeResponseAsString(o);
                else
                    serialized = session.getExecutor().submit(() -> textSerializer.serializeResponseAsString(o)).get();

                if (o.getStatus().getCode().isSuccess())
                    objects.add(new TextWebSocketFrame(true, 0, serialized));
                else {
                    objects.add(new TextWebSocketFrame(true, 0, serialized));
                    final ResponseMessage terminator = ResponseMessage.build(o.getRequestId()).code(ResponseStatusCode.SUCCESS_TERMINATOR).create();
                    objects.add(new TextWebSocketFrame(true, 0, textSerializer.serializeResponseAsString(terminator)));
                    errorMeter.mark();
                }
            }
        } catch (Exception ex) {
            errorMeter.mark();
            logger.warn("The result [{}] in the request {} could not be serialized and returned.", o.getResult(), o.getRequestId(), ex);
            final String errorMessage = String.format("Error during serialization: %s",
                    ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
            final ResponseMessage error = ResponseMessage.build(o.getRequestId())
                    .statusMessage(errorMessage)
                    .code(ResponseStatusCode.SERVER_ERROR_SERIALIZATION).create();
            if (useBinary) {
                ctx.write(new BinaryWebSocketFrame(serializer.serializeResponseAsBinary(error, ctx.alloc())), ctx.voidPromise());
                final ResponseMessage terminator = ResponseMessage.build(o.getRequestId()).code(ResponseStatusCode.SUCCESS_TERMINATOR).create();
                ctx.writeAndFlush(new BinaryWebSocketFrame(serializer.serializeResponseAsBinary(terminator, ctx.alloc())), ctx.voidPromise());
            } else {
                final MessageTextSerializer textSerializer = (MessageTextSerializer) serializer;
                ctx.write(new TextWebSocketFrame(textSerializer.serializeResponseAsString(error)), ctx.voidPromise());
                final ResponseMessage terminator = ResponseMessage.build(o.getRequestId()).code(ResponseStatusCode.SUCCESS_TERMINATOR).create();
                ctx.writeAndFlush(new TextWebSocketFrame(textSerializer.serializeResponseAsString(terminator)), ctx.voidPromise());
            }
        }
    }
}
