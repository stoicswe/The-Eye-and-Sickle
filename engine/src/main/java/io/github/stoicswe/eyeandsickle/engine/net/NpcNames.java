package io.github.stoicswe.eyeandsickle.engine.net;

import java.util.List;
import java.util.Set;

/**
 * What the machines and the people on them are called.
 *
 * <h2>Two pools, one rule: derived from the address, never drawn</h2>
 *
 * Neither name costs an RNG draw. {@code TopologyGenerator}'s class note makes the draw count a pure
 * function of the world's shape — "draw unconditionally, discard conditionally" — and a name is
 * chosen once per host, so a draw here would add one per machine and re-roll every existing world.
 * {@code DocumentPool} already solves this exact problem the same way and for the same reason: hash
 * the address, index the pool. Determinism comes from the address, which is unique by construction,
 * so a machine has the same name on every load without anything being stored to make it so.
 *
 * <h2>⚠ FNV-1a, never {@code String.hashCode} — and this is measured, not assumed</h2>
 *
 * Addresses differ only in their last octet, and {@code String.hashCode} is {@code 31·h + c}, so
 * consecutive addresses land a fixed distance apart and a modulo walks the pool in lockstep with the
 * host index. Measured against the eight-name list this replaces, on the real address scheme:
 *
 * <pre>
 *   hashCode: wren dana kai morgan riley sasha toma ves morgan riley
 *   FNV-1a  : kai morgan riley sasha toma ves wren dana riley dana
 * </pre>
 *
 * The first row is the pool in order. Every server's operators arrived in the same rotation, offset
 * by the server index — so a player who learned one server's sequence knew every other server's, and
 * the "random" name was an index in disguise. {@code DocumentPool} carries the same warning about the
 * same trap; this is the second time it has bitten, which is why the hash lives here rather than in
 * each caller.
 *
 * <h2>The machine name is Docker's scheme, and the adjective rule is Docker's too</h2>
 *
 * {@code adjective-pioneer}, after {@code docker/pkg/namesgenerator} — an adjective and a figure from
 * computing, mathematics, physics, quantum mechanics or astronomy. It reads as a real fleet naming
 * convention because it is one, and it is a convention a player can go and meet on their own machine
 * the first time they run a container.
 *
 * <p>⚠ <b>NO ADJECTIVE MAY BE DEMEANING, and that is a hard rule rather than taste.</b> These are
 * real people; most of the pool is dead and a good deal of it is not, and pairing a real name with an
 * insult is a claim about a person the game has no business making. Docker learned this in public:
 * its generator carries a special case excluding {@code boring_wozniak}, with the comment that Steve
 * Wozniak is not boring. A rule over the whole list is the version of that fix which cannot be
 * defeated by adding one more word.
 *
 * <p>⚠ <b>The rule is "not demeaning", NOT "complimentary"</b> — widened on explicit direction, and
 * the distinction is the whole of it. Atmosphere is welcome and the pool is deliberately moody and a
 * little suggestive, because this is a game about breaking into other people's machines at night:
 * {@code sultry}, {@code roguish}, {@code wicked}, {@code clandestine}, {@code illicit-adjacent} words
 * all read as flavour on a hostname. What stays out is anything that would read as a <em>judgement of
 * the person</em> — incompetent, dull, ugly, cowardly, and the obvious slurs. The test for a candidate
 * is not "is it polite" but "would the surname's owner object to being described this way".
 *
 * <p>⚠ <b>Surnames only, and no name carrying a hyphen or a space.</b> Three reasons and each one is
 * load-bearing: the separator is a hyphen, so {@code bold-berners-lee} cannot be split back into its
 * two halves by anything reading it; RFC 1123 governs the result, because these names reach
 * {@code Hostname}'s vocabulary and a space is not a legal host label; and a bare surname makes no
 * claim about the person beyond the surname existing, so nothing here can state a real-world fact
 * that {@code docs/education}'s verification rule would have to stand behind.
 *
 * <h2>⚠ A machine name must NOT encode what the machine is</h2>
 *
 * Carried over from {@code HostArchetypes.hostLabel}, which this replaces, and it is the constraint
 * that decides the scheme. Naming a node's type is what the 15 EC Passive Sniffer sells
 * ({@code docs/design/07-recon-tools.md} §1), so a label like {@code sentry-04} would hand out a
 * purchased tool's entire product at the moment of discovery.
 *
 * <p>The scheme this replaced leaked it in a quieter way, which is worth recording because it is the
 * kind of thing that survives review: labels were {@code <server>-<index>} and the generator makes
 * host index 0 the gateway on every server, always. So {@code home-relay-00} was a free, reliable
 * "this is the gateway" for anyone who noticed the pattern — the sniffer's answer, given away by a
 * counter. An adjective and a surname correlate with nothing.
 */
