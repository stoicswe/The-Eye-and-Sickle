package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/**
 * {@code KEY: VALUE} — the readout the whole interface is made of.
 *
 * <h2>The unit is not optional</h2>
 *
 * {@code docs/design/ui-design-language.md} §4: "{@code CPU TEMP: 67.2C}, never
 * {@code Temperature: 67°}". Two rules are folded into that one example — the key is uppercase and
 * abbreviated like a machine wrote it, and <b>the value carries its unit</b>. A bare {@code 67} is
 * the readout of a dashboard that assumes you already know what it measures; this interface is
 * supposed to read as instrumentation that does not care whether you do.
 *
 * <h2>Amber is a claim about the model, not about importance</h2>
 *
 * {@link #live()} exists so a view has to say the value is <em>earning or doing work</em> before it
 * gets the accent (§2.1). It is not "highlight this". A panel where the important number is amber
 * and the unimportant one is grey has quietly turned the single accent into an emphasis system,
 * which is the failure §2.1's rules-of-use paragraph is written to prevent.
 */
public final class KeyValue extends HBox {

    private final Label valueLabel;

    private KeyValue(String key, String value) {
        super(UiTokens.SPACE_3);
        setAlignment(Pos.BASELINE_LEFT);
        Label keyLabel = Ui.label(key);
        keyLabel.getStyleClass().add("es-kv-key");
        this.valueLabel = Ui.value(value);
        getChildren().addAll(keyLabel, valueLabel);
    }

    public static KeyValue of(String key, String value) {
        return new KeyValue(key, value);
    }

    /** Key with no value of its own — for a key that labels an adjacent meter (§3's noise cell). */
    public static KeyValue keyOnly(String key) {
        KeyValue kv = new KeyValue(key, "");
        kv.getChildren().remove(kv.valueLabel);
        return kv;
    }

    /** Marks this value as live: cycles doing work, or income. Nothing else earns the accent. */
    public KeyValue live() {
        valueLabel.getStyleClass().add("es-value-live");
        return this;
    }

    /** Loss or hostile state. §2.1 caps this at two per screen — count them. */
    public KeyValue alarm() {
        valueLabel.getStyleClass().add("es-value-warn");
        return this;
    }

    public KeyValue dim() {
        valueLabel.getStyleClass().add("es-value-dim");
        return this;
    }

    /**
     * Replaces the figure.
     *
     * <p>A direct assignment, and that is the specification rather than laziness: §5 requires values
     * to <b>twitch</b> — jump to the new figure with no interpolation. Any tween added here would
     * violate the rejection list from a single line of code.
     */
    public void set(String value) {
        valueLabel.setText(value == null ? "" : value);
    }

    public String get() {
        return valueLabel.getText();
    }

    public Label valueNode() {
        return valueLabel;
    }
}
