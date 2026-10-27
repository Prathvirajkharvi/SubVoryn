package com.subvoryn.voryn;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Repeatable regression tests for OCR cleanup rules; no video/device required. */
public class OcrTextCleanerTest {
    @Test public void preservesShortRealDialogueAndMeaningfulPunctuation() {
        assertEquals("No!", OcrTextCleaner.clean("No!"));
        assertEquals("Oh?", OcrTextCleaner.clean("Oh?"));
        assertEquals("I", OcrTextCleaner.clean("I"));
        assertEquals("What?", OcrTextCleaner.clean("What??"));
        assertEquals("Hello world!", OcrTextCleaner.clean("Hello   world!!!"));
    }

    @Test public void removesObviousNoiseWithoutDeletingBalancedText() {
        assertEquals("", OcrTextCleaner.clean("( ) |||"));
        assertEquals("", OcrTextCleaner.clean("|||  { }  ~~"));
        assertEquals("Hello", OcrTextCleaner.clean("Hello)"));
        assertEquals("(Hello)", OcrTextCleaner.clean("(Hello)"));
        assertFalse(OcrTextCleaner.isNoiseOnly("No!"));
        assertTrue(OcrTextCleaner.isNoiseOnly("|||"));
    }

    @Test public void appliesOnlyNarrowSampleBasedJoinCorrections() {
        assertEquals("toh isse", OcrTextCleaner.clean("tohisse"));
        assertEquals("karte hain", OcrTextCleaner.clean("kartej hain"));
        assertEquals("Kitna bada", OcrTextCleaner.clean("Kitna’bada"));
        assertEquals("magic stone", OcrTextCleaner.clean("magicjstone"));
        assertEquals("hi lagbhag", OcrTextCleaner.clean("hilagbhag"));
        assertEquals("monsters", OcrTextCleaner.clean("one sters"));
    }

    @Test public void doesNotRewriteNumbersOrQuestionExclamationMeaning() {
        assertEquals("2,000,000?!", OcrTextCleaner.clean("2,000,000??!!"));
        assertEquals("Are you okay?", OcrTextCleaner.clean("Are you okay?"));
    }

    @Test public void normalizesComparisonTextDeterministically() {
        assertEquals("hello world", OcrTextCleaner.normalizedForMatch(" HELLO,   World! "));
        assertEquals("", OcrTextCleaner.normalizedForMatch("|||"));
    }
}
