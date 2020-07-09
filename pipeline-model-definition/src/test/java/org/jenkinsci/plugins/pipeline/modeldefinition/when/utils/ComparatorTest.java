package org.jenkinsci.plugins.pipeline.modeldefinition.when.utils;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ComparatorTest {

  @Test
  public void testNullComparator() {
    assertNull(Comparator.get(null, null));
    assertNull(Comparator.get("unexisting", null));
  }

  @Test
  public void testDefaultValueComparator() {
    assertTrue("glob".equalsIgnoreCase(Comparator.get(null, Comparator.GLOB).name()));
    assertTrue("regexp".equalsIgnoreCase(Comparator.get(null, Comparator.REGEXP).name()));
    assertTrue("equals".equalsIgnoreCase(Comparator.get(null, Comparator.EQUALS).name()));
  }

  @Test
  public void testGlobComparator() {
    Comparator comparator = Comparator.get("glob", null);

    assertNotNull(comparator);
    assertFalse(comparator.compare("foo*", null));
    assertFalse(comparator.compare("foo*", ""));
    assertTrue(comparator.compare("foo*", "football"));
    assertTrue(comparator.compare("foo*", "FOOtball"));
    assertFalse(comparator.compare("foo*", "Football", true));
  }

  @Test
  public void testRegexpComparator() {
    Comparator comparator = Comparator.get("regexp", null);

    assertNotNull(comparator);
    assertFalse(comparator.compare("foo.*", null));
    assertFalse(comparator.compare("foo.*", ""));
    assertTrue(comparator.compare("foo.*", "football"));
  }

  @Test
  public void testEqualsComparator() {
    Comparator comparator = Comparator.get("equals", null);

    assertNotNull(comparator);
    assertFalse(comparator.compare("foo", null));
    assertFalse(comparator.compare("foo", ""));
    assertTrue(comparator.compare("foo", "foo"));
    assertTrue(comparator.compare("foo", "FoO", false));
  }
}
