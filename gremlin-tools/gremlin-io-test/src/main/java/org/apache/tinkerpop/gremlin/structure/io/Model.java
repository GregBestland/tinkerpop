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
package org.apache.tinkerpop.gremlin.structure.io;

import org.apache.tinkerpop.gremlin.driver.message.RequestMessage;
import org.apache.tinkerpop.gremlin.driver.message.ResponseMessage;
import org.apache.tinkerpop.gremlin.process.traversal.Bytecode;
import org.apache.tinkerpop.gremlin.process.traversal.Operator;
import org.apache.tinkerpop.gremlin.process.traversal.Order;
import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.Pop;
import org.apache.tinkerpop.gremlin.process.traversal.SackFunctions;
import org.apache.tinkerpop.gremlin.process.traversal.Scope;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.step.TraversalOptionParent;
import org.apache.tinkerpop.gremlin.process.traversal.util.DefaultTraversalMetrics;
import org.apache.tinkerpop.gremlin.process.traversal.util.MutableMetrics;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalMetrics;
import org.apache.tinkerpop.gremlin.structure.Column;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.io.graphson.GraphSONCompatibility;
import org.apache.tinkerpop.gremlin.structure.io.gryo.GryoCompatibility;
import org.apache.tinkerpop.gremlin.structure.util.star.StarGraph;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerFactory;