public final class NpcNames {

    private NpcNames() {}

    /**
     * The people. Ordinary given names, lowercase, short enough to be real Unix account names.
     *
     * <h2>The pool is deliberately not from one place</h2>
     *
     * English, Nordic and Romance names were the original set; Korean, Japanese and Norwegian names were
     * added on explicit direction, then Chinese and Russian ones. A surveillance state's network is staffed by whoever
     * lives there, and a machine room where everybody is called Dana or Morgan reads as a set built
     * from one writer's address book.
     *
     * <p>⚠ <b>SEVEN CHARACTERS IS A HARD CEILING, and it is a LAYOUT contract, not a style rule.</b>
     * The account name shares the network map's address line with the address itself, and that line
     * is {@code UiTokens.NET_NODE_COLS} = 18 glyph cells: one for the selection gutter, up to nine for
     * the widest address this scheme can produce ({@code 10.6.0.51}, at the published cap of fifty
     * machines a server), one separator, leaving <b>seven</b>. An eighth character does not wrap, it
     * is silently clipped by {@code NetCanvas}, so a name would lose its last letter on the primary
     * surface with nothing reporting it. {@code ragnhild} and {@code torbjorn} were dropped from the
     * Norwegian set for exactly this and for no other reason. {@code NpcNamesTest} pins the ceiling.
     *
     * <p>⚠ <b>Deliberately not de-collided across the world.</b> Two machines each having an operator
     * called {@code dana} is what a network of a few hundred people actually looks like, and a
     * username is namespaced by the machine it is on — {@code dana@10.2.0.7} and {@code dana@10.4.0.3}
     * are not ambiguous. Forcing uniqueness would make the name a hidden identifier for the host,
     * which is the opposite of what it is.
     */
    private static final List<String> OPERATORS = List.of(
            "adae", "akane", "akira", "alyona", "anette", "anya", "aoi", "areum", "ari", "arkady", "astrid", "bex",
            "bing", "birger", "bjorn", "bo", "bodhi", "bohyun", "boram", "boris", "brede", "cato", "chaewon", "chen",
            "cheng", "cira", "daeun", "dagny", "daiki", "dain", "dana", "dasha", "dev", "dima", "dmitri", "dohyun",
            "dong", "einar", "eirik", "elke", "emi", "esme", "eunji", "eunwoo", "fang", "faro", "fedor", "feng",
            "fenna", "finn", "frida", "fumi", "galina", "gang", "geir", "gerd", "gil", "gleb", "gunnar", "guo", "gyuri",
            "haeun", "hale", "halvor", "hana", "hao", "haru", "haruto", "heng", "henrik", "hilde", "hinata", "hong",
            "hui", "hyejin", "hyunwoo", "igor", "ilma", "ines", "inger", "ingrid", "inna", "irina", "iseul", "ivar",
            "ivo", "jae", "jaehyun", "jaro", "jia", "jihoon", "jimin", "jing", "jinho", "jisoo", "jiwoo", "jonas",
            "jorun", "juan", "jun", "juno", "kai", "kaito", "kang", "kaori", "kari", "katya", "kazu", "keiko", "kenji",
            "kesh", "kira", "kirsi", "kjell", "klim", "knut", "kolya", "koto", "kun", "lan", "lars", "lasse", "lei",
            "leif", "lena", "lev", "li", "lian", "liang", "lida", "ling", "lior", "liv", "luo", "lyuba", "magnus",
            "mako", "maks", "marit", "mave", "mei", "meret", "mette", "michi", "midori", "mila", "min", "ming", "minho",
            "minjun", "minseo", "mira", "mirae", "misha", "morgan", "na", "nadia", "nadya", "nan", "nao", "naoki",
            "nari", "nayeon", "nell", "nika", "niko", "nils", "nina", "ning", "njal", "noa", "nobu", "odile", "ola",
            "olav", "oleg", "olga", "oren", "osamu", "oyvind", "pasha", "pavel", "peng", "per", "pia", "ping", "polina",
            "pyotr", "qi", "qiang", "qing", "quill", "quin", "ragnar", "raisa", "rei", "ren", "rhea", "riku", "riley",
            "rin", "rita", "roman", "rui", "rune", "ruslan", "sable", "saerom", "saki", "sakura", "sasha", "seojun",
            "seoyeon", "shan", "shin", "shiro", "shu", "sigrid", "sindre", "siv", "sofia", "sohee", "solveig", "song",
            "sonya", "sora", "soren", "soyeon", "stas", "sungmin", "sveta", "taeyang", "takumi", "tam", "tanya", "tao",
            "tariq", "taro", "tian", "timur", "ting", "toma", "tomo", "tosya", "tove", "trygve", "tsubasa", "uli",
            "ulla", "ulrik", "ume", "umi", "unni", "vadim", "valya", "vanya", "vegard", "vera", "ves", "vidar", "vika",
            "vilde", "vito", "vitya", "vlad", "wanda", "wataru", "wei", "wen", "wren", "wynn", "xan", "xia", "xiang",
            "xin", "xiu", "xue", "yan", "yao", "yara", "yeji", "yerin", "yi", "ying", "ylva", "yong", "yoshi", "yu",
            "yui", "yuki", "yulia", "yun", "yuna", "yuri", "yusuf", "yusuke", "yuto", "zara", "zeph", "zhao", "zhen",
            "zhi", "zhu", "zina", "zola", "zoya");

