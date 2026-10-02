package online.toraka.dialogmenu;

import java.util.regex.Pattern;

public final class CommandTemplate {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{player}|\\{uuid}");

    private CommandTemplate() {}

    public static String render(String template, String player, String uuid) {
        return Kt.replace(
                PLACEHOLDER, template, match -> match.group().equals("{player}") ? player : uuid);
    }
}
