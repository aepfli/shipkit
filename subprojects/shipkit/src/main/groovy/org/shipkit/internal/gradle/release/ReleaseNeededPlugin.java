package org.shipkit.internal.gradle.release;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.shipkit.gradle.configuration.ShipkitConfiguration;
import org.shipkit.gradle.java.ComparePublicationsTask;
import org.shipkit.gradle.release.ReleaseNeededTask;
import org.shipkit.internal.gradle.configuration.ShipkitConfigurationPlugin;
import org.shipkit.internal.gradle.git.GitBranchPlugin;
import org.shipkit.internal.gradle.java.ComparePublicationsPlugin;
import org.shipkit.internal.gradle.util.TaskMaker;

/**
 * Adds tasks for checking if release is needed.
 * <br>
 * If the build is executed on a releasable branch the following criteria are checked:
 *  <ul>
 *      <li>SKIP_RELEASE env variable: no release if this environment variable is available</li>
 *      <li>[ci skip-release] commit message: no release if this commit message is available</li>
 *      <li>PR build: no release if the build job is a pull request build</li>
 *      <li>[ci skip-compare-publications] commit message: release if this commit message is available (although the previous and the current release might be identical)</li>
 *      <li>compare publications: release if previous publication is not identical to the current publication</li>
 *  </ul>
 *
 * Applies following plugins and preconfigures tasks provided by those plugins:
 *
 * <ul>
 *     <li>{@link ShipkitConfigurationPlugin}</li>
 *     <li>{@link GitBranchPlugin}</li>
 * </ul>
 *
 * Adds following tasks:
 *
 * <ul>
 *     <li>assertReleaseNeeded - {@link ReleaseNeededTask}
 *      - checks if release is needed and fails the build if not needed. Used in release process.</li>
 *     <li>releaseNeeded - {@link ReleaseNeededTask}
 *     - prints information if the release is needed. Useful for testing.</li>
 * </ul>
 */
public class ReleaseNeededPlugin implements Plugin<Project> {

    public final static String ASSERT_RELEASE_NEEDED_TASK = "assertReleaseNeeded";
    public final static String RELEASE_NEEDED = "releaseNeeded";

    @Override
    public void apply(Project project) {
        final ShipkitConfiguration conf = project.getPlugins().apply(ShipkitConfigurationPlugin.class).getConfiguration();

        //Task that throws an exception when release is not needed is very useful for CI workflows
        //Travis CI job will stop executing further commands if assertReleaseNeeded fails.
        //See the example projects how we have set up the 'assertReleaseNeeded' task in CI pipeline.
        releaseNeededTask(project, ASSERT_RELEASE_NEEDED_TASK, conf)
                .setExplosive(true)
                .setDescription("Asserts that criteria for the release are met and throws exception if release is not needed.");

        //Below task is useful for testing. It will not throw an exception but will run the code that check is release is needed
        //and it will print the information to the console.
        releaseNeededTask(project, RELEASE_NEEDED, conf)
                .setExplosive(false)
                .setDescription("Checks and prints to the console if criteria for the release are met.");
    }

    private static ReleaseNeededTask releaseNeededTask(final Project project, String taskName,
                                                       final ShipkitConfiguration conf) {
        return TaskMaker.task(project, taskName, ReleaseNeededTask.class, new Action<ReleaseNeededTask>() {
            public void execute(final ReleaseNeededTask t) {
                t.setDescription("Asserts that criteria for the release are met and throws exception if release not needed.");
                t.setExplosive(true);

                project.allprojects(new Action<Project>() {
                    public void execute(final Project subproject) {
                        subproject.getPlugins().withType(ComparePublicationsPlugin.class, new Action<ComparePublicationsPlugin>() {
                            public void execute(ComparePublicationsPlugin p) {
                                // make this task depend on all comparePublications tasks
                                ComparePublicationsTask task = (ComparePublicationsTask) subproject.getTasks().getByName(ComparePublicationsPlugin.COMPARE_PUBLICATIONS_TASK);
                                t.dependsOn(task);
                                t.getComparisonResults().add(task.getComparisonResult());
                            }
                        });
                    }
                });

                t.setReleasableBranchRegex(conf.getGit().getReleasableBranchRegex());

                project.getPlugins().apply(GitBranchPlugin.class)
                        .provideBranchTo(t, new Action<String>() {
                            public void execute(String branch) {
                                t.setBranch(branch);
                            }
                        });
            }
        });
    }
}
