package org.jenkinsci.plugins.pipeline.modeldefinition.generator;

import static org.junit.Assert.assertEquals;

import hudson.model.Describable;
import java.util.List;
import javax.annotation.Nonnull;
import jenkins.model.OptionalJobProperty;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.structs.describable.DescribableParameter;
import org.jenkinsci.plugins.workflow.cps.SnippetizerTester;
import org.jvnet.hudson.test.JenkinsRule;

public class DirectiveGeneratorTester extends SnippetizerTester {
  /** This helper requires {@link JenkinsRule}. */
  public DirectiveGeneratorTester(JenkinsRule r) {
    super(r);
  }

  /**
   * Tests a form submitting part of the generator.
   *
   * @param desc The describable we'll translate to JSON.
   * @param expected Expected directive snippet to be generated
   */
  public void assertGenerateDirective(@Nonnull AbstractDirective desc, @Nonnull String expected)
      throws Exception {
    assertEquals(expected, desc.toGroovy(true));
    assertGenerateSnippet(
        DirectiveGenerator.GENERATE_URL, describable2StaplerJson(desc).toString(), expected, null);
  }

  /** TODO: Should probably move this into structs, since it's pretty dang handy. */
  private JSONObject describable2StaplerJson(Describable d) {
    DescribableModel<?> m = DescribableModel.of(d.getClass());

    JSONObject o = new JSONObject();
    o.accumulate("stapler-class", d.getClass().getName());
    o.accumulate("$class", d.getClass().getName());
    if (d instanceof OptionalJobProperty) {
      o.accumulate("specified", true);
    }
    for (DescribableParameter param : m.getParameters()) {
      Object v = getValue(param, d);
      if (v != null) {
        if (v instanceof Describable) {
          o.accumulate(param.getName(), describable2StaplerJson((Describable) v));
        } else if (v instanceof List && !((List) v).isEmpty()) {
          JSONArray a = new JSONArray();
          for (Object obj : (List) v) {
            if (obj instanceof Describable) {
              a.add(describable2StaplerJson((Describable) obj));
            } else if (obj instanceof Number) {
              a.add(obj.toString());
            } else {
              a.add(obj);
            }
          }
          o.accumulate(param.getName(), a);
        } else if (v instanceof Number) {
          o.accumulate(param.getName(), v.toString());
        } else {
          o.accumulate(param.getName(), v);
        }
      }
    }
    return o;
  }

  private Object getValue(DescribableParameter p, Object o) {
    Class<?> ownerClass = o.getClass();
    try {
      try {
        return ownerClass.getField(p.getName()).get(o);
      } catch (NoSuchFieldException x) {
        // OK, check for getter instead
      }
      try {
        return ownerClass.getMethod("get" + p.getCapitalizedName()).invoke(o);
      } catch (NoSuchMethodException x) {
        // one more check
      }
      try {
        return ownerClass.getMethod("is" + p.getCapitalizedName()).invoke(o);
      } catch (NoSuchMethodException x) {
        throw new UnsupportedOperationException(
            "no public field ‘" + p.getName() + "’ (or getter method) found in " + ownerClass);
      }
    } catch (UnsupportedOperationException x) {
      throw x;
    } catch (Exception x) {
      throw new UnsupportedOperationException(x);
    }
  }
}
