/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.contacts;

import android.content.Intent;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

/**
 * Tests for {@link ContactsUtils}.
 */
@SmallTest
public class ContactsUtilsTests extends AndroidTestCase {

    public void testIsGraphicNull() throws Exception {
        assertFalse(ContactsUtils.isGraphic(null));
    }

    public void testIsGraphicEmpty() throws Exception {
        assertFalse(ContactsUtils.isGraphic(""));
    }

    public void testIsGraphicSpaces() throws Exception {
        assertFalse(ContactsUtils.isGraphic("  "));
    }

    public void testIsGraphicPunctuation() throws Exception {
        assertTrue(ContactsUtils.isGraphic("."));
    }

    public void testAreObjectsEqual() throws Exception {
        assertTrue("null:null", ContactsUtils.areObjectsEqual(null, null));
        assertTrue("1:1", ContactsUtils.areObjectsEqual(1, 1));

        assertFalse("null:1", ContactsUtils.areObjectsEqual(null, 1));
        assertFalse("1:null", ContactsUtils.areObjectsEqual(1, null));
        assertFalse("1:2", ContactsUtils.areObjectsEqual(1, 2));
    }

    public void testShouldCollapse() throws Exception {
        assertCollapses("1", true, null, null, null, null);
        assertCollapses("2", true, "a", "b", "a", "b");

        assertCollapses("11", false, "a", null, null, null);
        assertCollapses("12", false, null, "a", null, null);
        assertCollapses("13", false, null, null, "a", null);
        assertCollapses("14", false, null, null, null, "a");

        assertCollapses("21", false, "a", "b", null, null);
        assertCollapses("22", false, "a", "b", "a", null);
        assertCollapses("23", false, "a", "b", null, "b");
        assertCollapses("24", false, "a", "b", "a", "x");
        assertCollapses("25", false, "a", "b", "x", "b");

        assertCollapses("31", false, null, null, "a", "b");
        assertCollapses("32", false, "a", null, "a", "b");
        assertCollapses("33", false, null, "b", "a", "b");
        assertCollapses("34", false, "a", "x", "a", "b");
        assertCollapses("35", false, "x", "b", "a", "b");

        assertCollapses("41", true, Phone.CONTENT_ITEM_TYPE, null, Phone.CONTENT_ITEM_TYPE,
                null);
        assertCollapses("42", true, Phone.CONTENT_ITEM_TYPE, "1", Phone.CONTENT_ITEM_TYPE, "1");

        assertCollapses("51", false, Phone.CONTENT_ITEM_TYPE, "1", Phone.CONTENT_ITEM_TYPE,
                "2");
        assertCollapses("52", false, Phone.CONTENT_ITEM_TYPE, "1", Phone.CONTENT_ITEM_TYPE,
                null);
        assertCollapses("53", false, Phone.CONTENT_ITEM_TYPE, null, Phone.CONTENT_ITEM_TYPE,
                "2");

        // Test phone numbers
        assertCollapses("60", true,
                Phone.CONTENT_ITEM_TYPE, "1234567",
                Phone.CONTENT_ITEM_TYPE, "1234567");
        assertCollapses("61", false,
                Phone.CONTENT_ITEM_TYPE, "1234567",
                Phone.CONTENT_ITEM_TYPE, "1234568");
        assertCollapses("62", true,
                Phone.CONTENT_ITEM_TYPE, "1234567;0",
                Phone.CONTENT_ITEM_TYPE, "1234567;0");
        assertCollapses("63", false,
                Phone.CONTENT_ITEM_TYPE, "1234567;89321",
                Phone.CONTENT_ITEM_TYPE, "1234567;89322");
        assertCollapses("64", true,
                Phone.CONTENT_ITEM_TYPE, "1234567;89321",
                Phone.CONTENT_ITEM_TYPE, "1234567;89321");
        assertCollapses("65", false,
                Phone.CONTENT_ITEM_TYPE, "1234567;0111111111",
                Phone.CONTENT_ITEM_TYPE, "1234567;");
        assertCollapses("66", false,
                Phone.CONTENT_ITEM_TYPE, "12345675426;91970xxxxx",
                Phone.CONTENT_ITEM_TYPE, "12345675426");
        assertCollapses("67", false,
                Phone.CONTENT_ITEM_TYPE, "12345675426;23456xxxxx",
                Phone.CONTENT_ITEM_TYPE, "12345675426;234567xxxx");
        assertCollapses("68", true,
                Phone.CONTENT_ITEM_TYPE, "1234567;1234567;1234567",
                Phone.CONTENT_ITEM_TYPE, "1234567;1234567;1234567");
        assertCollapses("69", false,
                Phone.CONTENT_ITEM_TYPE, "1234567;1234567;1234567",
                Phone.CONTENT_ITEM_TYPE, "1234567;1234567");

        // test some numbers with country and area code
        assertCollapses("70", true,
                Phone.CONTENT_ITEM_TYPE, "+49 (89) 12345678",
                Phone.CONTENT_ITEM_TYPE, "+49 (89) 12345678");
        assertCollapses("71", true,
                Phone.CONTENT_ITEM_TYPE, "+49 (89) 12345678",
                Phone.CONTENT_ITEM_TYPE, "+49 (89)12345678");
        assertCollapses("72", true,
                Phone.CONTENT_ITEM_TYPE, "+49 (8092) 1234",
                Phone.CONTENT_ITEM_TYPE, "+49 (8092)1234");
        assertCollapses("73", false,
                Phone.CONTENT_ITEM_TYPE, "0049 (8092) 1234",
                Phone.CONTENT_ITEM_TYPE, "+49/80921234");
        assertCollapses("74", false,
                Phone.CONTENT_ITEM_TYPE, "+49 (89) 12345678",
                Phone.CONTENT_ITEM_TYPE, "+49 (89) 12345679");

        // test some numbers with wait symbol and area code
        assertCollapses("80", true,
                Phone.CONTENT_ITEM_TYPE, "+49 (8092) 1234;89321",
                Phone.CONTENT_ITEM_TYPE, "+49/80921234;89321");
        assertCollapses("81", false,
                Phone.CONTENT_ITEM_TYPE, "+49 (8092) 1234;89321",
                Phone.CONTENT_ITEM_TYPE, "+49/80921235;89321");
        assertCollapses("82", false,
                Phone.CONTENT_ITEM_TYPE, "+49 (8092) 1234;89322",
                Phone.CONTENT_ITEM_TYPE, "+49/80921234;89321");
        assertCollapses("83", true,
                Phone.CONTENT_ITEM_TYPE, "1234567;+49 (8092) 1234",
                Phone.CONTENT_ITEM_TYPE, "1234567;+49/80921234");

        assertCollapses("86", true,
                Phone.CONTENT_ITEM_TYPE, "",
                Phone.CONTENT_ITEM_TYPE, "");

        assertCollapses("87", false,
                Phone.CONTENT_ITEM_TYPE, "1",
                Phone.CONTENT_ITEM_TYPE, "");

        assertCollapses("88", false,
                Phone.CONTENT_ITEM_TYPE, "",
                Phone.CONTENT_ITEM_TYPE, "1");

        assertCollapses("89", true,
                Phone.CONTENT_ITEM_TYPE, "---",
                Phone.CONTENT_ITEM_TYPE, "---");

        assertCollapses("90", true,
                Phone.CONTENT_ITEM_TYPE, "1-/().",
                Phone.CONTENT_ITEM_TYPE, "--$%1");

        assertCollapses("91", true,
                Phone.CONTENT_ITEM_TYPE, "abcdefghijklmnopqrstuvwxyz",
                Phone.CONTENT_ITEM_TYPE, "22233344455566677778889999");

        assertCollapses("92", false,
                Phone.CONTENT_ITEM_TYPE, "1;2",
                Phone.CONTENT_ITEM_TYPE, "12");

        assertCollapses("93", false,
                Phone.CONTENT_ITEM_TYPE, "1,2",
                Phone.CONTENT_ITEM_TYPE, "12");
    }

