package net.kdt.pojavlaunch.downloads;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class MarkdownLiteTest {
    private static MarkdownLite.Span span(MarkdownLite.Block block, char type) {
        for (MarkdownLite.Span span : block.spans) if (span.type == type) return span;
        return null;
    }

    @Test
    public void emptyAndNullGiveNoBlocks() {
        assertTrue(MarkdownLite.parse(null).isEmpty());
        assertTrue(MarkdownLite.parse("  \n ").isEmpty());
    }

    @Test
    public void headingsListsQuotesAndRules() {
        List<MarkdownLite.Block> blocks = MarkdownLite.parse(
                "# Title\n\n## Sub\n\n#### Small\n\n- one\n  - nested\n\n1. first\n2) second\n\n> said\n\n---\n");
        assertEquals(MarkdownLite.Kind.H1, blocks.get(0).kind);
        assertEquals("Title", blocks.get(0).text);
        assertEquals(MarkdownLite.Kind.H2, blocks.get(1).kind);
        assertEquals(MarkdownLite.Kind.H3, blocks.get(2).kind);
        assertEquals(MarkdownLite.Kind.BULLET, blocks.get(3).kind);
        assertEquals(0, blocks.get(3).level);
        assertEquals(1, blocks.get(4).level);
        assertEquals(MarkdownLite.Kind.NUMBERED, blocks.get(5).kind);
        assertEquals(1, blocks.get(5).number);
        assertEquals(2, blocks.get(6).number);
        assertEquals(MarkdownLite.Kind.QUOTE, blocks.get(7).kind);
        assertEquals("said", blocks.get(7).text);
        assertEquals(MarkdownLite.Kind.RULE, blocks.get(8).kind);
    }

    @Test
    public void softLineBreaksJoinAndHardOnesStay() {
        List<MarkdownLite.Block> blocks = MarkdownLite.parse("one\ntwo\n\nthree  \nfour");
        assertEquals("one two", blocks.get(0).text);
        assertEquals("three\n four", blocks.get(1).text);
    }

    @Test
    public void boldItalicCodeStrikeAreMarkedNotKept() {
        MarkdownLite.Block block = MarkdownLite.parse("a **bold** and *it* and `code` and ~~gone~~ end").get(0);
        assertEquals("a bold and it and code and gone end", block.text);
        MarkdownLite.Span bold = span(block, 'b');
        assertEquals("bold", block.text.substring(bold.start, bold.end));
        MarkdownLite.Span italic = span(block, 'i');
        assertEquals("it", block.text.substring(italic.start, italic.end));
        MarkdownLite.Span code = span(block, 'c');
        assertEquals("code", block.text.substring(code.start, code.end));
        MarkdownLite.Span strike = span(block, 's');
        assertEquals("gone", block.text.substring(strike.start, strike.end));
    }

    @Test
    public void nestedEmphasisInsideBold() {
        MarkdownLite.Block block = MarkdownLite.parse("**very _nice_ thing**").get(0);
        assertEquals("very nice thing", block.text);
        assertEquals('b', span(block, 'b').type);
        MarkdownLite.Span italic = span(block, 'i');
        assertEquals("nice", block.text.substring(italic.start, italic.end));
    }

    @Test
    public void snakeCaseAndLoneMarksStayPlain() {
        MarkdownLite.Block block = MarkdownLite.parse("use some_file_name and 2 * 3 * 4 and **open").get(0);
        assertEquals("use some_file_name and 2 * 3 * 4 and **open", block.text);
        assertTrue(block.spans.isEmpty());
    }

    @Test
    public void linksKeepTheirLabelAndAddress() {
        MarkdownLite.Block block = MarkdownLite.parse("see [the **wiki**](https://example.com/w \"title\") now").get(0);
        assertEquals("see the wiki now", block.text);
        MarkdownLite.Span link = span(block, 'l');
        assertEquals("the wiki", block.text.substring(link.start, link.end));
        assertEquals("https://example.com/w", link.url);
    }

    @Test
    public void unsafeLinksKeepTheTextButNotTheAddress() {
        MarkdownLite.Block block = MarkdownLite.parse("[click](javascript:alert(1)) [f](file:///etc/passwd) [i](intent://x)").get(0);
        assertEquals("click f i", block.text);
        assertEquals(null, span(block, 'l'));
        assertFalse(MarkdownLite.isSafeLink("javascript:alert(1)"));
        assertFalse(MarkdownLite.isSafeLink(null));
        assertTrue(MarkdownLite.isSafeLink(" https://modrinth.com/mod/x "));
    }

    @Test
    public void imagesAndBadgeLinksDisappear() {
        List<MarkdownLite.Block> blocks = MarkdownLite.parse(
                "![logo](https://x/logo.png)\n\n[![badge](https://x/b.svg)](https://x/page)\n\nText ![inline](a.png) here");
        assertEquals(1, blocks.size());
        assertEquals("Text here".replace("  ", " "), blocks.get(0).text.replace("  ", " "));
    }

    @Test
    public void htmlIsReducedToItsText() {
        List<MarkdownLite.Block> blocks = MarkdownLite.parse(
                "<!-- hidden -->\n<h2>Features</h2>\n<p>Fast <b>and</b> light</p>\n<ul><li>one</li><li>two</li></ul>\n<a href=\"https://example.com\">Home</a><br>x\n<img src=\"a.png\">");
        assertEquals(MarkdownLite.Kind.H2, blocks.get(0).kind);
        assertEquals("Features", blocks.get(0).text);
        assertEquals("Fast and light", blocks.get(1).text);
        boolean sawBullet = false;
        boolean sawLink = false;
        for (MarkdownLite.Block block : blocks) {
            if (block.kind == MarkdownLite.Kind.BULLET) sawBullet = true;
            if (span(block, 'l') != null && "https://example.com".equals(span(block, 'l').url)) sawLink = true;
            assertFalse(block.text.contains("<"));
            assertFalse(block.text.contains("hidden"));
        }
        assertTrue(sawBullet);
        assertTrue(sawLink);
    }

    @Test
    public void fencedCodeKeepsEverythingAsIs() {
        List<MarkdownLite.Block> blocks = MarkdownLite.parse("before\n```java\nint a = **2**;\n# not a heading\n```\nafter");
        assertEquals("before", blocks.get(0).text);
        assertEquals(MarkdownLite.Kind.CODE, blocks.get(1).kind);
        assertEquals("int a = **2**;\n# not a heading", blocks.get(1).text);
        assertEquals("after", blocks.get(2).text);
    }

    @Test
    public void anUnclosedFenceStillShowsItsCode() {
        List<MarkdownLite.Block> blocks = MarkdownLite.parse("```\nline1\nline2");
        assertEquals(MarkdownLite.Kind.CODE, blocks.get(0).kind);
        assertEquals("line1\nline2", blocks.get(0).text);
    }

    @Test
    public void escapedMarksAreLiteral() {
        MarkdownLite.Block block = MarkdownLite.parse("2 \\* 3 \\* 4 and \\[x\\](y)").get(0);
        assertEquals("2 * 3 * 4 and [x](y)", block.text);
        assertTrue(block.spans.isEmpty());
    }

    @Test
    public void aHugeOddInputDoesNotBlowUp() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 3000; i++) text.append("[a](b)**x*_`[[").append(i).append("\n");
        assertTrue(MarkdownLite.parse(text.toString()).size() > 0);
    }

    @Test
    public void projectInfoReadsTheFieldsThePageNeeds() throws Exception {
        String json = "{\"id\":\"P1\",\"slug\":\"fabric-api\",\"project_type\":\"mod\",\"title\":\"Fabric API\","
                + "\"description\":\"Hooks.\",\"body\":\"# Hi\",\"icon_url\":\"https://cdn/i.png\",\"downloads\":293000000,"
                + "\"followers\":37300,\"categories\":[\"library\"],\"loaders\":[\"fabric\"],\"game_versions\":[\"1.21.4\"],"
                + "\"client_side\":\"required\",\"server_side\":\"required\",\"license\":{\"id\":\"Apache-2.0\",\"name\":\"Apache License 2.0\"},"
                + "\"source_url\":\"https://github.com/x\",\"updated\":\"2026-10-28T01:02:03Z\"}";
        ProjectInfo info = ProjectInfo.parse(json);
        assertEquals("Fabric API", info.title);
        assertEquals(293000000L, info.downloads);
        assertEquals(37300L, info.followers);
        assertEquals("Apache License 2.0", info.licenseName);
        assertEquals("https://modrinth.com/mod/fabric-api", info.webAddress());
        assertEquals("library", info.orderedTags().get(0));
        assertEquals("fabric", info.orderedTags().get(1));
        assertEquals("https://github.com/x", info.sourceUrl);
        try {
            ProjectInfo.parse("[1,2]");
            org.junit.Assert.fail("a list is not a project");
        } catch (java.io.IOException expected) {
            // fine
        }
    }

    @Test
    public void iconMathNamesAndShrinks() {
        assertEquals(40, IconMath.cacheName("https://cdn.modrinth.com/data/x/icon.png").length());
        assertEquals(IconMath.cacheName("a"), IconMath.cacheName("a"));
        assertFalse(IconMath.cacheName("a").equals(IconMath.cacheName("b")));
        assertEquals(1, IconMath.sampleSize(100, 100, 96));
        assertEquals(4, IconMath.sampleSize(1024, 1024, 200));
        assertEquals(1, IconMath.sampleSize(0, 10, 10));
        assertEquals(2, IconMath.sampleSize(400, 1000, 150));
        assertTrue(IconMath.isWebAddress("https://cdn.modrinth.com/a.png"));
        assertFalse(IconMath.isWebAddress("file:///sdcard/a.png"));
        assertFalse(IconMath.isWebAddress(null));
    }

    @Test
    public void inheritsFromIsReadFromVersionJson() {
        assertEquals("1.21.4", ProfileTarget.inheritsFromJson("{\"id\":\"fabric-loader-0.17.3-1.21.4\",\"inheritsFrom\":\"1.21.4\"}"));
        assertEquals(null, ProfileTarget.inheritsFromJson("{\"id\":\"1.21.4\"}"));
        assertEquals(null, ProfileTarget.inheritsFromJson("{\"inheritsFrom\":\"  \"}"));
        assertEquals(null, ProfileTarget.inheritsFromJson("{broken"));
        assertEquals(null, ProfileTarget.inheritsFromJson(null));
    }
}
