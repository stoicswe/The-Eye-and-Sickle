package io.github.stoicswe.eyeandsickle.engine.stocks;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns a real company name into a near-synonym of itself.
 *
 * <h2>What this is for</h2>
 *
 * The <b>symbol</b> is real, because a player looking up {@code AAPL} should get the thing {@code
 * AAPL} tracks. The <b>name</b> is not, because the game is not about real companies and putting
 * their marks on a darknet brokerage in a surveillance dystopia says something about them that the
 * game does not mean. So each word is swapped for one that means roughly the same thing: the result
 * is recognisable, obviously a joke, and nobody's trademark.
 *
 * <h2>⚠ DETERMINISTIC, and that is not a nicety</h2>
 *
 * A symbol must alias to the same name every time — across repaints, sessions and machines.
 * A player builds a portfolio against names they have learned; a name that drifted would make their
 * own holdings unrecognisable, and two players comparing notes would be describing different
 * markets. Everything here is a pure function of the input string.
 *
 * <h2>⚠ The fallback is a MAPPING, never a random pick</h2>
 *
 * An unknown word is hashed into a themed list rather than drawn, for the same reason. It also means
 * the vocabulary can grow — adding a word to {@link #SYNONYMS} changes that one company's name and
 * nothing else, because nothing downstream stores the output.
 */
public final class Aliaser {

    private Aliaser() {}

    /**
     * Word-for-word substitutions.
     *
     * <p>Ordered, and consulted longest-first, so a multi-word morpheme wins over its parts —
     * "General Electric" should not become two unrelated jokes.
     */
    private static final Map<String, String> SYNONYMS = new LinkedHashMap<>();

    static {
        // Fruit, animals, materials — the recognisable stems.
        SYNONYMS.put("apple", "Pear");
        SYNONYMS.put("amazon", "Congo");
        // ⚠ A dot-com tail is part of the name, not punctuation — dropping it would leave "Congo Ltd"
        // and lose the era the name is from.
        SYNONYMS.put("com", "Web");
        SYNONYMS.put("alphabet", "Lexicon");
        SYNONYMS.put("meta", "Beyond");
        SYNONYMS.put("microsoft", "Nanosilk");
        SYNONYMS.put("micro", "Nano");
        SYNONYMS.put("soft", "Silk");
        SYNONYMS.put("oracle", "Prophet");
        SYNONYMS.put("tesla", "Volta");
        SYNONYMS.put("nvidia", "Invidia");
        SYNONYMS.put("intel", "Notion");
        SYNONYMS.put("broadcom", "Widecast");
        SYNONYMS.put("netflix", "Webreel");
        SYNONYMS.put("adobe", "Claybrick");
        SYNONYMS.put("salesforce", "Tradecorps");
        SYNONYMS.put("cisco", "Frisco");
        SYNONYMS.put("qualcomm", "Merittalk");
        SYNONYMS.put("palantir", "Seeingstone");
        SYNONYMS.put("shopify", "Storeify");
        SYNONYMS.put("spotify", "Notify");
        SYNONYMS.put("uber", "Super");
        SYNONYMS.put("airbnb", "Aircot");
        SYNONYMS.put("boeing", "Roaring");
        SYNONYMS.put("ford", "Crossing");
        SYNONYMS.put("delta", "Estuary");
        SYNONYMS.put("visa", "Permit");
        SYNONYMS.put("mastercard", "Overseerplate");
        SYNONYMS.put("paypal", "Wagechum");
        SYNONYMS.put("chase", "Pursuit");
        SYNONYMS.put("goldman", "Aurumman");
        SYNONYMS.put("morgan", "Marrow");
        SYNONYMS.put("wells", "Springs");
        SYNONYMS.put("fargo", "Faraway");
        SYNONYMS.put("target", "Bullseye");
        SYNONYMS.put("walmart", "Wallbazaar");
        SYNONYMS.put("costco", "Priceco");
        SYNONYMS.put("nike", "Victory");
        SYNONYMS.put("starbucks", "Sundollars");
        SYNONYMS.put("disney", "Dizzy");
        SYNONYMS.put("pfizer", "Physik");
        SYNONYMS.put("moderna", "Recenta");
        SYNONYMS.put("johnson", "Jackson");
        SYNONYMS.put("general", "Colonel");
        SYNONYMS.put("electric", "Galvanic");
        SYNONYMS.put("motors", "Engines");
        SYNONYMS.put("dynamics", "Kinetics");
        SYNONYMS.put("systems", "Frameworks");
        SYNONYMS.put("technologies", "Craftworks");
        SYNONYMS.put("technology", "Craftwork");
        SYNONYMS.put("digital", "Numeric");
        SYNONYMS.put("data", "Records");
        SYNONYMS.put("energy", "Vigour");
        SYNONYMS.put("petroleum", "Rockoil");
        SYNONYMS.put("mobil", "Movil");
        SYNONYMS.put("exxon", "Vexxon");
        SYNONYMS.put("chevron", "Stripes");
        SYNONYMS.put("pharmaceuticals", "Apothecaries");
        SYNONYMS.put("pharma", "Apothecary");
        SYNONYMS.put("health", "Wellness");
        SYNONYMS.put("bank", "Vault");
        SYNONYMS.put("financial", "Fiscal");
        SYNONYMS.put("capital", "Principal");
        SYNONYMS.put("holdings", "Estates");
        SYNONYMS.put("group", "Cohort");
        SYNONYMS.put("global", "Worldwide");
        SYNONYMS.put("international", "Transnational");
        SYNONYMS.put("industries", "Manufactories");
        SYNONYMS.put("communications", "Dispatches");
        SYNONYMS.put("networks", "Meshes");
        SYNONYMS.put("labs", "Workshops");
        SYNONYMS.put("laboratories", "Workshops");
        SYNONYMS.put("devices", "Apparatus");
        SYNONYMS.put("materials", "Substances");
        SYNONYMS.put("semiconductor", "Halfconductor");
        SYNONYMS.put("advanced", "Forward");
        SYNONYMS.put("micro devices", "Nano Apparatus");
        SYNONYMS.put("machines", "Engines");
        SYNONYMS.put("business", "Trade");
        SYNONYMS.put("stores", "Emporia");
        SYNONYMS.put("airlines", "Skylines");
        SYNONYMS.put("air", "Sky");
        SYNONYMS.put("lines", "Routes");
        SYNONYMS.put("foods", "Provisions");
        SYNONYMS.put("beverage", "Draught");
        SYNONYMS.put("coca", "Kola");
        SYNONYMS.put("cola", "Fizz");
        SYNONYMS.put("pepsi", "Zestco");
        SYNONYMS.put("home", "Hearth");
        SYNONYMS.put("depot", "Warehouse");
        SYNONYMS.put("lowe", "Vale");
        SYNONYMS.put("solutions", "Remedies");
        SYNONYMS.put("services", "Offices");
        SYNONYMS.put("enterprises", "Ventures");
        SYNONYMS.put("platforms", "Stages");
        SYNONYMS.put("interactive", "Responsive");
        SYNONYMS.put("entertainment", "Diversions");
        SYNONYMS.put("media", "Broadcast");
        SYNONYMS.put("studios", "Ateliers");
        SYNONYMS.put("gaming", "Play");
        SYNONYMS.put("cloud", "Vapour");
        SYNONYMS.put("security", "Safekeeping");
        SYNONYMS.put("software", "Program");
        SYNONYMS.put("hardware", "Ironmongery");
        SYNONYMS.put("mining", "Delving");
        SYNONYMS.put("resources", "Stocks");
        SYNONYMS.put("gold", "Aurum");
        SYNONYMS.put("silver", "Argent");
        SYNONYMS.put("steel", "Ironwork");
        SYNONYMS.put("motor", "Engine");
        SYNONYMS.put("automotive", "Carriage");
        SYNONYMS.put("logistics", "Haulage");
        SYNONYMS.put("transport", "Carriage");
        SYNONYMS.put("railway", "Ironroad");
        SYNONYMS.put("union", "Guild");
        SYNONYMS.put("pacific", "Peaceful");
        SYNONYMS.put("atlantic", "Tidal");
        SYNONYMS.put("northern", "Boreal");
        SYNONYMS.put("southern", "Austral");
        SYNONYMS.put("eastern", "Orient");
        SYNONYMS.put("western", "Occident");
        SYNONYMS.put("first", "Premier");
        SYNONYMS.put("american", "Columbian");
        SYNONYMS.put("national", "Civic");
        SYNONYMS.put("standard", "Ordinary");
        SYNONYMS.put("dynamic", "Kinetic");
        SYNONYMS.put("power", "Might");
        SYNONYMS.put("light", "Lumen");
        SYNONYMS.put("water", "Aqua");
        SYNONYMS.put("waste", "Refuse");
        SYNONYMS.put("management", "Stewardship");
        SYNONYMS.put("insurance", "Assurance");
        SYNONYMS.put("realty", "Landright");
        SYNONYMS.put("properties", "Estates");
        SYNONYMS.put("trust", "Confidence");
    }

    /**
     * Corporate suffixes, mapped rather than dropped.
     *
     * <p>⚠ Kept, because the suffix is most of what makes a string read as a <em>company</em>. A name
     * with it stripped reads as a word.
     */
    private static final Map<String, String> SUFFIXES = Map.of(
            "inc", "Ltd",
            "inc.", "Ltd",
            "corp", "Consortium",
            "corp.", "Consortium",
            "co", "Company",
            "co.", "Company",
            "plc", "PLC",
            "ltd", "Incorporated",
            "sa", "SA",
            "nv", "NV");

    /**
     * Words an unknown token is mapped into.
     *
     * <p>Deliberately bland and corporate-sounding: the joke is the whole name, and a fallback that
     * tried to be funny on its own would make every unrecognised company louder than the ones the
     * table actually knows.
     */
    private static final List<String> FALLBACK = List.of(
            "Ridge", "Harbour", "Beacon", "Meridian", "Summit", "Anchor", "Lantern", "Compass",
            "Bastion", "Cardinal", "Kestrel", "Quarry", "Foundry", "Cinder", "Marrow", "Thicket",
            "Pallas", "Vellum", "Obsidian", "Larkspur", "Ironbark", "Sablewood", "Halcyon", "Verdant");

    /**
     * The name this symbol trades under here.
     *
     * @param realName the company's actual name
     * @param symbol the ticker, used only to seed the fallback so two companies sharing a bland word
     *     do not both become the same thing
     * @return the aliased name
     */
    public static String alias(String realName, String symbol) {
        if (realName == null || realName.isBlank()) {
            return fallback(symbol == null ? "" : symbol, 0);
        }
        // ⚠ Split on DOTS AND HYPHENS as well as spaces. Real names carry both — "Amazon.com",
        // "Coca-Cola" — and a whitespace-only split hands the table "amazon.com" and "coca-cola",
        // which match nothing and fall through to the bland fallback. Both looked like the aliaser
        // simply had no entry for a household name.
        //
        // ⚠ The corporate suffix survives it: "Inc." splits to "inc", which SUFFIXES knows.
        String[] words = realName.trim().split("[\\s.\\-]+");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String raw = words[i];
            String key = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
            String mapped;
            if (SUFFIXES.containsKey(key)) {
                mapped = SUFFIXES.get(key);
            } else if (SYNONYMS.containsKey(key)) {
                mapped = SYNONYMS.get(key);
            } else if (key.isEmpty() || key.length() <= 2) {
                // "&", "of", "de" — structural glue. Kept as-is; swapping it produces gibberish
                // rather than a name.
                mapped = raw;
            } else {
                mapped = fallback(symbol + ":" + key, i);
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(mapped);
        }
        return out.toString();
    }

    private static String fallback(String seed, int position) {
        long h = hash(seed.hashCode(), position);
        return FALLBACK.get((int) Math.floorMod(h, FALLBACK.size()));
    }

    /** SplitMix64's finaliser — deterministic across JVMs, unlike {@code String.hashCode} alone. */
    private static long hash(long seed, long a) {
        long x = seed * 0x9E3779B97F4A7C15L + a * 0xBF58476D1CE4E5B9L;
        x ^= (x >>> 30);
        x *= 0xBF58476D1CE4E5B9L;
        x ^= (x >>> 27);
        x *= 0x94D049BB133111EBL;
        x ^= (x >>> 31);
        return x;
    }
}
