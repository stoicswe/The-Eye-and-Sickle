package io.github.stoicswe.eyeandsickle.client.ui.netmap;

import java.util.Set;

/**
 * Prints the map as text, for a human to look at.
 *
 * <p>The grid <em>is</em> the rendering, so this needs no toolkit and no window — it is the cheapest
 * possible answer to "what does that actually draw", which is the question every geometric decision in
 * {@code docs/client/09-network-map-graph.md} turns on and which no assertion answers on its own.
 *
 * <pre>{@code
 * mvn -pl client test-compile
 * mvn -pl client exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=io.github.stoicswe.eyeandsickle.client.ui.netmap.NetDump
 * }</pre>
 */
public final class NetDump {

    private NetDump() {}

    public static void main(String[] args) {
        show("twoHops", NetCanvas.frame(NetFixtures.twoHops(), 0));
        show("opening", NetCanvas.frame(NetFixtures.opening(), 0));
        show("estate(7) collapsed", NetCanvas.frame(NetFixtures.estate(7), 0));
        show(
                "estate(7) expanded",
                NetCanvas.frame(NetFixtures.estate(7), 0, "", Set.of(NetLayout.stackId("10.0.0.2"))));
    }

    private static void show(String title, String frame) {
        System.out.println("── " + title + " " + "─".repeat(Math.max(0, 60 - title.length())));
        System.out.println(frame);
    }
}
