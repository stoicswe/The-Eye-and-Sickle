package io.github.stoicswe.eyeandsickle.solo;

import io.github.stoicswe.eyeandsickle.solo.save.SaveStore;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NetProbe {
    static final class Clk extends java.time.Clock {
        Instant now = Instant.parse("2026-07-27T09:00:00Z");
        public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
        public java.time.Clock withZone(java.time.ZoneId z) { return this; }
        public Instant instant() { return now; }
    }

    @Test
    void scanDiscovers(@TempDir Path dir) {
        Clk clk = new Clk();
        SoloGame g = SoloGame.open(new SaveStore(dir.resolve("s.json")), "op", clk);
        System.out.println("hosts at start: " + g.state().knownNodes.size());
        System.out.println("topology servers: "
                + (g.state().topology == null ? "NULL" : g.state().topology.servers.size()));

        System.out.println("--- SWEEP (the network verb) ---");
        for (io.github.stoicswe.eyeandsickle.solo.net.SweepTier t
                : io.github.stoicswe.eyeandsickle.solo.net.SweepTier.values()) {
            System.out.println("  owns " + t + ": " + g.ownsSweep(t));
        }
        var sw = g.sweep(io.github.stoicswe.eyeandsickle.solo.net.SweepTier.values()[0]);
        System.out.println("sweep started: " + sw.isPresent()
                + "  hasNetwork=" + g.hasNetwork());
        System.out.println("tasks: " + g.state().tasks.size());
        g.state().tasks.forEach(t -> System.out.println("  task kind=" + t.kind + " ends " + t.endsAt));

        for (int i = 0; i < 40; i++) {
            clk.now = clk.now.plus(Duration.ofSeconds(30));
            g.tick();
            if (g.state().tasks.isEmpty()) {
                System.out.println("task settled after " + ((i + 1) * 30) + "s");
                break;
            }
        }
        System.out.println("hosts after scan: " + g.state().knownNodes.size());
        System.out.println("remaining tasks: " + g.state().tasks.size());
        g.log().forEach(l -> System.out.println("  log " + l.facility + ": " + l.message));

        System.out.println("--- BREACH ---");
        var targets = g.breachTargets();
        System.out.println("breach targets: " + targets.size());
        targets.stream().limit(4).forEach(t -> System.out.println("  target " + t));
        if (!targets.isEmpty()) {
            String id = targets.get(0).targetId();
            var res = g.beginBreach(id);
            System.out.println("beginBreach(" + id + "): ok=" + res.applied() + " msg=" + res.message());
            System.out.println("snapshot present: " + g.breachSnapshot().isPresent());
            System.out.println("actions: " + g.breachActions().size());
        }
    }
}
