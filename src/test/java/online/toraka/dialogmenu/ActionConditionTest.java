package online.toraka.dialogmenu;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ActionConditionTest {

    private static final Function<String, Boolean> PERMISSIONS = Set.of("vip.use")::contains;
    private static final Function<String, String> PAPI =
            Map.of("%level%", " 12 ", "%rank%", "gold")::get;

    private static boolean holds(Object condition) {
        return holds(condition, Map.of());
    }

    private static boolean holds(Object condition, Map<String, String> named) {
        return ActionCondition.parse(condition, "test", null).test(PERMISSIONS, PAPI, named);
    }

    @Test
    @DisplayName("perm, placeholder comparisons and not combine; every clause must hold")
    void clausesCombine() {
        assertTrue(holds("perm vip.use"));
        assertTrue(holds("permission *vip.use"));
        assertFalse(holds("perm admin"));
        assertTrue(holds("not perm admin"));
        assertTrue(holds("%level% >= 10"));
        assertFalse(holds("%level% > 12"));
        assertTrue(holds("%rank% = gold"));
        assertTrue(holds("%rank% != iron"));
        assertFalse(holds("%rank% > 3"));
        assertTrue(holds(List.of("perm vip.use", "%level% < 20")));
        assertFalse(holds(List.of("perm vip.use", "%level% < 5")));
        assertTrue(holds("NOT %level% < 5"));
    }

    @Test
    @DisplayName("an unavailable placeholder never holds, not even negated")
    void unavailablePlaceholderNeverHolds() {
        assertFalse(holds("%missing% = x"));
        assertFalse(holds("not %missing% = x"));
        assertFalse(holds("%missing% != x"));
    }

    @Test
    @DisplayName("declared names go through the menu's validator and read the named values")
    void declaredNamesUseTheValidator() {
        ActionCondition condition =
                ActionCondition.parse(
                        "mode=hard",
                        "test",
                        clause -> clause.name().equals("mode") ? null : "未声明 " + clause.name());
        assertTrue(condition.readsNames());
        assertTrue(condition.test(PERMISSIONS, PAPI, Map.of("mode", "hard")));
        assertFalse(condition.test(PERMISSIONS, PAPI, Map.of("mode", "easy")));
        assertFalse(condition.test(PERMISSIONS, PAPI, Map.of()));
        assertFalse(ActionCondition.parse("perm a", "test", null).readsNames());
    }

    @Test
    @DisplayName("bad clauses explain what is allowed")
    void badClausesExplain() {
        for (String bad :
                List.of(
                        "mode=hard",
                        "perm",
                        "perm two words",
                        "%level% >= ten",
                        "%level% == 3",
                        "just text",
                        "")) {
            Exception error =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> ActionCondition.parse(bad, "here", null),
                            bad);
            assertTrue(Objects.requireNonNull(error.getMessage()).startsWith("here"), bad);
        }
        assertThrows(Exception.class, () -> ActionCondition.parse(3, "here", null));
    }
}
