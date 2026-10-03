package online.toraka.dialogmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TemplatePlaceholderTest {

    private final String base =
            """
            MenuType: dialog
            Version: 1
            Skin: parchment
            Variables:
              mood: [calm, angry]
            Placeholders:
              rank: "%luckperms_primary_group_name%"
              level: "%player_level%"
              quest: "%quest_stage%"
            Elements:
              portrait:
                Type: sprite
                Position: [24, 3]
                Font: "demo:portraits"
                Width: 108
                Rows: 12
                Advance: 109
                Glyph: "\\uE001"
                Cases:
                  - When: "quest>=3"
                    Glyph: "\\uE003"
                  - When: [mood=angry, "rank!=vip"]
                    Glyph: "\\uE002"
                    Advance: 100
              badge:
                Type: sprite
                Position: [150, 3]
                Sprite: button
                Cases:
                  - When: rank=vip
                    Sprite: selected
              photo:
                Type: sprite
                Position: [300, 3]
                Width: 64
                Rows: 8
                Image: "CE:demo:elf_calm"
                Cases:
                  - When: "level>=30"
                    Image: "IA:demo:elf_smile"
              greeting:
                Position: [150, 14]
                Width: 300
                Text: "{player} {rank} {mood}"
              low:
                Type: button
                Position: [150, 17]
                Text: 等级不足
                VisibleWhen: "level<30"
                Actions: ["message: 需要 30 级 {rank}"]
              accept:
                Type: button
                Position: [150, 17]
                Text: 接受
                VisibleWhen: ["level>=30", "quest=0"]
                Actions: ["console: quest start {player} {mood}", "close"]""";

    private DialogTemplate parse() {
        return parse(base);
    }

    private DialogTemplate parse(String source) {
        return TemplateParser.parse("npc", source);
    }

    private static MenuCondition condition(String... clauses) {
        List<MenuCondition.Clause> parsed = new ArrayList<>();
        for (String clause : clauses) {
            parsed.add(Objects.requireNonNull(MenuCondition.clause(clause)));
        }
        return new MenuCondition(parsed);
    }

    private static List<Component> flatten(Component component) {
        List<Component> result = new ArrayList<>();
        result.add(component);
        for (Component child : component.children()) {
            result.addAll(flatten(child));
        }
        return result;
    }

    private static Component render(DialogTemplate template, Map<String, String> values) {
        return render(template, values, new RichMenuText());
    }

    private static Component render(
            DialogTemplate template, Map<String, String> values, RichMenuText rich) {
        return TemplateRenderer.render(
                        template,
                        values,
                        it -> it,
                        rich,
                        it -> DialogClicks.custom(Key.key("test", it)))
                .build();
    }

    private static List<String> glyphs(Component component, Key font) {
        return flatten(component).stream()
                .filter(it -> Objects.equals(it.font(), font))
                .map(it -> ((TextComponent) it).content())
                .toList();
    }

    private static TemplateElement single(DialogTemplate template, String id) {
        return Kt.single(template.elements().stream().filter(it -> it.id().equals(id)).toList());
    }

    /** Kotlin {@code String.split(delimiter)}: literal, trailing empty parts kept. */
    private static List<String> split(String text, String delimiter) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        int index = text.indexOf(delimiter);
        while (index >= 0) {
            parts.add(text.substring(start, index));
            start = index + delimiter.length();
            index = text.indexOf(delimiter, start);
        }
        parts.add(text.substring(start));
        return parts;
    }

    @Test
    @DisplayName("conditions compare text and numbers and unresolved placeholders never hold")
    void conditionsCompareTextAndNumbersAndUnresolvedPlaceholdersNeverHold() {
        DialogTemplate template = parse();
        TemplateElement low = single(template, "low");
        TemplateElement accept = single(template, "accept");
        assertFalse(TemplateRenderer.visible(low, Map.of("mood", "calm")));
        assertFalse(TemplateRenderer.visible(accept, Map.of("mood", "calm")));
        assertTrue(TemplateRenderer.visible(low, Map.of("level", "-3")));
        assertTrue(TemplateRenderer.visible(accept, Map.of("level", "30", "quest", "0")));
        assertFalse(TemplateRenderer.visible(accept, Map.of("level", "29.5", "quest", "0")));
        assertFalse(TemplateRenderer.visible(accept, Map.of("level", "30", "quest", "1")));
        // Formatted numbers are not numbers; neither branch claims them.
        assertFalse(TemplateRenderer.visible(accept, Map.of("level", "1,000", "quest", "0")));
        assertFalse(TemplateRenderer.visible(low, Map.of("level", "1,000")));
        assertEquals(
                List.of("level", "quest"),
                Objects.requireNonNull(accept.condition()).clauses().stream()
                        .map(MenuCondition.Clause::name)
                        .toList());
        assertEquals("rank", template.placeholders().keySet().iterator().next());
    }

    @Test
    @DisplayName("overlapping buttons are accepted only when their conditions provably exclude")
    void overlappingButtonsAreAcceptedOnlyWhenTheirConditionsProvablyExclude() {
        assertTrue(condition("level<30").excludes(condition("level>=30")));
        assertTrue(condition("level<30").excludes(condition("level>30")));
        assertFalse(condition("level<=30").excludes(condition("level>=30")));
        assertTrue(condition("level=30").excludes(condition("level<30")));
        assertFalse(condition("level=30").excludes(condition("level<=30")));
        assertFalse(condition("level=abc").excludes(condition("level<30")));
        assertTrue(condition("rank=vip").excludes(condition("rank!=vip")));
        assertTrue(condition("rank=vip").excludes(condition("rank=mvp")));
        assertFalse(condition("rank!=vip").excludes(condition("rank!=mvp")));
        assertFalse(condition("rank=vip").excludes(condition("level<30")));
        assertTrue(condition("level>=30", "quest=0").excludes(condition("level<30")));
        // Legacy variable conditions keep their original exclusivity rule.
        assertTrue(condition("difficulty=hard").excludes(condition("difficulty=normal")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        parse(
                                base.replace(
                                        "VisibleWhen: \"level<30\"", "VisibleWhen: \"level<40\"")));
        parse(base.replace("VisibleWhen: \"level<30\"", "VisibleWhen: \"level<=29\""));
    }

    @Test
    @DisplayName("sprite cases pick the first match and fall back to the default look")
    void spriteCasesPickTheFirstMatchAndFallBackToTheDefaultLook() {
        DialogTemplate template = parse();
        TemplateElement portrait = single(template, "portrait");
        assertEquals(
                List.of(109), Objects.requireNonNull(portrait.cases().get(0).sprite()).advances());
        assertEquals(
                List.of(100), Objects.requireNonNull(portrait.cases().get(1).sprite()).advances());
        Key font = Key.key("demo:portraits");
        Function<Map<String, String>, List<String>> portraitGlyphs =
                values -> glyphs(render(template, values), font);
        assertEquals(List.of("\uE001"), portraitGlyphs.apply(Map.of("mood", "calm")));
        // rank is unresolved, so rank!=vip does not hold either.
        assertEquals(List.of("\uE001"), portraitGlyphs.apply(Map.of("mood", "angry")));
        assertEquals(
                List.of("\uE002"), portraitGlyphs.apply(Map.of("mood", "angry", "rank", "member")));
        assertEquals(
                List.of("\uE001"), portraitGlyphs.apply(Map.of("mood", "angry", "rank", "vip")));
        assertEquals(
                List.of("\uE003"),
                portraitGlyphs.apply(Map.of("mood", "angry", "rank", "member", "quest", "3")));

        DialogCanvas.Skin button = TemplateSkins.get("parchment", "button");
        DialogCanvas.Skin selected = TemplateSkins.get("parchment", "selected");
        String buttonGlyph = String.valueOf((char) button.glyph());
        String selectedGlyph = String.valueOf((char) selected.glyph());
        Function<Map<String, String>, List<String>> badge =
                values ->
                        glyphs(render(template, values), TemplateSkins.font()).stream()
                                .filter(it -> it.equals(buttonGlyph) || it.equals(selectedGlyph))
                                .toList();
        assertEquals(List.of(buttonGlyph), badge.apply(Map.of("mood", "calm")));
        assertEquals(List.of(selectedGlyph), badge.apply(Map.of("mood", "calm", "rank", "vip")));
    }

    @Test
    @DisplayName("image sprites resolve by id, switch by case and fall back to text")
    void imageSpritesResolveByIdSwitchByCaseAndFallBackToText() {
        DialogTemplate template = parse();
        TemplateElement photo = single(template, "photo");
        assertEquals(new MenuImageRequest("CE", "demo:elf_calm"), photo.image());
        assertEquals(
                new MenuImageRequest("IA", "demo:elf_smile"), Kt.single(photo.cases()).image());
        assertEquals(List.of(64, 8), List.of(photo.width(), photo.rows()));
        assertNull(photo.sprite());
        List<MenuImageRequest> requests = new ArrayList<>();
        Key imageFont = Key.key("demo:images");
        RichMenuText rich =
                new RichMenuText(
                        new MenuTranslations(),
                        Locale.ENGLISH,
                        it -> {
                            requests.add(it);
                            String glyph = it.provider().equals("IA") ? "\uE102" : "\uE101";
                            return new MenuImage(Component.text(glyph).font(imageFont), 60);
                        },
                        message -> {});
        assertEquals(
                List.of("\uE101"),
                glyphs(render(template, Map.of("mood", "calm"), rich), imageFont));
        assertEquals(
                List.of("\uE102"),
                glyphs(render(template, Map.of("mood", "calm", "level", "30"), rich), imageFont));
        assertEquals(
                Set.of("CE", "IA"),
                requests.stream()
                        .map(MenuImageRequest::provider)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
        Component missing = render(template, Map.of("mood", "calm"), new RichMenuText());
        assertTrue(
                PlainTextComponentSerializer.plainText()
                        .serialize(missing)
                        .contains("[image:demo:elf_calm]"));
        assertEquals(
                "<image:CE:demo:elf_calm:0:0>",
                TemplateRenderer.imageTag(Objects.requireNonNull(photo.image())));
        assertEquals(
                "<image:IA:demo:elf_smile>",
                TemplateRenderer.imageTag(
                        Objects.requireNonNull(Kt.single(photo.cases()).image())));
    }

    @Test
    @DisplayName("display text expands built-ins, declared names and papi tokens in one pass")
    void displayTextExpandsBuiltInsDeclaredNamesAndPapiTokensInOnePass() {
        Map<String, String> lookup = Map.of("player", "Alex", "rank", "{uuid}", "uuid", "u-1");
        String text =
                TemplateRenderer.display(
                        "{player} {rank} %player_level% %other% {unknown} 50% 与 100%",
                        lookup::get, it -> it.equals("%player_level%") ? "30" : "%player_name%");
        assertEquals("Alex {uuid} 30 %player_name% {unknown} 50% 与 100%", text);
    }

    private record Invalid(String old, String replacement, String reason) {}

    @Test
    @DisplayName("invalid placeholders, conditions, cases and images are rejected")
    void invalidPlaceholdersConditionsCasesAndImagesAreRejected() {
        List<Invalid> invalid =
                List.of(
                        new Invalid(
                                "  quest: \"%quest_stage%\"",
                                "  quest: \"%quest_stage%\"\n  mood: \"%x%\"", "重名"),
                        new Invalid(
                                "  quest: \"%quest_stage%\"",
                                "  quest: \"%quest_stage%\"\n  ping: \"%x%\"", "重名"),
                        new Invalid(
                                "  level: \"%player_level%\"", "  level: player_level", "%PAPI变量%"),
                        new Invalid("  level: \"%player_level%\"", "  level: 5", "%PAPI变量%"),
                        new Invalid(
                                "VisibleWhen: \"level<30\"",
                                "VisibleWhen: \"levle<30\"",
                                "未在 Variables"),
                        new Invalid(
                                "VisibleWhen: \"level<30\"", "VisibleWhen: \"level<abc\"", "需要数字"),
                        new Invalid(
                                "VisibleWhen: \"level<30\"",
                                "VisibleWhen: \"level<30\"\n    SelectedWhen: \"mood>1\"",
                                "菜单变量只能用"),
                        new Invalid(
                                "VisibleWhen: \"level<30\"", "VisibleWhen: \"level==30\"", "一个 ="),
                        new Invalid(
                                "{player} {mood}\", \"close\"",
                                "{player} {nope}\", \"close\"",
                                "未声明"),
                        new Invalid(
                                "[\"message: 需要 30 级 {rank}\"]",
                                "[\"set: rank=vip\", \"refresh\"]",
                                "set 只能"),
                        new Invalid(
                                "    Actions: [\"message: 需要 30 级 {rank}\"]",
                                "    Actions: [\"message: 需要 30 级 {rank}\"]\n"
                                        + "    Cases:\n      - When: rank=vip\n        Sprite: selected",
                                "Cases: 仅用于 sprite"),
                        new Invalid(
                                "    Sprite: button\n    Cases:",
                                "    Sprite: button\n    SelectedWhen: rank=vip\n    Cases:",
                                "Cases 不能与"),
                        new Invalid(
                                "        Glyph: \"\\uE003\"",
                                "        Sprite: button",
                                "默认写法为 Glyph"),
                        new Invalid("        Sprite: selected", "        Sprite: close", "尺寸必须与默认"),
                        new Invalid(
                                "    Image: \"CE:demo:elf_calm\"",
                                "    Image: \"CE:demo:elf_calm\"\n    Font: \"demo:x\"",
                                "Image 不能与"),
                        new Invalid(
                                "    Text: \"{player} {rank} {mood}\"",
                                "    Text: \"{player} {rank} {mood}\"\n    Image: \"demo:x\"",
                                "Image: 仅用于 sprite"),
                        new Invalid("Image: \"CE:demo:elf_calm\"", "Image: \"only\"", "格式为"),
                        new Invalid(
                                "Image: \"IA:demo:elf_smile\"",
                                "Image: \"IA:demo:elf_smile:1:2\"",
                                "格式为"));
        for (Invalid candidate : invalid) {
            assertEquals(1, split(base, candidate.old()).size() - 1, candidate.old());
            IllegalArgumentException error =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> parse(base.replace(candidate.old(), candidate.replacement())),
                            candidate.replacement());
            assertTrue(
                    Objects.requireNonNullElse(error.getMessage(), "").contains(candidate.reason()),
                    candidate.replacement() + " -> " + error.getMessage());
        }
    }

    @Test
    @DisplayName("documented example switches buttons, badge and text by placeholder values")
    void documentedExampleSwitchesButtonsBadgeAndTextByPlaceholderValues() throws IOException {
        String source = Files.readString(Path.of("docs/wiki/examples/placeholders.yml"));
        MenuCatalog catalog =
                MenuCatalogParser.parse(
                        "Version: 3\nDefaultMenu: placeholders\n", Map.of("placeholders", source));
        DialogTemplate template = catalog.templates().get("placeholders/main");
        assertEquals(Set.of("level", "mode"), template.placeholders().keySet());
        String high = output(template, "35");
        assertTrue(high.contains("领取奖励") && high.contains("30 级认证"), high);
        assertFalse(high.contains("等级不足") || high.contains("尚未认证"));
        assertTrue(high.contains("等级 35 · 模式 未接入"));
        assertTrue(high.contains("直接写 PAPI：35"));
        String low = output(template, "3");
        assertTrue(low.contains("等级不足") && low.contains("尚未认证"));
        assertFalse(low.contains("领取奖励"));
        String missing = output(template, null);
        assertFalse(missing.contains("领取奖励") || missing.contains("等级不足"));
        assertTrue(missing.contains("你好，Alex"));
    }

    /** The {@code output(level)} local function of the documented-example test. */
    private static String output(DialogTemplate template, String level) {
        Map<String, String> context = new LinkedHashMap<>(template.values(Collections.emptyMap()));
        if (level != null) {
            context.put("level", level);
        }
        Map<String, String> lookup = new LinkedHashMap<>(context);
        lookup.put("player", "Alex");
        lookup.put("ping", "20");
        lookup.put("world", "world");
        DialogCanvas canvas =
                TemplateRenderer.render(
                        template,
                        context,
                        text ->
                                TemplateRenderer.display(
                                        text,
                                        it -> {
                                            String value = lookup.get(it);
                                            if (value != null) {
                                                return value;
                                            }
                                            return it.equals("mode") ? "未接入" : null;
                                        },
                                        it -> level != null ? level : "未接入"),
                        it -> DialogClicks.custom(Key.key("test", it)));
        return PlainTextComponentSerializer.plainText().serialize(canvas.build());
    }

    @Test
    @DisplayName("one file canvas menus accept root placeholders and page overrides")
    void oneFileCanvasMenusAcceptRootPlaceholdersAndPageOverrides() {
        String menu =
                """
                MenuType: dialog
                Version: 1
                Type: canvas
                DefaultPage: main
                Placeholders:
                  level: "%player_level%"
                Pages:
                  main:
                    Elements:
                      hint:
                        Position: [20, 2]
                        Text: "等级 {level}"
                        VisibleWhen: "level>=1"
                  other:
                    Placeholders:
                      rank: "%vault_rank%"
                    Elements:
                      hint:
                        Position: [20, 2]
                        Text: "{rank}"
                        VisibleWhen: rank=vip""";
        Map<String, String> sources = new LinkedHashMap<>();
        for (String id : Kt.plus(CatalogRepository.defaults(), List.of("settings"))) {
            sources.put(id, MenuRepository.resource("catalog/menus/" + id + ".yml"));
        }
        sources.put("npc", menu);
        MenuCatalog catalog =
                MenuCatalogParser.parse(MenuRepository.resource("catalog/config.yml"), sources);
        Map<String, DialogTemplate> pages = catalog.menus().get("npc").canvas();
        assertEquals(Map.of("level", "%player_level%"), pages.get("main").placeholders());
        assertEquals(Map.of("rank", "%vault_rank%"), pages.get("other").placeholders());
        Map<String, String> changed = new LinkedHashMap<>(sources);
        changed.put("npc", menu.replace("rank=vip", "level=vip"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MenuCatalogParser.parse(
                                MenuRepository.resource("catalog/config.yml"), changed));
    }
}
