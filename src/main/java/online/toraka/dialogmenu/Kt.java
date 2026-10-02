package online.toraka.dialogmenu;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small helpers that keep the exact semantics the Kotlin sources had (validation messages,
 * exception types, whitespace rules, splitting and ordered collections). Operators read these
 * messages, so they must not drift when the code moved to Java.
 */
final class Kt {

    private Kt() {}

    // ---- preconditions ------------------------------------------------------

    /** Kotlin {@code require}: {@link IllegalArgumentException} with the lazy message. */
    static void require(boolean condition, Supplier<String> message) {
        if (!condition) {
            throw new IllegalArgumentException(String.valueOf(message.get()));
        }
    }

    /** Kotlin {@code require} without a message. */
    static void require(boolean condition) {
        if (!condition) {
            throw new IllegalArgumentException("Failed requirement.");
        }
    }

    /** Kotlin {@code requireNotNull}: {@link IllegalArgumentException} with the lazy message. */
    static <T> T requireNotNull(T value, Supplier<String> message) {
        if (value == null) {
            throw new IllegalArgumentException(String.valueOf(message.get()));
        }
        return value;
    }

    /** Kotlin {@code requireNotNull} without a message. */
    static <T> T requireNotNull(T value) {
        if (value == null) {
            throw new IllegalArgumentException("Required value was null.");
        }
        return value;
    }

    /** Kotlin {@code check}: {@link IllegalStateException} with the lazy message. */
    static void check(boolean condition, Supplier<String> message) {
        if (!condition) {
            throw new IllegalStateException(String.valueOf(message.get()));
        }
    }

    /** Kotlin {@code error(message)}: use as {@code throw Kt.error("...")}. */
    static IllegalStateException error(String message) {
        return new IllegalStateException(message);
    }

    /** Kotlin {@code Map.getValue}: a missing key throws {@link NoSuchElementException}. */
    static <K, V> V getValue(Map<K, V> map, K key) {
        V value = map.get(key);
        if (value == null && !map.containsKey(key)) {
            throw new NoSuchElementException("Key " + key + " is missing in the map.");
        }
        return value;
    }

    /** Kotlin {@code List.first()}. */
    static <T> T first(List<T> list) {
        if (list.isEmpty()) {
            throw new NoSuchElementException("List is empty.");
        }
        return list.get(0);
    }

    /** Kotlin {@code List.last()}. */
    static <T> T last(List<T> list) {
        if (list.isEmpty()) {
            throw new NoSuchElementException("List is empty.");
        }
        return list.get(list.size() - 1);
    }

    /** Kotlin {@code List.single()}. */
    static <T> T single(List<T> list) {
        if (list.isEmpty()) {
            throw new NoSuchElementException("List is empty.");
        }
        if (list.size() > 1) {
            throw new IllegalArgumentException("List has more than one element.");
        }
        return list.get(0);
    }

    /** Kotlin {@code List.getOrNull(index)}. */
    static <T> T getOrNull(List<T> list, int index) {
        return index >= 0 && index < list.size() ? list.get(index) : null;
    }

    // ---- characters and strings (Kotlin rules, not java.lang rules) ---------

    /** Kotlin {@code Char.isWhitespace()} on the JVM. */
    static boolean isWhitespace(char character) {
        return Character.isWhitespace(character) || Character.isSpaceChar(character);
    }