    /**
     * The adjectives. See the class note: <b>none of these may be pejorative</b>, because each one is
     * going to be attached to a real person's surname.
     */
    private static final List<String> ADJECTIVES = List.of(
            "admiring",
            "agile",
            "alluring",
            "amber",
            "ample",
            "ardent",
            "audacious",
            "blazing",
            "bold",
            "brash",
            "brave",
            "breezy",
            "bright",
            "brooding",
            "calm",
            "candid",
            "clandestine",
            "clever",
            "cobalt",
            "coral",
            "cosmic",
            "covert",
            "coy",
            "crimson",
            "cryptic",
            "crystal",
            "curious",
            "dapper",
            "daring",
            "dashing",
            "dazzling",
            "deft",
            "devoted",
            "dreamy",
            "dusky",
            "eager",
            "ebon",
            "elated",
            "electric",
            "elegant",
            "ember",
            "enchanted",
            "epic",
            "exact",
            "fearless",
            "feisty",
            "fervent",
            "fierce",
            "fleet",
            "flint",
            "flirty",
            "frosted",
            "gallant",
            "gentle",
            "ghostly",
            "gifted",
            "gilded",
            "glacial",
            "gleaming",
            "glossy",
            "glowing",
            "golden",
            "graceful",
            "granite",
            "happy",
            "hardy",
            "heady",
            "hidden",
            "hopeful",
            "humming",
            "hushed",
            "indigo",
            "intrepid",
            "ivory",
            "jaunty",
            "jolly",
            "jovial",
            "keen",
            "kindly",
            "languid",
            "lucid",
            "lucky",
            "lunar",
            "lush",
            "magnetic",
            "mellow",
            "midnight",
            "modest",
            "molten",
            "moonlit",
            "muted",
            "mystic",
            "nimble",
            "noble",
            "nocturnal",
            "nova",
            "obscure",
            "obsidian",
            "onyx",
            "opal",
            "opulent",
            "patient",
            "peaceful",
            "phantom",
            "plucky",
            "plush",
            "polar",
            "practical",
            "prismatic",
            "quartz",
            "quick",
            "quiet",
            "radiant",
            "rakish",
            "rapid",
            "reckless",
            "resolute",
            "restive",
            "restless",
            "roaring",
            "rogue",
            "roguish",
            "ruby",
            "russet",
            "sage",
            "sapphire",
            "saucy",
            "scarlet",
            "serene",
            "shadowed",
            "sharp",
            "shrouded",
            "silent",
            "silken",
            "silver",
            "sincere",
            "sleek",
            "smoky",
            "solar",
            "spectral",
            "spry",
            "stealthy",
            "stellar",
            "stoic",
            "storied",
            "sturdy",
            "sublime",
            "subtle",
            "sultry",
            "sumptuous",
            "sunny",
            "swift",
            "sylvan",
            "teal",
            "tempest",
            "tempting",
            "tender",
            "thriving",
            "tidal",
            "tranquil",
            "twilight",
            "umber",
            "undaunted",
            "unruly",
            "upbeat",
            "valiant",
            "veiled",
            "velvet",
            "verdant",
            "vesper",
            "vibrant",
            "vigilant",
            "vivid",
            "wandering",
            "wicked",
            "wild",
            "wily",
            "winsome",
            "wise",
            "wistful",
            "witty",
            "wondrous",
            "zephyr",
            "zesty");

