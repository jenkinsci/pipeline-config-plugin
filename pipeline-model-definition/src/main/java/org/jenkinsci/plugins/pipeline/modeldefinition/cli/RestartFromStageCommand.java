/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.pipeline.modeldefinition.cli;

import hudson.AbortException;
import hudson.Extension;
import hudson.cli.CLICommand;
import hudson.cli.handlers.GenericItemOptionHandler;
import hudson.model.Job;
import hudson.model.Run;
import org.jenkinsci.plugins.pipeline.modeldefinition.actions.RestartDeclarativePipelineAction;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.Setter;

@Extension
public class RestartFromStageCommand extends CLICommand {
  @Option(
      required = true,
      name = "-j",
      aliases = "--job",
      metaVar = "JOB",
      usage = "Name of the job to restart.",
      handler = RestartFromStageCommand.JobHandler.class)
  public Job<?, ?> job;

  @Option(
      required = true,
      name = "-s",
      aliases = "--stage",
      metaVar = "STAGE",
      usage = "Stage to restart from.")
  public String stage;

  @Option(
      name = "-n",
      aliases = "--number",
      metaVar = "BUILD#",
      usage = "Build to restart, if not the last.")
  public int number;

  @Override
  public String getShortDescription() {
    return Messages.RestartFromStageCommand_ShortDescription();
  }

  @Override
  protected int run() throws Exception {
    Run<?, ?> run = number <= 0 ? job.getLastBuild() : job.getBuildByNumber(number);
    if (run == null) {
      throw new AbortException("No such build");
    }
    RestartDeclarativePipelineAction action = run.getAction(RestartDeclarativePipelineAction.class);
    if (action == null) {
      throw new AbortException("Not a Declarative Pipeline build");
    }
    if (!action.isRestartEnabled()) {
      throw new AbortException("Not authorized to restart builds of this job");
    }
    if (!action.getRestartableStages().contains(stage)) {
      throw new AbortException(
          "Stage "
              + stage
              + " either does not exist in the build, or did not run in the build, and so cannot be restarted.");
    }
    action.run(stage);
    return 0;
  }

  @SuppressWarnings("rawtypes")
  public static class JobHandler extends GenericItemOptionHandler<Job> {

    public JobHandler(CmdLineParser parser, OptionDef option, Setter<Job> setter) {
      super(parser, option, setter);
    }

    @Override
    protected Class<Job> type() {
      return Job.class;
    }
  }
}