import java.io.File;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Defines the supported types for IO and the versions (and configurations) to which they apply and are tested.
 *
 * @author Stephen Mallette (http://stephen.genoprime.com)
 */
public class Model {

    private static final List<Compatibility> ALL = Collections.unmodifiableList(new ArrayList<Compatibility>() {{
        addAll(Arrays.asList(GraphSONCompatibility.values()));
        addAll(Arrays.asList(GryoCompatibility.values()));
    }});

    private static final Model model = new Model();

    private final Map<String, List<Entry>> entries = new HashMap<>();
    
    private Model() {
        final Graph graph = TinkerFactory.createTheCrew();
        final GraphTraversalSource g = graph.traversal();

        // IMPORTANT - the "title" or name of the Entry needs to be unique
        addCoreEntry(File.class, "Class", new HashMap<Compatibility, String>() {{
            put(GryoCompatibility.V1D0_3_2_3, "Serialization of Class in Gryo 1.0 had a bug that prevented proper operation in versions prior to 3.2.4.");
        }}, GryoCompatibility.V1D0_3_2_3);
        addCoreEntry(new Date(1481750076295L), "Date");
        addCoreEntry(100.00d, "Double");
        addCoreEntry(100.00f, "Float");
        addCoreEntry(100, "Integer");
        addCoreEntry(100L, "Long");
        addCoreEntry(new java.sql.Timestamp(1481750076295L), "Timestamp", new HashMap<Compatibility, String>() {{
            put(GryoCompatibility.V1D0_3_2_3, "Timestamp was added to Gryo 1.0 as of 3.2.4. It was not supported in 3.2.3.");
        }}, GryoCompatibility.V1D0_3_2_3);
        addCoreEntry(UUID.fromString("41d2e28a-20a4-4ab0-b379-d810dede3786"), "UUID");

        // TODO: remove incompatibilities in Element on GraphSON 2.0
        // temporary incompatibility in v2 graphson starting at 3.3.0 with Element properties - need to revert some
        // changes on master (which is what helped start this mess) once this work is merged
        final Compatibility[] graphsonV2NoType = Compatibilities.with(GraphSONCompatibility.class)
                .configuredAs(".*no-types").matchToArray();
        addGraphStructureEntry(graph.edges().next(), "Edge", "", graphsonV2NoType);
        addGraphStructureEntry(g.V().out().out().path().next(), "Path", "", graphsonV2NoType);
        addGraphStructureEntry(graph.edges().next().properties().next(), "Property", "", graphsonV2NoType);
        addEntry("Graph Structure", StarGraph.of(graph.vertices().next()), "StarGraph", "", Compatibilities.GRYO_ONLY.match());
        addGraphStructureEntry(graph, "TinkerGraph", "`TinkerGraph` has a custom serializer that is registered as part of the `TinkerIoRegistry`.");
        addEntry("Graph Structure", g.V(1).out().out().tree().next(), "Tree", "", Compatibilities.GRYO_ONLY.match());
        addGraphStructureEntry(graph.vertices().next(), "Vertex", "", graphsonV2NoType);
        addGraphStructureEntry(graph.vertices().next().properties().next(), "VertexProperty", "", graphsonV2NoType);

        addGraphProcessEntry(SackFunctions.Barrier.normSack, "Barrier", "");
        addGraphProcessEntry(new Bytecode.Binding("x", 1), "Binding", "A \"Binding\" refers to a `Bytecode.Binding`.");
        addGraphProcessEntry(g.V().hasLabel("person").out().in().tree().asAdmin().getBytecode(), "Bytecode", "The following `Bytecode` example represents the traversal of `g.V().hasLabel('person').out().in().tree()`. Obviously the serialized `Bytecode` woudl be quite different for the endless variations of commands that could be used together in the Gremlin language.");
        addGraphProcessEntry(VertexProperty.Cardinality.list, "Cardinality");
        addGraphProcessEntry(Column.keys, "Column", "");
        addGraphProcessEntry(Direction.OUT, "Direction");
        addGraphProcessEntry(Operator.sum, "Operator", "");
        addGraphProcessEntry(Order.incr, "Order", "");
        addGraphProcessEntry(TraversalOptionParent.Pick.any, "Pick");
        addGraphProcessEntry(Pop.all, "Pop");
        addGraphProcessEntry(org.apache.tinkerpop.gremlin.util.function.Lambda.function("{ it.get() }"), "Lambda");
        final TraversalMetrics tm = createStaticTraversalMetrics();
        final MutableMetrics metrics = new MutableMetrics(tm.getMetrics("7.0.0()"));
        metrics.addNested(new MutableMetrics(tm.getMetrics("3.0.0()")));
        addGraphProcessEntry(metrics, "Metrics");
        addGraphProcessEntry(P.gt(0), "P");
        addGraphProcessEntry(P.gt(0).and(P.lt(10)), "P and", "", new HashMap<Compatibility, String>() {{
            put(GryoCompatibility.V1D0_3_2_3, "A bug in the the Gryo serialization of ConjunctiveP prevented its proper serialization in versions prior to 3.3.0 and 3.2.4.");
        }}, GryoCompatibility.V1D0_3_2_3);
        addGraphProcessEntry(P.gt(0).or(P.within(-1, -10, -100)), "P or", "", new HashMap<Compatibility, String>() {{
            put(GryoCompatibility.V1D0_3_2_3, "A bug in the the Gryo serialization of ConjunctiveP prevented its proper serialization in versions prior to 3.3.0 and 3.2.4.");
        }}, GryoCompatibility.V1D0_3_2_3);
        addGraphProcessEntry(Scope.local, "Scope");
        addGraphProcessEntry(T.label, "T", "");
        addGraphProcessEntry(createStaticTraversalMetrics(), "TraversalMetrics");
        addGraphProcessEntry(g.V().hasLabel("person").asAdmin().nextTraverser(), "Traverser");

        final Map<String,Object> requestBindings = new HashMap<>();
        requestBindings.put("x", 1);

        final Map<String,Object> requestAliases = new HashMap<>();
        requestAliases.put("g", "social");

        RequestMessage requestMessage;
        requestMessage = RequestMessage.build("authentication").
                overrideRequestId(UUID.fromString("cb682578-9d92-4499-9ebc-5c6aa73c5397")).
                add("saslMechanism", "PLAIN", "sasl", "AHN0ZXBocGhlbgBwYXNzd29yZA==").create();
        addRequestMessageEntry(requestMessage, "Authentication Response", "The following `RequestMessage` is an example of the response that should be made to a SASL-based authentication challenge.");
        requestMessage = RequestMessage.build("eval").processor("session").
                overrideRequestId(UUID.fromString("cb682578-9d92-4499-9ebc-5c6aa73c5397")).
                add("gremlin", "g.V(x)", "bindings", requestBindings, "language", "gremlin-groovy", "session", UUID.fromString("41d2e28a-20a4-4ab0-b379-d810dede3786")).create();
        addRequestMessageEntry(requestMessage, "Session Eval", "The following `RequestMessage` is an example of a simple session request for a script evaluation with parameters.");
        requestMessage = RequestMessage.build("eval").processor("session").
                overrideRequestId(UUID.fromString("cb682578-9d92-4499-9ebc-5c6aa73c5397")).
                add("gremlin", "social.V(x)", "bindings", requestBindings, "language", "gremlin-groovy", "aliases", requestAliases, "session", UUID.fromString("41d2e28a-20a4-4ab0-b379-d810dede3786")).create();
        addRequestMessageEntry(requestMessage, "Session Eval Aliased", "The following `RequestMessage` is an example of a session request for a script evaluation with an alias that binds the `TraversalSource` of \"g\" to \"social\".");
        requestMessage = RequestMessage.build("close").processor("session").
                overrideRequestId(UUID.fromString("cb682578-9d92-4499-9ebc-5c6aa73c5397")).
                add("session", UUID.fromString("41d2e28a-20a4-4ab0-b379-d810dede3786")).create();
        addRequestMessageEntry(requestMessage, "Session Close", "The following `RequestMessage` is an example of a request to close a session.");
        requestMessage = RequestMessage.build("eval").
                overrideRequestId(UUID.fromString("cb682578-9d92-4499-9ebc-5c6aa73c5397")).
                add("gremlin", "g.V(x)", "bindings", requestBindings, "language", "gremlin-groovy").create();
        addRequestMessageEntry(requestMessage, "Sessionless Eval", "The following `RequestMessage` is an example of a simple sessionless request for a script evaluation with parameters.");
        requestMessage = RequestMessage.build("eval").
                overrideRequestId(UUID.fromString("cb682578-9d92-4499-9ebc-5c6aa73c5397")).
                add("gremlin", "social.V(x)", "bindings", requestBindings, "language", "gremlin-groovy", "aliases", requestAliases).create();
        addRequestMessageEntry(requestMessage, "Sessionless Eval Aliased", "The following `RequestMessage` is an example of a sessionless request for a script evaluation with an alias that binds the `TraversalSource` of \"g\" to \"social\".");

        ResponseMessage responseMessage = ResponseMessage.build(UUID.fromString("41d2e28a-20a4-4ab0-b379-d810dede3786")).
                code(org.apache.tinkerpop.gremlin.driver.message.ResponseStatusCode.AUTHENTICATE).create();
        addResponseMessageEntry(responseMessage, "Authentication Challenge", "When authentication is enabled, an initial request to the server will result in an authentication challenge. The typical response message will appear as follows, but handling it could be different depending on the SASL implementation (e.g. multiple challenges maybe requested in some cases, but no in the default provided by Gremlin Server).");
        responseMessage = ResponseMessage.build(UUID.fromString("41d2e28a-20a4-4ab0-b379-d810dede3786")).
                code(org.apache.tinkerpop.gremlin.driver.message.ResponseStatusCode.SUCCESS).
                result(Collections.singletonList(graph.vertices().next())).create();
        addResponseMessageEntry(responseMessage, "Standard Result", "The following `ResponseMessage` is a typical example of the typical successful response Gremlin Server will return when returning results from a script.");
        
        addExtendedEntry(new BigDecimal(new java.math.BigInteger("123456789987654321123456789987654321")), "BigDecimal", "", Compatibilities.UNTYPED_GRAPHSON.matchToArray());
        addExtendedEntry(new BigInteger("123456789987654321123456789987654321"), "BigInteger", "", Compatibilities.UNTYPED_GRAPHSON.matchToArray());
        addExtendedEntry(new Byte("1"), "Byte", "", Compatibilities.UNTYPED_GRAPHSON.matchToArray());
        addEntry("Extended", () -> java.nio.ByteBuffer.wrap("some bytes for you".getBytes()), "ByteBuffer", "",
                new HashMap<Compatibility, String>() {{
                    put(GryoCompatibility.V1D0_3_2_3, "ByteBuffer was added to Gryo 1.0 as of 3.2.4. It was not supported in earlier versions.");
                }},
                GraphSONCompatibility.V1D0_3_2_3, GraphSONCompatibility.V1D0_3_3_0, GraphSONCompatibility.V2D0_NO_TYPE_3_2_3,
                GraphSONCompatibility.V2D0_NO_TYPE_3_3_0, GryoCompatibility.V1D0_3_2_3);
        addExtendedEntry("x".charAt(0), "Char", "", Compatibilities.UNTYPED_GRAPHSON.matchToArray());
        addExtendedEntry(Duration.ofDays(5), "Duration","The following example is a `Duration` of five days.", Compatibilities.UNTYPED_GRAPHSON.matchToArray());
        try {
            addEntry("Extended", InetAddress.getByName("localhost"), "InetAddress", "",
                    new HashMap<Compatibility, String>() {{
                        put(GryoCompatibility.V1D0_3_2_3, "InetAddress was added to Gryo 1.0 as of 3.2.4. It was not supported in earlier versions.");
                    }},
                    GraphSONCompatibility.V1D0_3_2_3, GraphSONCompatibility.V1D0_3_3_0, GraphSONCompatibility.V2D0_NO_TYPE_3_2_3,
                    GraphSONCompatibility.V2D0_NO_TYPE_3_3_0, GryoCompatibility.V1D0_3_2_3);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        addExtendedEntry(Instant.parse("2016-12-14T16:39:19.349Z"), "Instant", "", Compatibilities.UNTYPED_GRAPHSON.matchToArray());
        addExtendedEntry(LocalDate.of(2016, 1, 1), "LocalDate", "", Compatibilities.UNTYPED_GRAPHSON.matchToArray());
        addExtendedEntry(LocalDateTime.of(2016, 1, 1, 12, 30), "LocalDateTime", "", Compatibilities.UNTYPED_GRAPHSON.matchToArray());
        addExtendedEntry(LocalTime.of(12, 30, 45), "LocalTime", "", Compatibilities.UNTYPED_GRAPHSON.matchToArray());
        addExtendedEntry(MonthDay.of(1, 1), "MonthDay", "", Compatibilities.UNTYPED_GRAPHSON.matchToArray());
        addExtendedEntry(OffsetDateTime.parse("2007-12-03T10:15:30+01:00"), "OffsetDateTime", "", Compatibilities.UNTYPED_GRAPHSON.matchToArray());
        addExtendedEntry(OffsetTime.parse("10:15:30+01:00"), "OffsetTime", "", Compatibilities.UNTYPED_GRAPHSON.matchToArray());
        addExtendedEntry(Period.of(1, 6, 15), "Period", "The following example is a `Period` of one year, six months and fifteen days.", Compatibilities.UNTYPED_GRAPHSON.matchToArray());
        addExtendedEntry(new Short("100"), "Short", "", Compatibilities.UNTYPED_GRAPHSON.matchToArray());
        addExtendedEntry(Year.of(2016), "Year", "The following example is of the `Year` \"2016\".", Compatibilities.UNTYPED_GRAPHSON.matchToArray());
        addExtendedEntry(YearMonth.of(2016, 6), "YearMonth", "The following example is a `YearMonth` of \"June 2016\"", Compatibilities.UNTYPED_GRAPHSON.matchToArray());
        addExtendedEntry(ZonedDateTime.of(2016, 12, 23, 12, 12, 24, 36, ZoneId.of("GMT+2")), "ZonedDateTime", "", Compatibilities.UNTYPED_GRAPHSON.matchToArray());
        addExtendedEntry(ZoneOffset.ofHoursMinutesSeconds(3, 6, 9), "ZoneOffset", "The following example is a `ZoneOffset` of three hours, six minutes, and nine seconds.", Compatibilities.UNTYPED_GRAPHSON.matchToArray());
    }

    private static DefaultTraversalMetrics createStaticTraversalMetrics() {
        // based on g.V().hasLabel("person").out().out().tree().profile().next()
        final List<MutableMetrics> traversalMutableMetrics = new ArrayList<>();
        final MutableMetrics m7 = new MutableMetrics("7.0.0()", "TinkerGraphStep(vertex,[~label.eq(person)])");
        m7.setDuration(100, TimeUnit.MILLISECONDS);
        m7.setCount("traverserCount", 4);
        m7.setCount("elementCount", 4);
        m7.setAnnotation("percentDur", 25.0d);
        traversalMutableMetrics.add(m7);

        final MutableMetrics m2 = new MutableMetrics("2.0.0()", "VertexStep(OUT,vertex)");
        m2.setDuration(100, TimeUnit.MILLISECONDS);
        m2.setCount("traverserCount", 13);
        m2.setCount("elementCount", 13);
        m2.setAnnotation("percentDur", 25.0d);
        traversalMutableMetrics.add(m2);

        final MutableMetrics m3 = new MutableMetrics("3.0.0()", "VertexStep(OUT,vertex)");
        m3.setDuration(100, TimeUnit.MILLISECONDS);
        m3.setCount("traverserCount", 7);
        m3.setCount("elementCount", 7);
        m3.setAnnotation("percentDur", 25.0d);
        traversalMutableMetrics.add(m3);

        final MutableMetrics m4 = new MutableMetrics("4.0.0()", "TreeStep");
        m4.setDuration(100, TimeUnit.MILLISECONDS);
        m4.setCount("traverserCount", 1);
        m4.setCount("elementCount", 1);
        m4.setAnnotation("percentDur", 25.0d);
        traversalMutableMetrics.add(m4);

        return new DefaultTraversalMetrics(4000, traversalMutableMetrics);
    }

    public static Model instance() {
        return model;
    }

    public Set<String> groups() {
        return Collections.unmodifiableSet(entries.keySet());
    }

    public List<Entry> entries(final String key) {
        return Collections.unmodifiableList(entries.get(key));
    }

    public List<Entry> entries() {
        return Collections.unmodifiableList(entries.values().stream().flatMap(Collection::stream).collect(Collectors.toList()));
    }

    public Optional<Entry> find(final String resource) {
        return entries.values().stream().flatMap(Collection::stream).filter(e -> e.getResourceName().equals(resource)).findFirst();
    }

    private void addCoreEntry(final Object obj, final String title) {
        addEntry("Core", obj, title, "");
    }

    private void addCoreEntry(final Object obj, final String title, final Compatibility... incompatibleWith) {
        addEntry("Core", obj, title, "", incompatibleWith);
    }

    private void addCoreEntry(final Object obj, final String title, final Map<Compatibility, String> incompatibilityNotes, final Compatibility... incompatibleWith) {
        addEntry("Core", obj, title, "", incompatibilityNotes, incompatibleWith);
    }

    private void addGraphStructureEntry(final Object obj, final String title) {
        addGraphStructureEntry(obj, title, "");
    }

    private void addGraphStructureEntry(final Object obj, final String title, final String description, final Compatibility... incompatibilities) {
        addEntry("Graph Structure", obj, title, description, incompatibilities);
    }

    private void addGraphProcessEntry(final Object obj, final String title) {
        addGraphProcessEntry(obj, title, "");
    }

    private void addGraphProcessEntry(final Object obj, final String title, final String description) {
        addEntry("Graph Process", obj, title, description);
    }

    private void addGraphProcessEntry(final Object obj, final String title, final String description, final Map<Compatibility, String> incompatibilityNotes, final Compatibility... incompatibleWith) {
        addEntry("Graph Process", obj, title, description, incompatibilityNotes, incompatibleWith);
    }

    private void addRequestMessageEntry(final Object obj, final String title, final String description) {
        final List<Compatibility> incompatibilityList = Compatibilities.with(GryoCompatibility.class)
                .before("3.0")
                .match();
        incompatibilityList.addAll(Compatibilities.with(GraphSONCompatibility.class).configuredAs(".*partial.*").match());
        final Compatibility[] incompatibilities = new Compatibility[incompatibilityList.size()];
        incompatibilityList.toArray(incompatibilities);
        addEntry("RequestMessage", obj, title, description,
                createIncompatibilityMap("RequestMessage is not testable prior to Gryo 3.0 as serialization was handled by an intermediate component (MessageSerializer) that doesn't fit the test model.",  incompatibilities), incompatibilities);
    }

    private void addResponseMessageEntry(final Object obj, final String title, final String description) {
        final List<Compatibility> incompatibilityList = Compatibilities.with(GryoCompatibility.class)
                .before("3.0")
                .match();
        incompatibilityList.addAll(Compatibilities.with(GraphSONCompatibility.class).configuredAs(".*partial.*").match());

        // TODO: temporary problem? seems to be something breaking in vertex serialization
        if (title.equals("Standard Result"))
            incompatibilityList.addAll(Compatibilities.with(GraphSONCompatibility.class).configuredAs(".*no-types").match());


        final Compatibility[] incompatibilities = new Compatibility[incompatibilityList.size()];
        incompatibilityList.toArray(incompatibilities);
        addEntry("ResponseMessage", obj, title, description,
                createIncompatibilityMap("ResponseMessage is not testable prior to Gryo 3.0 as serialization was handled by an intermediate component (MessageSerializer) that doesn't fit the test model.", incompatibilities), incompatibilities);
    }

    private void addExtendedEntry(final Object obj, final String title) {
        addExtendedEntry(obj, title, "");
    }

    private void addExtendedEntry(final Object obj, final String title, final String description) {
        addEntry("Extended", obj, title, description);
    }

    private void addExtendedEntry(final Object obj, final String title, final String description, final Compatibility... incompatibleWith) {
        addEntry("Extended", obj, title, description, incompatibleWith);
    }
    
    private void addEntry(final String group, final Object obj, final String title, final String description) {
        addEntry(group, obj, title, description, ALL);
    }

    private void addEntry(final String group, final Supplier<?> maker, final String title, final String description, final Compatibility... incompatibleWith) {
        addEntry(group, null, title, description, Collections.unmodifiableList(ALL.stream()
                .filter(c -> !Arrays.asList(incompatibleWith).contains(c))
                .collect(Collectors.toList())), maker, null);
    }

    private void addEntry(final String group, final Supplier<?> maker, final String title, final String description,
                          final Map<Compatibility, String> incompatibilityNotes, final Compatibility... incompatibleWith) {
        addEntry(group, null, title, description, Collections.unmodifiableList(ALL.stream()
                .filter(c -> !Arrays.asList(incompatibleWith).contains(c))
                .collect(Collectors.toList())), maker, incompatibilityNotes);
    }

    private void addEntry(final String group, final Object obj, final String title, final String description,
                          final Map<Compatibility, String> incompatibilityNotes,
                          final Compatibility... incompatibleWith) {
        addEntry(group, obj, title, description, Collections.unmodifiableList(ALL.stream()
                .filter(c -> !Arrays.asList(incompatibleWith).contains(c))
                .collect(Collectors.toList())), null, incompatibilityNotes);
    }

    private void addEntry(final String group, final Object obj, final String title, final String description, final Compatibility... incompatibleWith) {
        addEntry(group, obj, title, description, Collections.unmodifiableList(ALL.stream()
                .filter(c -> !Arrays.asList(incompatibleWith).contains(c))
                .collect(Collectors.toList())), null, null);
    }

    private void addEntry(final String group, final Object obj, final String title, final String description,
                            final List<Compatibility> compatibleWith) {
        addEntry(group, obj, title, description, compatibleWith, null, null);
    }

    private void addEntry(final String group, final Object obj, final String title, final String description,
                          final List<Compatibility> compatibleWith, final Supplier<?> maker,
                          final Map<Compatibility, String> incompatibilityNotes) {
        if (!entries.containsKey(group))
            entries.put(group, new ArrayList<>());

        entries.get(group).add(new Entry(title, obj, description, compatibleWith, maker, incompatibilityNotes));
    }

    private Map<Compatibility, String> createIncompatibilityMap(final String msg, final Compatibility... incompatibilities) {
        final Map<Compatibility, String> m = new HashMap<>();
        Arrays.asList(incompatibilities).forEach(c -> m.put(c, msg));
        return m;
    }

    public void saveAsCsv(final String file) throws Exception {
        final File f = new File(file);
        f.getParentFile().mkdirs();

        final List<Compatibility> compatibilities = Stream.concat(
                Stream.of(GraphSONCompatibility.values()),
                Stream.of(GryoCompatibility.values())).collect(Collectors.toList());

        final List<String> headers = new ArrayList<>();
        headers.add("resource");
        headers.addAll(compatibilities.stream().map(c -> {
            if (c instanceof GryoCompatibility)
                return "gryo-" + ((GryoCompatibility) c).name();
            else if (c instanceof GraphSONCompatibility)
                return "graphson-" + ((GraphSONCompatibility) c).name();
            else
                throw new IllegalStateException("No support for the provided Compatibility type");
        }).collect(Collectors.toList()));

        try (final PrintWriter writer = new PrintWriter(f)) {
            writer.println(String.join(",", headers));

            final List<Entry> sorted = new ArrayList<>(entries());
            Collections.sort(sorted, (o1, o2) -> o1.getResourceName().compareTo(o2.getResourceName()));

            sorted.forEach(e -> {
                writer.write(e.getResourceName());
                writer.write(",");
                final List<String> compatibleList = new ArrayList<>();
                compatibilities.forEach(c -> compatibleList.add(Boolean.toString(e.isCompatibleWith(c))));
                writer.println(String.join(",", compatibleList));
            });
        }
    }

    public static class Entry {

        private final String title;
        private final Object object;
        private final String description;
        private final List<Compatibility> compatibleWith;
        private final Supplier<?> maker;
        private final Map<Compatibility, String> incompatibilityNotes;

        public Entry(final String title, final Object object, final String description,
                     final List<Compatibility> compatibleWith, final Supplier<?> maker) {
            this(title, object, description, compatibleWith, maker, Collections.emptyMap());
        }

        public Entry(final String title, final Object object, final String description,
                     final List<Compatibility> compatibleWith, final Supplier<?> maker,
                     final Map<Compatibility, String> incompatibilityNotes) {
            this.title = title;
            this.object = object;
            this.description = description;
            this.compatibleWith = compatibleWith;
            this.maker = maker;
            this.incompatibilityNotes = Optional.ofNullable(incompatibilityNotes).orElseGet(Collections::emptyMap);

            if (this.compatibleWith.stream().anyMatch(this.incompatibilityNotes::containsKey))
                throw new IllegalStateException("The " + title + " entry is marked as 'compatible' but it has incompatibility notes");
        }

        public Map<Compatibility, String> getIncompatibilityNotes() {
            return Collections.unmodifiableMap(incompatibilityNotes);
        }

        public String getTitle() {
            return title;
        }

        public String getResourceName() {
            return title.replace(" ", "").toLowerCase();
        }

        public <T> T getObject() {
            return (T) ((null == object) ? maker.get() : object);
        }

        public String getDescription() {
            return description;
        }

        public boolean isCompatibleWith(final Compatibility compatibility) {
            return compatibleWith.contains(compatibility);
        }

        public boolean hasGryoCompatibility() {
            return compatibleWith.stream().anyMatch(c -> c instanceof GryoCompatibility);
        }

        public boolean hasGraphSONCompatibility() {
            return compatibleWith.stream().anyMatch(c -> c instanceof GryoCompatibility);
        }
    }
}