    /** Kotlin {@code CharSequence.isBlank()}. */
    static boolean isBlank(CharSequence text) {
        for (int i = 0; i < text.length(); i++) {
            if (!isWhitespace(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** Kotlin {@code isNotBlank()}. */
    static boolean isNotBlank(CharSequence text) {
        return !isBlank(text);
    }

    /** Kotlin {@code String.trim()}: removes Kotlin whitespace (not every char below U+0020). */
    static String trim(String text) {
        int start = 0;
        int end = text.length() - 1;
        boolean startFound = false;
        while (start <= end) {
            int index = !startFound ? start : end;
            boolean match = isWhitespace(text.charAt(index));
            if (!startFound) {
                if (!match) {
                    startFound = true;
                } else {
                    start++;
                }
            } else {
                if (!match) {
                    break;
                } else {
                    end--;
                }
            }
        }
        return text.substring(start, end + 1);
    }

    /** True when no UTF-16 unit is an ISO control character. */
    static boolean noControl(CharSequence text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isISOControl(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** Kotlin {@code String.lowercase()} (locale independent). */
    static String lower(String text) {
        return text.toLowerCase(Locale.ROOT);
    }

    /** Kotlin {@code String.drop(n)}. */
    static String drop(String text, int count) {
        return text.substring(Math.min(count, text.length()));
    }

    /** Kotlin {@code String.take(n)}. */
    static String take(String text, int count) {
        return text.length() <= count ? text : text.substring(0, count);
    }

    /** Kotlin {@code substringBefore(delimiter)}: the whole text when the delimiter is absent. */
    static String substringBefore(String text, char delimiter) {
        int index = text.indexOf(delimiter);
        return index < 0 ? text : text.substring(0, index);
    }

    /** Kotlin {@code substringBefore(delimiter)} for a string delimiter. */
    static String substringBefore(String text, String delimiter) {
        int index = text.indexOf(delimiter);
        return index < 0 ? text : text.substring(0, index);
    }

    /** Kotlin {@code substringAfter(delimiter)}: the whole text when the delimiter is absent. */
    static String substringAfter(String text, char delimiter) {
        return substringAfter(text, delimiter, text);
    }

    /** Kotlin {@code substringAfter(delimiter, missingDelimiterValue)}. */
    static String substringAfter(String text, char delimiter, String missing) {
        int index = text.indexOf(delimiter);
        return index < 0 ? missing : text.substring(index + 1);
    }

    /** Kotlin {@code removePrefix}. */
    static String removePrefix(String text, String prefix) {
        return text.startsWith(prefix) ? text.substring(prefix.length()) : text;
    }

    /** Kotlin {@code removeSuffix}. */
    static String removeSuffix(String text, String suffix) {
        return text.endsWith(suffix) ? text.substring(0, text.length() - suffix.length()) : text;
    }

    /** Kotlin {@code split(char)}: every part, trailing empty parts included, no regex. */
    static List<String> split(String text, char delimiter) {
        return split(text, delimiter, 0);
    }

    /** Kotlin {@code split(char, limit)}; a limit of 0 means no limit. */
    static List<String> split(String text, char delimiter, int limit) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (limit == 0 || parts.size() < limit - 1) {
            int index = text.indexOf(delimiter, start);
            if (index < 0) {
                break;
            }
            parts.add(text.substring(start, index));
            start = index + 1;
        }
        parts.add(text.substring(start));
        return parts;
    }

    /** Kotlin {@code CharSequence.contains(other, ignoreCase = true)}. */
    static boolean containsIgnoreCase(String text, String other) {
        if (other.isEmpty()) {
            return true;
        }
        for (int i = 0; i + other.length() <= text.length(); i++) {
            if (text.regionMatches(true, i, other, 0, other.length())) {
                return true;
            }
        }
        return false;
    }

    /** Kotlin {@code String.equals(other, ignoreCase = true)}, null-safe like Kotlin. */
    static boolean equalsIgnoreCase(String text, String other) {
        if (text == null) {
            return other == null;
        }
        return other != null && text.equalsIgnoreCase(other);
    }

    /** Kotlin {@code toIntOrNull()}. */
    static Integer toIntOrNull(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    /** Kotlin {@code toDoubleOrNull()}. */
    static Double toDoubleOrNull(String text) {
        if (text == null) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    /** Kotlin's {@code IntRange.toString()}, as used in validation messages. */
    static String range(int first, int last) {
        return first + ".." + last;
    }

    /** Kotlin's {@code Regex.replace(input) { transform }}: the replacement is literal text. */
    static String replace(
            Pattern pattern, CharSequence input, Function<MatchResult, String> transform) {
        Matcher matcher = pattern.matcher(input);
        if (!matcher.find()) {
            return input.toString();
        }
        StringBuilder result = new StringBuilder(input.length());
        int last = 0;
        do {
            result.append(input, last, matcher.start());
            result.append(transform.apply(matcher.toMatchResult()));
            last = matcher.end();
        } while (last < input.length() && matcher.find());
        if (last < input.length()) {
            result.append(input, last, input.length());
        }
        return result.toString();
    }

    /** Kotlin {@code MatchResult.groupValues[index]}: an unmatched group is "". */
    static String group(MatchResult match, int index) {
        String value = match.group(index);
        return value == null ? "" : value;
    }

    // ---- ordered, null-tolerant collections (Kotlin listOf/setOf/mapOf) ----

    /** An unmodifiable list that, unlike {@code List.of}, tolerates {@code contains(null)}. */
    @SafeVarargs
    static <T> List<T> listOf(T... values) {
        return Collections.unmodifiableList(Arrays.asList(values.clone()));
    }

    /** An insertion-ordered, unmodifiable set (Kotlin {@code setOf}). */
    @SafeVarargs
    static <T> Set<T> setOf(T... values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(values)));
    }

    /** An insertion-ordered, unmodifiable map from alternating keys and values. */
    @SuppressWarnings("unchecked")
    static <K, V> Map<K, V> mapOf(Object... pairs) {
        Map<K, V> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put((K) pairs[i], (V) pairs[i + 1]);
        }
        return Collections.unmodifiableMap(map);
    }

    /** Kotlin {@code Set - Collection}: keeps the order of {@code values}. */
    static <T> Set<T> minus(Collection<T> values, Collection<?> removed) {
        Set<T> result = new LinkedHashSet<>(values);
        result.removeAll(removed);
        return result;
    }

    /** Kotlin {@code List + List}. */
    static <T> List<T> plus(List<? extends T> first, List<? extends T> second) {
        List<T> result = new ArrayList<>(first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return result;
    }

    // ---- files (kotlin.io semantics: same exceptions, lenient UTF-8 decoding) ----

    /** Rethrows any throwable unchanged, like Kotlin, which has no checked exceptions. */
    @SuppressWarnings("unchecked")
    static <E extends Throwable> RuntimeException sneaky(Throwable error) throws E {
        throw (E) error;
    }

    /** Kotlin {@code File.readText(Charsets.UTF_8)}. */
    static String readText(File file) {
        try (InputStream input = new FileInputStream(file)) {
            return MenuFiles.decode(input.readAllBytes());
        } catch (IOException error) {
            throw sneaky(error);
        }
    }

    /** Kotlin {@code File.writeText(text, Charsets.UTF_8)}. */
    static void writeText(File file, String text) {
        try {
            dev.keystone.storage.StorageWriter.writeAtomic(file, text);
        } catch (IOException error) {
            throw sneaky(error);
        }
    }

    /** Kotlin {@code File.extension}. */
    static String extension(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }

    /** Kotlin {@code File.nameWithoutExtension}. */
    static String nameWithoutExtension(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }
}
