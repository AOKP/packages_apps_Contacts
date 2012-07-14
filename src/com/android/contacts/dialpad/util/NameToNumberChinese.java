
package com.android.contacts.dialpad.util;

import com.android.contacts.util.HanziToPinyin;

/**
 * @author Barami Implementation for Korean normalization. This will change
 *         Hangul character to number by Choseong(Korean word of initial
 *         character).
 */
public class NameToNumberChinese extends NameToNumber {
    public NameToNumberChinese(String t9Chars, String t9Digits) {
        super(t9Chars, t9Digits);
        // TODO Auto-generated constructor stub
    }

    private String convertToT9(String hzPinYin) {
        StringBuilder sb = new StringBuilder(hzPinYin.length());
        int iTotal = hzPinYin.length();
        for (int i = 0; i < iTotal; i++) {
            int pos = t9Chars.indexOf(hzPinYin.charAt(i));
            if (-1 == pos) {
                pos = 0;
            }
            sb.append(t9Digits.charAt(pos));
        }

        return sb.toString();
    }

    @Override
    public String convert(String name) {
        String t9 = null;

        String hzPinYin = HanziToPinyin.getInstance().getFirstPinYin(name).toLowerCase();

        if (hzPinYin != null && !hzPinYin.isEmpty()) {
            t9 = convertToT9(hzPinYin);
            //Append the full ping yin at the end of the first ping yin
            hzPinYin = HanziToPinyin.getInstance().getFullPinYin(name).toLowerCase();
            if (hzPinYin != null && !hzPinYin.isEmpty()) {
                t9 += " " + convertToT9(hzPinYin);
            }
        } else {
            // Add English name search support
            t9 = convertToT9(name.toLowerCase());
        }

        return t9;

    }
}
