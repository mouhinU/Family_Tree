package com.mouhin.family.tree.common.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

import java.util.regex.Pattern;

/**
 * 富文本 HTML 白名单清洗工具。
 * <p>
 * 论坛帖子、人物传记等富文本内容入库前必须经本工具清洗，
 * 仅保留安全标签与属性，防止存储型 XSS。
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
public final class HtmlSanitizeUtils {

    /**
     * 富文本允许的最大长度（清洗后的字符数）
     */
    public static final int MAX_RICH_TEXT_LENGTH = 20000;

    /**
     * 白名单：在 relaxed 基础上允许图片携带 src/alt，并限制图片协议
     */
    private static final Safelist RICH_TEXT_SAFELIST = buildSafelist();

    /**
     * 站内图片相对路径前缀（仅允许引用本服务上传接口产出的图片）
     */
    private static final String INTERNAL_IMAGE_PREFIX = "/api/";

    /**
     * 安全图片地址：站内 /api/ 相对路径或 http(s) 绝对地址
     */
    private static final Pattern SAFE_IMAGE_SRC = Pattern.compile("^(/api/|https?://).*");

    private HtmlSanitizeUtils() {
    }

    private static Safelist buildSafelist() {
        Safelist safelist = Safelist.relaxed();
        safelist.addAttributes("img", "alt", "width", "height");
        // relative 伪协议放行相对路径，后续再按前缀二次过滤
        safelist.addProtocols("img", "src", "http", "https", "relative");
        safelist.addProtocols("a", "href", "http", "https");
        return safelist;
    }

    /**
     * 清洗富文本 HTML：移除脚本、事件属性等非白名单内容，
     * 并过滤来源不可信的图片（仅保留站内上传与 http(s) 图片）。
     *
     * @param html 原始富文本内容
     * @return 清洗后的安全 HTML，入参为空时返回空串
     */
    public static String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String cleaned = Jsoup.clean(html, RICH_TEXT_SAFELIST);
        Document doc = Jsoup.parseBodyFragment(cleaned);
        for (Element img : doc.select("img")) {
            String src = img.attr("src");
            if (!SAFE_IMAGE_SRC.matcher(src).matches()
                    || (!src.startsWith("http") && !src.startsWith(INTERNAL_IMAGE_PREFIX))) {
                img.remove();
            }
        }
        return doc.body().html();
    }

    /**
     * 提取富文本中的纯文本（用于摘要展示）。
     *
     * @param html 富文本内容
     * @return 纯文本
     */
    public static String extractText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return Jsoup.parse(html).text();
    }
}
