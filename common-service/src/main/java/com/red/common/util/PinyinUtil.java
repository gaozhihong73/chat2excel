package com.red.common.util;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

/**
 * 中文转拼音工具类
 */
public class PinyinUtil {


    public static String chineseToPinYin(String chinese) {

        StringBuilder pinyin = new StringBuilder();
        HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();

        // 设置格式
        format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        format.setVCharType(HanyuPinyinVCharType.WITH_V);

        char[] chars = chinese.toCharArray();

        for(int i =0; i< chars.length; i++) {
            char c = chars[i];

            if (Character.toString(c).matches("[\\u4E00-\\u9FA5]+")) { // 若是汉字转换为拼音
                try {
                    String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray(c, format);
                    if (pinyinArray != null && pinyinArray.length > 0) {
                        pinyin.append(pinyinArray[0]);
                        if (i < chars.length -1) {
                            pinyin.append("_");
                        }
                    }
                } catch (BadHanyuPinyinOutputFormatCombination e) {
                    throw new RuntimeException(e);
                }
            } else if (Character.isLetterOrDigit(c)) { // 判断是否是英文字母或数字
                // 1. 统一转为小写，确保生成的字段名风格一致
                pinyin.append(Character.toLowerCase(c));

                // 2. 预测下一步：看后面紧跟的是不是特殊符号
                // 如果后面还有字符，并且下一个字符是“特殊字符”
                if (i < chars.length - 1 && isSpecialChar(chars[i+1])) {
                    // 在当前字符后添加下划线，起到单词分隔的作用
                    pinyin.append("_");
                }
            }
            else if (c == '_') {
                pinyin.append("_");
            }
            else {
                pinyin.append("_");
            }
        }
        return clean(pinyin.toString());
    }


    private static  boolean isSpecialChar(char c) {
        return !Character.isLetterOrDigit(c) && c != '_';
    }

    private static String clean(String str) {
        str = str.replaceAll("^_+|_+$", "");
        str = str.replaceAll("_+", "_");
        return str;
    }
}

