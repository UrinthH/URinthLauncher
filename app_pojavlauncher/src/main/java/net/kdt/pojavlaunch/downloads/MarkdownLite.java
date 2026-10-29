package net.kdt.pojavlaunch.downloads;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A small reader for the Markdown (with some HTML) that Modrinth project descriptions are written
 * in. It turns the text into blocks (headings, paragraphs, lists, quotes, code) whose words carry
 * bold / italic / code / link marks. It reads only; nothing is run or fetched. Images are dropped.
 * No Android classes, so it is covered by JVM unit tests.
 */
public final class MarkdownLite {
    private MarkdownLite() {}

    public enum Kind { H1, H2, H3, PARAGRAPH, BULLET, NUMBERED, QUOTE, CODE, RULE }

    /** A marked stretch of a block's text. {@code type}: b bold, i italic, c code, s strike, l link. */
    public static final class Span {
        public final int start, end;
        public final char type;
        public final String url;

        Span(int start, int end, char type, String url) {
            this.start = start;
            this.end = end;
            this.type = type;
            this.url = url;
        }
    }

    public static final class Block {
        public final Kind kind;
        public final String text;
        public final List<Span> spans;
        /** List nesting depth (0 = top level) for bullets and numbered items. */
        public int level;
        /** The number shown for a numbered item. */
        public int number;

