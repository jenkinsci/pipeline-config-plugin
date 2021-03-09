package org.jenkinsci.plugins.pipeline.modeldefinition.parser;

import com.cloudbees.groovy.cps.NonCPS;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.model.Run;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.GlobalVariable;
import org.jenkinsci.plugins.workflow.cps.GroovyShellDecorator;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;

/**
 * Registers the 'pipeline' step validation during Jenkinsfile parsing.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class GroovyShellDecoratorImpl extends GroovyShellDecorator {
  private static final Logger LOGGER = Logger.getLogger(GroovyShellDecoratorImpl.class.getName());

  @Override
  public GroovyShellDecorator forTrusted() {
    return this;
  }

  @Override
  public void configureCompiler(
      @CheckForNull final CpsFlowExecution execution, CompilerConfiguration cc) {
    ImportCustomizer ic = new ImportCustomizer();
    ic.addStarImports(NonCPS.class.getPackage().getName());
    ic.addStarImports("hudson.model", "jenkins.model");
    this.customizeImports(execution, ic);
    cc.addCompilationCustomizers(ic);

    cc.addCompilationCustomizers(
        new CompilationCustomizer(CompilePhase.SEMANTIC_ANALYSIS) {
          @Override
          public void call(SourceUnit source, GeneratorContext context, ClassNode classNode)
              throws CompilationFailedException {
            boolean doModelParsing = false;
            // First, we'll parse anything that's not coming from an actual source file on disk -
            // i.e., Jenkinsfiles
            // (which are read in as strings and then compiled), dynamically loaded/evaluated
            // strings, etc. We can
            // tell when we've got one of those by looking to see if the code source's location is
            // "file:/groovy/shell"
            // and if the classNode is a script.
            if (context
                    .getCompileUnit()
                    .getCodeSource()
                    .getLocation()
                    .toString()
                    .equals("file:/groovy/shell")
                && classNode.isScript()) {
              doModelParsing = true;
            } else if (execution != null && classNode.getPackageName() == null) {
              // Second, if we've got an execution and there's no package name, we'll parse if this
              // is a global
              // variable, which we can determine by looking to see if its name is in the list of
              // global variables.
              // Note that the combination of no package name plus global variable name should keep
              // us from
              // parsing src/org/whatever/Foo.groovy in shared libraries *or* global variables
              // defined in plugins.
              try {
                FlowExecutionOwner owner = execution.getOwner();
                if (owner != null && owner.getExecutable() instanceof Run) {
                  Run run = (Run) owner.getExecutable();
                  for (GlobalVariable v : GlobalVariable.forRun(run)) {
                    if (classNode.getNameWithoutPackage().equals(v.getName())) {
                      doModelParsing = true;
                    }
                  }
                }
              } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error loading WorkflowRun for execution: {0}", e);
              }
            }
            if (doModelParsing) {
              new ModelParser(source, execution).parse();
            }
          }
        });
  }
}
