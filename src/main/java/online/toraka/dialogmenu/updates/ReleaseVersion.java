package online.toraka.dialogmenu.updates;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Numeric release comparison; build metadata does not affect precedence. */
final class ReleaseVersion implements Comparable<ReleaseVersion> {
    private static final Pattern FORMAT =
            Pattern.compile(
                    "^[vV]?([0-9]+(?:\\.[0-9]+)*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+[0-9A-Za-z.-]+)?$");
    private final List<BigInteger> numbers;
    private final List<String> prerelease;

    private ReleaseVersion(List<BigInteger> numbers, List<String> prerelease) {
        this.numbers = numbers;
        this.prerelease = prerelease;
    }

    static Optional<ReleaseVersion> parse(String value) {
        if (value == null || value.length() > 128) {
            return Optional.empty();
        }
        var match = FORMAT.matcher(value.trim());
        if (!match.matches()) {
            return Optional.empty();
        }
        List<BigInteger> numbers = new ArrayList<>();
        for (String part : match.group(1).split("\\.")) {
            numbers.add(new BigInteger(part));
        }
        List<String> prerelease =
                match.group(2) == null ? List.of() : List.of(match.group(2).split("\\."));
        return Optional.of(new ReleaseVersion(numbers, prerelease));
    }

    @Override
    public int compareTo(ReleaseVersion other) {
        for (int i = 0; i < Math.max(numbers.size(), other.numbers.size()); i++) {
            BigInteger left = i < numbers.size() ? numbers.get(i) : BigInteger.ZERO;
            BigInteger right = i < other.numbers.size() ? other.numbers.get(i) : BigInteger.ZERO;
            int result = left.compareTo(right);
            if (result != 0) {
                return result;
            }
        }
        if (prerelease.isEmpty() || other.prerelease.isEmpty()) {
            return Boolean.compare(prerelease.isEmpty(), other.prerelease.isEmpty());
        }
        for (int i = 0; i < Math.min(prerelease.size(), other.prerelease.size()); i++) {
            String left = prerelease.get(i);
            String right = other.prerelease.get(i);
            boolean leftNumber = left.matches("[0-9]+");
            boolean rightNumber = right.matches("[0-9]+");
            int result;
            if (leftNumber && rightNumber) {
                result = new BigInteger(left).compareTo(new BigInteger(right));
            } else if (leftNumber != rightNumber) {
                result = leftNumber ? -1 : 1;
            } else {
                result = left.compareTo(right);
            }
            if (result != 0) {
                return result;
            }
        }
        return Integer.compare(prerelease.size(), other.prerelease.size());
    }
}
