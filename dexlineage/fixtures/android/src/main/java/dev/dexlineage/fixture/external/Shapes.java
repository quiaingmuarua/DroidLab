package dev.dexlineage.fixture.external;

/** Self-authored, framework-neutral input for an externally compiled extension. */
public final class Shapes {
    public static final class Product { public int value = 1; }
    public static final class Alternative { public int value = 2; }
    public static final class Factory {
        public static String marker() { return "fixture.external.factory"; }
        public static Product make() { return new Product(); }
    }
    public static final class AmbiguousFactory {
        public static String marker() { return "fixture.external.ambiguous"; }
        public static Product first() { return new Product(); }
        public static Alternative second() { return new Alternative(); }
    }
    public static final class UnknownFactory {
        public static String marker() { return "fixture.external.unknown"; }
        public static Product make() { return select(1); }
        private static Product select(int value) { return value == 0 ? null : new Product(); }
    }
}