    private void assertCollapses(String message, boolean expected, CharSequence mimetype1,
            CharSequence data1, CharSequence mimetype2, CharSequence data2) {
        assertEquals(message, expected,
                ContactsUtils.shouldCollapse(mimetype1, data1, mimetype2, data2));
        assertEquals(message, expected,
                ContactsUtils.shouldCollapse(mimetype2, data2, mimetype1, data1));

        // If data1 and data2 are the same instance, make sure the same test passes with different
        // instances.
        if (data1 == data2 && data1 != null) {
            // Create a different instance
            final CharSequence data2_newref = new StringBuilder(data2).append("").toString();

            if (data1 == data2_newref) {
                // In some cases no matter what we do the runtime reuses the same instance, so
                // we can't do the "different instance" test.
                return;
            }

            // we have two different instances, now make sure we get the same result as before
            assertEquals(message, expected,
                    ContactsUtils.shouldCollapse(mimetype1, data1, mimetype2,
                    data2_newref));
            assertEquals(message, expected,
                    ContactsUtils.shouldCollapse(mimetype2, data2_newref, mimetype1,
                    data1));
        }
    }

    public void testAreIntentActionEqual() throws Exception {
        assertTrue("1", ContactsUtils.areIntentActionEqual(null, null));
        assertTrue("1", ContactsUtils.areIntentActionEqual(new Intent("a"), new Intent("a")));

        assertFalse("11", ContactsUtils.areIntentActionEqual(new Intent("a"), null));
        assertFalse("12", ContactsUtils.areIntentActionEqual(null, new Intent("a")));

        assertFalse("21", ContactsUtils.areIntentActionEqual(new Intent("a"), new Intent()));
        assertFalse("22", ContactsUtils.areIntentActionEqual(new Intent(), new Intent("b")));
        assertFalse("23", ContactsUtils.areIntentActionEqual(new Intent("a"), new Intent("b")));
    }

