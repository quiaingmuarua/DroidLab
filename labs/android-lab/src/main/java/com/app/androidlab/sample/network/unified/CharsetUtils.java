package com.app.androidlab.sample.network.unified;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 字符集工具类，用于根据 Content-Type 正确解码响应内容
 */
public class CharsetUtils {
    
    private static final Pattern CHARSET_PATTERN = Pattern.compile(
        "charset\\s*=\\s*([a-zA-Z0-9\\-_]+)",
        Pattern.CASE_INSENSITIVE
    );
    
    /**
     * 从 Content-Type 中提取字符集
     * 
     * @param contentType Content-Type 头的值，例如 "text/html; charset=UTF-8"
     * @return 字符集名称，如果未找到则返回 null
     */
    public static String extractCharset(String contentType) {
        if (contentType == null || contentType.trim().isEmpty()) {
            return null;
        }
        
        Matcher matcher = CHARSET_PATTERN.matcher(contentType);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        return null;
    }
    
    /**
     * 根据 Content-Type 获取 Charset 对象
     * 
     * @param contentType Content-Type 头的值
     * @return Charset 对象，如果无法确定则返回默认的 UTF-8
     */
    public static Charset getCharset(String contentType) {
        String charsetName = extractCharset(contentType);
        
        if (charsetName != null) {
            try {
                return Charset.forName(charsetName);
            } catch (Exception e) {
                // 如果字符集名称不支持，使用默认编码
            }
        }
        
        // 根据 Content-Type 推测默认编码
        if (contentType != null) {
            String lowerContentType = contentType.toLowerCase();
            if (lowerContentType.contains("application/json") ||
                lowerContentType.contains("application/javascript") ||
                lowerContentType.contains("application/xml") ||
                lowerContentType.contains("text/")) {
                return StandardCharsets.UTF_8;
            }
            if (lowerContentType.contains("text/html")) {
                return StandardCharsets.UTF_8; // HTML 默认 UTF-8
            }
        }
        
        // 默认使用 UTF-8
        return StandardCharsets.UTF_8;
    }
    
    /**
     * 解码响应体字节数组为字符串
     * 
     * @param responseBody 响应体字节数组
     * @param contentType Content-Type 头的值
     * @return 解码后的字符串
     */
    public static String decodeResponse(byte[] responseBody, String contentType) {
        if (responseBody == null) {
            return null;
        }
        
        if (responseBody.length == 0) {
            return "";
        }
        
        Charset charset = getCharset(contentType);
        return new String(responseBody, charset);
    }
    
    /**
     * 检查内容类型是否为文本类型
     * 
     * @param contentType Content-Type 头的值
     * @return 如果是文本类型返回 true
     */
    public static boolean isTextContent(String contentType) {
        if (contentType == null) {
            return false;
        }
        
        String lower = contentType.toLowerCase();
        return lower.startsWith("text/") ||
               lower.contains("application/json") ||
               lower.contains("application/javascript") ||
               lower.contains("application/xml") ||
               lower.contains("application/xhtml") ||
               lower.contains("application/x-www-form-urlencoded");
    }
    
    /**
     * 智能检测字节数组的编码（简单实现）
     * 
     * @param bytes 字节数组
     * @return 推测的字符集
     */
    public static Charset detectCharset(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return StandardCharsets.UTF_8;
        }
        
        // 检查 UTF-8 BOM
        if (bytes.length >= 3 &&
            bytes[0] == (byte) 0xEF &&
            bytes[1] == (byte) 0xBB &&
            bytes[2] == (byte) 0xBF) {
            return StandardCharsets.UTF_8;
        }
        
        // 检查 UTF-16 BOM
        if (bytes.length >= 2) {
            if ((bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE) ||
                (bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF)) {
                return StandardCharsets.UTF_16;
            }
        }
        
        // 简单的 UTF-8 有效性检查
        if (isValidUTF8(bytes)) {
            return StandardCharsets.UTF_8;
        }
        
        // 默认使用系统编码或 UTF-8
        return StandardCharsets.UTF_8;
    }
    
    /**
     * 简单检查字节数组是否为有效的 UTF-8 编码
     */
    private static boolean isValidUTF8(byte[] bytes) {
        try {
            String test = new String(bytes, StandardCharsets.UTF_8);
            byte[] back = test.getBytes(StandardCharsets.UTF_8);
            return bytes.length == back.length;
        } catch (Exception e) {
            return false;
        }
    }
}