        Block(Kind kind, String text, List<Span> spans) {
            this.kind = kind;
            this.text = text;
            this.spans = spans;
        }
    }

    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern HTML_LINK = Pattern.compile("<a\\s[^>]*?href\\s*=\\s*[\"']([^\"']*)[\"'][^>]*>(.*?)</a\\s*>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_IMAGE = Pattern.compile("<img\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_BREAK = Pattern.compile("<br\\s*/?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_HEADING = Pattern.compile("<h([1-6])\\b[^>]*>(.*?)</h\\1\\s*>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_LIST_ITEM = Pattern.compile("<li\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_BLOCK_END = Pattern.compile("</(p|div|ul|ol|table|tr|center|details|summary)\\s*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_ANY_TAG = Pattern.compile("</?[A-Za-z][^>]*>");
    private static final Pattern MD_IMAGE = Pattern.compile("!\\[[^\\]]*\\]\\([^)]*\\)");
    private static final Pattern HEADING = Pattern.compile("^\\s{0,3}(#{1,6})\\s+(.*?)\\s*#*\\s*$");
    private static final Pattern BULLET = Pattern.compile("^(\\s*)[-*+]\\s+(.*)$");
    private static final Pattern NUMBERED = Pattern.compile("^(\\s*)(\\d{1,4})[.)]\\s+(.*)$");
    private static final Pattern QUOTE = Pattern.compile("^\\s{0,3}>\\s?(.*)$");
    private static final Pattern RULE = Pattern.compile("^\\s{0,3}([-*_])(\\s*\\1){2,}\\s*$");
    private static final Pattern FENCE = Pattern.compile("^\\s{0,3}(```|~~~).*$");

    /** Reads the whole text. Never throws; an empty or null text gives an empty list. */
    public static List<Block> parse(String markdown) {
        List<Block> blocks = new ArrayList<>();
        if (markdown == null || markdown.trim().isEmpty()) return blocks;
        String text = simplifyHtml(markdown.replace("\r\n", "\n").replace('\r', '\n'));
        String[] lines = text.split("\n", -1);
        StringBuilder paragraph = new StringBuilder();
        boolean inFence = false;
        StringBuilder code = new StringBuilder();
        for (String line : lines) {
            if (FENCE.matcher(line).matches()) {
                if (inFence) {
                    blocks.add(new Block(Kind.CODE, trimEnd(code.toString()), new ArrayList<Span>()));
                    code.setLength(0);
                    inFence = false;
                } else {
                    flush(blocks, paragraph);
                    inFence = true;
                }
                continue;
            }
            if (inFence) {
                if (code.length() > 0) code.append('\n');
                code.append(line);
                continue;
            }
            if (line.trim().isEmpty()) {
                flush(blocks, paragraph);
                continue;
            }
            Matcher m = HEADING.matcher(line);
            if (m.matches()) {
                flush(blocks, paragraph);
                int depth = m.group(1).length();
                addInline(blocks, depth == 1 ? Kind.H1 : depth == 2 ? Kind.H2 : Kind.H3, m.group(2), 0, 0);
                continue;
            }
            if (RULE.matcher(line).matches()) {
                flush(blocks, paragraph);
                blocks.add(new Block(Kind.RULE, "", new ArrayList<Span>()));
                continue;
            }
            m = BULLET.matcher(line);
            if (m.matches()) {
                flush(blocks, paragraph);
                addInline(blocks, Kind.BULLET, m.group(2), m.group(1).length() / 2, 0);
                continue;
            }
            m = NUMBERED.matcher(line);
            if (m.matches()) {
                flush(blocks, paragraph);
                int number = 1;
                try {
                    number = Integer.parseInt(m.group(2));
                } catch (NumberFormatException ignored) {
                    // keep 1
                }
                addInline(blocks, Kind.NUMBERED, m.group(3), m.group(1).length() / 2, number);
                continue;
            }
            m = QUOTE.matcher(line);
            if (m.matches()) {
                flush(blocks, paragraph);
                addInline(blocks, Kind.QUOTE, m.group(1), 0, 0);
                continue;
            }
            // A paragraph line. A soft line break reads as a space; two spaces or a backslash at the end keep it.
            boolean hardBreak = line.endsWith("  ") || line.endsWith("\\");
            if (paragraph.length() > 0) paragraph.append(' ');
            paragraph.append(hardBreak ? trimEnd(line).replaceAll("\\\\$", "") + "\n" : line.trim());
        }
        if (inFence && code.length() > 0) blocks.add(new Block(Kind.CODE, trimEnd(code.toString()), new ArrayList<Span>()));
        flush(blocks, paragraph);
        return blocks;
    }

    private static void flush(List<Block> blocks, StringBuilder paragraph) {
        if (paragraph.length() == 0) return;
        addInline(blocks, Kind.PARAGRAPH, paragraph.toString().trim(), 0, 0);
        paragraph.setLength(0);
    }

    private static void addInline(List<Block> blocks, Kind kind, String raw, int level, int number) {
        StringBuilder out = new StringBuilder();
        List<Span> spans = new ArrayList<>();
        inline(raw, out, spans, 0);
        String plain = out.toString();
        if (plain.trim().isEmpty() && kind != Kind.RULE) return; // e.g. a line that was only an image
        Block block = new Block(kind, plain, spans);
        block.level = level;
        block.number = number;
        blocks.add(block);
    }

    /** Turns the HTML that shows up in project pages into plain Markdown, and drops the rest. */
    static String simplifyHtml(String text) {
        String result = COMMENT.matcher(text).replaceAll("");
        result = HTML_IMAGE.matcher(result).replaceAll("");
        result = MD_IMAGE.matcher(result).replaceAll("");
        Matcher link = HTML_LINK.matcher(result);
        StringBuffer withLinks = new StringBuffer();
        while (link.find()) {
            String inner = HTML_ANY_TAG.matcher(link.group(2)).replaceAll("").trim();
            link.appendReplacement(withLinks, Matcher.quoteReplacement(inner.isEmpty() ? "" : "[" + inner + "](" + link.group(1) + ")"));
        }
        link.appendTail(withLinks);
        result = withLinks.toString();
        Matcher heading = HTML_HEADING.matcher(result);
        StringBuffer withHeadings = new StringBuffer();
        while (heading.find()) {
            int depth = Integer.parseInt(heading.group(1));
            String inner = HTML_ANY_TAG.matcher(heading.group(2)).replaceAll("").trim();
            StringBuilder hashes = new StringBuilder();
            for (int i = 0; i < depth; i++) hashes.append('#');
            heading.appendReplacement(withHeadings, Matcher.quoteReplacement("\n\n" + hashes + " " + inner + "\n\n"));
        }
        heading.appendTail(withHeadings);
        result = withHeadings.toString();
        result = HTML_BREAK.matcher(result).replaceAll("  \n");
        result = HTML_LIST_ITEM.matcher(result).replaceAll("\n- ");
        result = HTML_BLOCK_END.matcher(result).replaceAll("\n\n");
        result = HTML_ANY_TAG.matcher(result).replaceAll("");
        return result.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"");
    }

    /**
     * Writes the text of {@code in} to {@code out}, without the Markdown marks, and records the
     * marked stretches. Marks that are never closed are kept as the plain characters they were.
     */
    private static void inline(String in, StringBuilder out, List<Span> spans, int depth) {
        int i = 0;
        int n = in.length();
        while (i < n) {
            char c = in.charAt(i);
            if (c == '\\' && i + 1 < n && "\\`*_{}[]()#+-.!~>|".indexOf(in.charAt(i + 1)) >= 0) {
                out.append(in.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == '`') {
                int close = in.indexOf('`', i + 1);
                if (close > i + 1) {
                    int start = out.length();
                    out.append(in, i + 1, close);
                    spans.add(new Span(start, out.length(), 'c', null));
                    i = close + 1;
                    continue;
                }
            }
            if (c == '[' && depth < 3) {
                int closeBracket = matchingBracket(in, i);
                if (closeBracket > 0 && closeBracket + 1 < n && in.charAt(closeBracket + 1) == '(') {
                    int closeParen = matchingParen(in, closeBracket + 1);
                    if (closeParen > 0) {
                        String label = in.substring(i + 1, closeBracket);
                        String url = in.substring(closeBracket + 2, closeParen).trim();
                        int space = url.indexOf(' ');
                        if (space > 0) url = url.substring(0, space); // drop an optional "title"
                        int start = out.length();
                        inline(label, out, spans, depth + 1);
                        if (out.length() > start && isSafeLink(url)) spans.add(new Span(start, out.length(), 'l', url));
                        i = closeParen + 1;
                        continue;
                    }
                }
            }
            if ((c == '*' || c == '_') && depth < 4) {
                boolean double_ = i + 1 < n && in.charAt(i + 1) == c;
                String mark = double_ ? "" + c + c : "" + c;
                int contentStart = i + mark.length();
                if (canOpen(in, i, mark.length(), c)) {
                    int close = findClose(in, contentStart, mark, c);
                    if (close > contentStart) {
                        int start = out.length();
                        inline(in.substring(contentStart, close), out, spans, depth + 1);
                        spans.add(new Span(start, out.length(), double_ ? 'b' : 'i', null));
                        i = close + mark.length();
                        continue;
                    }
                }
            }
            if (c == '~' && i + 1 < n && in.charAt(i + 1) == '~') {
                int close = in.indexOf("~~", i + 2);
                if (close > i + 2) {
                    int start = out.length();
                    inline(in.substring(i + 2, close), out, spans, depth + 1);
                    spans.add(new Span(start, out.length(), 's', null));
                    i = close + 2;
                    continue;
                }
            }
            out.append(c);
            i++;
        }
    }

    private static boolean canOpen(String in, int at, int markLength, char c) {
        int after = at + markLength;
        if (after >= in.length() || Character.isWhitespace(in.charAt(after))) return false;
        if (c == '_' && at > 0 && Character.isLetterOrDigit(in.charAt(at - 1))) return false; // snake_case
        return true;
    }

    private static int findClose(String in, int from, String mark, char c) {
        int index = from;
        while (true) {
            index = in.indexOf(mark, index);
            if (index < 0) return -1;
            boolean previousOk = index > from && !Character.isWhitespace(in.charAt(index - 1));
            int after = index + mark.length();
            boolean nextOk = c != '_' || after >= in.length() || !Character.isLetterOrDigit(in.charAt(after));
            // a single mark must not be the first half of a double one
            boolean notDouble = mark.length() == 2 || after >= in.length() || in.charAt(after) != c;
            if (previousOk && nextOk && notDouble) return index;
            index += 1;
        }
    }

    private static int matchingBracket(String in, int open) {
        int depth = 0;
        for (int i = open; i < in.length(); i++) {
            char c = in.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    /** The ")" that closes the "(" at {@code open}, counting nested pairs (addresses may contain them). */
    private static int matchingParen(String in, int open) {
        int depth = 0;
        for (int i = open; i < in.length(); i++) {
            char c = in.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    /** Links are only followed when they are web addresses (no javascript:, file:, intent: and so on). */
    public static boolean isSafeLink(String url) {
        if (url == null) return false;
        String lower = url.trim().toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://");
    }

    private static String trimEnd(String text) {
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) end--;
        return text.substring(0, end);
    }
}