    public void testEqualPhoneNumbers() {
        // Identical.
        assertTrue(ContactsUtils.phoneNumbersEqual("6505555555", "6505555555"));
        assertTrue(ContactsUtils.phoneNumbersEqual("650 555 5555", "650 555 5555"));
        // Formatting.
        assertTrue(ContactsUtils.phoneNumbersEqual("6505555555", "650 555 5555"));
        assertTrue(ContactsUtils.phoneNumbersEqual("6505555555", "(650) 555-5555"));
        assertTrue(ContactsUtils.phoneNumbersEqual("650 555 5555", "(650) 555-5555"));
        // Short codes.
        assertTrue(ContactsUtils.phoneNumbersEqual("55555", "55555"));
        assertTrue(ContactsUtils.phoneNumbersEqual("55555", "555 55"));
        // Different numbers.
        assertFalse(ContactsUtils.phoneNumbersEqual("6505555555", "650555555"));
        assertFalse(ContactsUtils.phoneNumbersEqual("6505555555", "6505555551"));
        assertFalse(ContactsUtils.phoneNumbersEqual("650 555 5555", "650 555 555"));
        assertFalse(ContactsUtils.phoneNumbersEqual("650 555 5555", "650 555 5551"));
        assertFalse(ContactsUtils.phoneNumbersEqual("55555", "5555"));
        assertFalse(ContactsUtils.phoneNumbersEqual("55555", "55551"));
        // SIP addresses.
        assertTrue(ContactsUtils.phoneNumbersEqual("6505555555@host.com", "6505555555@host.com"));
        assertTrue(ContactsUtils.phoneNumbersEqual("6505555555@host.com", "6505555555@HOST.COM"));
        assertTrue(ContactsUtils.phoneNumbersEqual("user@host.com", "user@host.com"));
        assertTrue(ContactsUtils.phoneNumbersEqual("user@host.com", "user@HOST.COM"));
        assertFalse(ContactsUtils.phoneNumbersEqual("USER@host.com", "user@host.com"));
        assertFalse(ContactsUtils.phoneNumbersEqual("user@host.com", "user@host1.com"));
        // SIP address vs phone number.
        assertFalse(ContactsUtils.phoneNumbersEqual("6505555555@host.com", "6505555555"));
        assertFalse(ContactsUtils.phoneNumbersEqual("6505555555", "6505555555@host.com"));
        assertFalse(ContactsUtils.phoneNumbersEqual("user@host.com", "6505555555"));
        assertFalse(ContactsUtils.phoneNumbersEqual("6505555555", "user@host.com"));
        // Nulls.
        assertTrue(ContactsUtils.phoneNumbersEqual(null, null));
        assertFalse(ContactsUtils.phoneNumbersEqual(null, "6505555555"));
        assertFalse(ContactsUtils.phoneNumbersEqual("6505555555", null));
        assertFalse(ContactsUtils.phoneNumbersEqual(null, "6505555555@host.com"));
        assertFalse(ContactsUtils.phoneNumbersEqual("6505555555@host.com", null));
    }

    public void testCompareSipAddresses() {
        // Identical.
        assertTrue(ContactsUtils.sipAddressesEqual("6505555555@host.com", "6505555555@host.com"));
        assertTrue(ContactsUtils.sipAddressesEqual("user@host.com", "user@host.com"));
        // Host is case insensitive.
        assertTrue(ContactsUtils.sipAddressesEqual("6505555555@host.com", "6505555555@HOST.COM"));
        assertTrue(ContactsUtils.sipAddressesEqual("user@host.com", "user@HOST.COM"));
        // Userinfo is case sensitive.
        assertFalse(ContactsUtils.sipAddressesEqual("USER@host.com", "user@host.com"));
        // Different hosts.
        assertFalse(ContactsUtils.sipAddressesEqual("user@host.com", "user@host1.com"));
        // Different users.
        assertFalse(ContactsUtils.sipAddressesEqual("user1@host.com", "user@host.com"));
        // Nulls.
        assertTrue(ContactsUtils.sipAddressesEqual(null, null));
        assertFalse(ContactsUtils.sipAddressesEqual(null, "6505555555@host.com"));
        assertFalse(ContactsUtils.sipAddressesEqual("6505555555@host.com", null));
    }
}