    /**
     * The pioneers: computing, mathematics, physics, quantum mechanics and astronomy.
     *
     * <p>Surnames only, one word, letters only — see the class note for why all three matter. Where a
     * surname is shared by figures in more than one of the five fields ({@code thomson},
     * {@code hamilton}, {@code clarke}) that is a feature of the naming and not a collision: the pool
     * is a pool of names, and nothing downstream resolves one to a person.
     */
    private static final List<String> PIONEERS = List.of(
            // Computing
            "aiken",
            "allen",
            "babbage",
            "backus",
            "bartik",
            "cerf",
            "codd",
            "dijkstra",
            "eckert",
            "engelbart",
            "goldberg",
            "hamilton",
            "hamming",
            "hoare",
            "holberton",
            "hollerith",
            "hopper",
            "kay",
            "kernighan",
            "knuth",
            "lamport",
            "liskov",
            "lovelace",
            "mauchly",
            "mccarthy",
            "minsky",
            "perlis",
            "ritchie",
            "shannon",
            "stallman",
            "sutherland",
            "thompson",
            "torvalds",
            "turing",
            "wilkes",
            "wozniak",
            "zuse",
            // Cryptography
            "adleman",
            "brassard",
            "diffie",
            "hellman",
            "merkle",
            "rivest",
            "shamir",
            // Mathematics
            "abel",
            "archimedes",
            "banach",
            "bernoulli",
            "cantor",
            "cauchy",
            "descartes",
            "erdos",
            "euclid",
            "euler",
            "fermat",
            "fibonacci",
            "fourier",
            "galois",
            "gauss",
            "germain",
            "godel",
            "hilbert",
            "hypatia",
            "khwarizmi",
            "kolmogorov",
            "kovalevskaya",
            "laplace",
            "leibniz",
            "mandelbrot",
            "markov",
            "mirzakhani",
            "noether",
            "pascal",
            "poincare",
            "ramanujan",
            "riemann",
            "tarski",
            "uhlenbeck",
            // Physics
            "ampere",
            "bardeen",
            "bohr",
            "boltzmann",
            "born",
            "brattain",
            "chadwick",
            "coulomb",
            "curie",
            "dirac",
            "einstein",
            "faraday",
            "fermi",
            "feynman",
            "franklin",
            "goeppert",
            "heisenberg",
            "hertz",
            "joule",
            "kelvin",
            "landau",
            "lorentz",
            "maxwell",
            "meitner",
            "newton",
            "ohm",
            "pauli",
            "planck",
            "rutherford",
            "schrodinger",
            "shockley",
            "tesla",
            "thomson",
            "volta",
            "wu",
            "yalow",
            // Quantum
            "aspect",
            "bell",
            "bennett",
            "bose",
            "clauser",
            "deutsch",
            "everett",
            "grover",
            "haroche",
            "kitaev",
            "preskill",
            "shor",
            "wheeler",
            "wineland",
            "zeilinger",
            // Astronomy
            "brahe",
            "burnell",
            "cannon",
            "cassini",
            "chandrasekhar",
            "copernicus",
            "eddington",
            "galileo",
            "halley",
            "herschel",
            "hoyle",
            "hubble",
            "huygens",
            "kepler",
            "kuiper",
            "leavitt",
            "lemaitre",
            "messier",
            "mitchell",
            "oort",
            "payne",
            "penrose",
            "ptolemy",
            "rubin",
            "sagan",
            "shapley",
            "slipher",
            "somerville",
            "tombaugh",
            "zwicky");

    /** The pools, for tests and for anything that wants to report how large the space is. */
    public static List<String> operators() {
        return OPERATORS;
    }

    public static List<String> adjectives() {
        return ADJECTIVES;
    }

    public static List<String> pioneers() {
        return PIONEERS;
    }

    /**
     * The operator account on the machine at {@code address}.
     *
     * <p>A pure function of the address, so it survives a reload with nothing stored — which is what
     * makes {@code VirtualFs}' generated home directory stable across visits, and that stability is
     * what {@code docs/design/04-mining.md} §3.1's "was this here before?" is built on.
     */
    public static String operator(String address) {
        return OPERATORS.get((int) Math.floorMod(hash(address), (long) OPERATORS.size()));
    }

    /**
     * The name of the machine at {@code address}: {@code adjective-pioneer}.
     *
     * <p>⚠ <b>{@code taken} is what makes the name an identifier.</b> The pool is large — 184 × 159,
     * 29,256 combinations — but a world holds a few hundred machines, and the
     * birthday bound says a duplicate is not unlikely, it is expected. Two machines called
     * {@code bold-turing} on one map is worse than a dull name: the map, the list, the shell prompt
     * and the recon file would all show one string for two hosts, on the surface a player uses to
     * tell machines apart.
     *
     * <p>Resolved the way Docker resolves it — keep looking until the name is free — but
     * deterministically rather than by re-rolling: walk the adjectives from the hashed start, then
     * advance the pioneer and walk again. Called in the generator's canonical order (server ascending,
     * then host ascending) so the assignment is a pure function of the world's shape, exactly like the
     * draw counts around it.
     *
     * @param taken names already handed out; never modified here — the caller owns the set, because
     *     the caller is the only thing that knows when a name has actually been committed to a host
     */
    public static String machine(String address, Set<String> taken) {
        long h = hash(address);
        int adjective = (int) Math.floorMod(h, (long) ADJECTIVES.size());
        // A second, independent index off the same hash rather than a second hash: the two pools are
        // different sizes and co-prime enough that one mixed value indexes both without the pair
        // correlating. Shifted by 32 so the two indices do not both come off the low bits.
        int pioneer = (int) Math.floorMod(h >>> 32, (long) PIONEERS.size());

        for (int p = 0; p < PIONEERS.size(); p++) {
            for (int a = 0; a < ADJECTIVES.size(); a++) {
                String candidate = ADJECTIVES.get((adjective + a) % ADJECTIVES.size())
                        + "-"
                        + PIONEERS.get((pioneer + p) % PIONEERS.size());
                if (taken == null || !taken.contains(candidate)) {
                    return candidate;
                }
            }
        }
        // Unreachable for any world this generator builds — it would need every one of the ~14,000
        // combinations to be spoken for, against a published cap of fifty machines per server. It
        // exists because a hand-edited save is not this generator, and returning a duplicate would be
        // worse than returning something obviously synthetic.
        return "host-" + Long.toUnsignedString(h, 36);
    }

    /**
     * Whether {@code label} is a name this class could have produced.
     *
     * <p>Both halves have to be in the pools, so it is a statement about <em>this</em> generator
     * rather than a shape check — {@code some-thing} is the right shape and is not one of ours.
     * That matters because the one caller uses it to decide what to overwrite, and a shape check
     * would eventually rename something it did not put there.
     *
     * <p>⚠ It is deliberately <b>not</b> the inverse of "was generated by the old scheme". The old
     * labels were {@code <server name>-<NN>}, and testing for that would need this class to carry a
     * copy of {@code HostArchetypes.SERVER_NAMES} and the two-digit suffix — a description of a
     * format that no longer exists, kept in step by hand. Asking "is this one of mine" needs only
     * what is already here and stays true however many schemes came before.
     */
    public static boolean looksGenerated(String label) {
        if (label == null) {
            return false;
        }
        int split = label.indexOf('-');
        if (split <= 0 || split == label.length() - 1) {
            return false;
        }
        return ADJECTIVES.contains(label.substring(0, split)) && PIONEERS.contains(label.substring(split + 1));
    }

    /**
     * FNV-1a over the address, folded to 32 bits.
     *
     * <p>Identical to {@code DocumentPool.forAddress}'s hash, and deliberately so: both answer "which
     * entry of a fixed pool does this machine get", both are indexing off addresses that differ in one
     * octet, and having one of them quietly use a weaker mix is how the two would come to disagree
     * about whether the pools are evenly spread.
     */
    private static long hash(String address) {
        // ⚠ Moved to AddressHash 2026-08-07, byte-for-byte the same function. A third caller
        // (MonJobs) was about to make a third copy of it, which is the point at which "identical, and
        // deliberately so" becomes "nobody extracted it". Delegating rather than inlining keeps this
        // method as the documented entry point the class comment above still describes.
        return AddressHash.of(address);
    }
}
