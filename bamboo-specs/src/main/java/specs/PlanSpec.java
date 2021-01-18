package specs;

import com.atlassian.bamboo.specs.api.BambooSpec;
import com.atlassian.bamboo.specs.api.builders.BambooKey;
import com.atlassian.bamboo.specs.api.builders.BambooOid;
import com.atlassian.bamboo.specs.api.builders.permission.PermissionType;
import com.atlassian.bamboo.specs.api.builders.permission.Permissions;
import com.atlassian.bamboo.specs.api.builders.permission.PlanPermissions;
import com.atlassian.bamboo.specs.api.builders.plan.Job;
import com.atlassian.bamboo.specs.api.builders.plan.Plan;
import com.atlassian.bamboo.specs.api.builders.plan.PlanBranchIdentifier;
import com.atlassian.bamboo.specs.api.builders.plan.PlanIdentifier;
import com.atlassian.bamboo.specs.api.builders.plan.Stage;
import com.atlassian.bamboo.specs.api.builders.plan.artifact.Artifact;
import com.atlassian.bamboo.specs.api.builders.plan.branches.BranchCleanup;
import com.atlassian.bamboo.specs.api.builders.plan.branches.BranchIntegration;
import com.atlassian.bamboo.specs.api.builders.plan.branches.PlanBranchManagement;
import com.atlassian.bamboo.specs.api.builders.plan.configuration.AllOtherPluginsConfiguration;
import com.atlassian.bamboo.specs.api.builders.plan.configuration.ConcurrentBuilds;
import com.atlassian.bamboo.specs.api.builders.project.Project;
import com.atlassian.bamboo.specs.builders.task.ArtifactDownloaderTask;
import com.atlassian.bamboo.specs.builders.task.CheckoutItem;
import com.atlassian.bamboo.specs.builders.task.DownloadItem;
import com.atlassian.bamboo.specs.builders.task.MavenTask;
import com.atlassian.bamboo.specs.builders.task.ScriptTask;
import com.atlassian.bamboo.specs.builders.task.TestParserTask;
import com.atlassian.bamboo.specs.builders.task.VcsCheckoutTask;
import com.atlassian.bamboo.specs.builders.trigger.RepositoryPollingTrigger;
import com.atlassian.bamboo.specs.model.task.TestParserTaskProperties;
import com.atlassian.bamboo.specs.util.BambooServer;
import com.atlassian.bamboo.specs.util.MapBuilder;

@BambooSpec
public class PlanSpec {

    public Plan plan() {
        final Plan plan = new Plan(new Project()
                .oid(new BambooOid("1gmsj9psyqi2p"))
                .key(new BambooKey("RXDEM"))
                .name("RxDemo"),
                "RxDemo Server Copy",
                new BambooKey("COPY"))
                .description("Main build from trunk")
                .pluginConfigurations(new ConcurrentBuilds())
                .stages(new Stage("Checkout and Build")
                                .jobs(new Job("Maven clean install",
                                        new BambooKey("JOB1"))
                                        .pluginConfigurations(new AllOtherPluginsConfiguration()
                                                .configuration(new MapBuilder()
                                                        .put("custom", new MapBuilder()
                                                                .put("auto", new MapBuilder()
                                                                        .put("label", "")
                                                                        .put("regex", "")
                                                                        .build())
                                                                .put("clover.path", "")
                                                                .put("buildHangingConfig.enabled", "false")
                                                                .put("ncover.path", "")
                                                                .build())
                                                        .build()))
                                        .artifacts(new Artifact()
                                                        .name("Uber jar for testing")
                                                        .copyPattern("rxdemo-server-jar-with-dependencies.jar")
                                                        .location("rxdemo-server/core/target")
                                                        .shared(true)
                                                        .required(true),
                                                new Artifact()
                                                        .name("War for deployment")
                                                        .copyPattern("rxdemo-api.war")
                                                        .location("rxdemo-server/rest/target")
                                                        .shared(true)
                                                        .required(true))
                                        .tasks(new VcsCheckoutTask()
                                                        .description("Checkout Default Repository")
                                                        .checkoutItems(new CheckoutItem().defaultRepository()),
                                                new MavenTask()
                                                        .description("Maven build")
                                                        .goal("clean install")
                                                        .jdk("JDK 1.8")
                                                        .executableLabel("Maven 3")
                                                        .hasTests(true)
                                                        .workingSubdirectory("rxdemo-server"))
                                        .cleanWorkingDirectory(true)),
                        new Stage("Verify")
                                .jobs(
                                        new Job("Fitnesse",
                                                new BambooKey("FIT"))
                                                .description("Run Fitnesse acceptance tests")
                                                .tasks(new ArtifactDownloaderTask()
                                                                .description("Download source jar")
                                                                .artifacts(new DownloadItem()
                                                                        .artifact("Uber jar for testing")),
                                                        new ScriptTask()
                                                                .description("Copy source jar to Fitnesse")
                                                                .inlineBody("cp -f *.jar /Users/jimweaver/ps-course/testpyramidexample/rxdemo-fitnesse/lib"),
                                                        new ScriptTask()
                                                                .description("Run fitnesse tests")
                                                                .inlineBody("curl \"http://localhost:8080/RxDemoTestPyramidTests?suite&format=junit\" > results.xml"),
                                                        new TestParserTask(TestParserTaskProperties.TestType.JUNIT)
                                                                .description("Parse test results")
                                                                .resultDirectories("**/**.xml"))))
                .linkedRepositories("RxDemo Github")

                .triggers(new RepositoryPollingTrigger())
                .planBranchManagement(new PlanBranchManagement()
                        .delete(new BranchCleanup()
                                .whenRemovedFromRepositoryAfterDays(7)
                                .whenInactiveInRepositoryAfterDays(30))
                        .notificationForCommitters()
                        .branchIntegration(new BranchIntegration()
                                .integrationBranch(new PlanBranchIdentifier(new BambooKey("MAIN"))
                                        .oid(new BambooOid("1gmiu24fqwpoh")))
                                .pushOnSuccessfulBuild(true))
                        .issueLinkingEnabled(false));
        return plan;
    }

    public PlanPermissions planPermission() {
        final PlanPermissions planPermission = new PlanPermissions(new PlanIdentifier("RXDEM", "COPY"))
                .permissions(new Permissions()
                        .userPermissions("jimweaver", PermissionType.EDIT, PermissionType.VIEW, PermissionType.ADMIN, PermissionType.CLONE, PermissionType.BUILD)
                        .loggedInUserPermissions(PermissionType.VIEW)
                        .anonymousUserPermissionView());
        return planPermission;
    }

    public static void main(String... argv) {
        //By default credentials are read from the '.credentials' file.
        BambooServer bambooServer = new BambooServer("http://localhost:8085");
        final PlanSpec planSpec = new PlanSpec();

        final Plan plan = planSpec.plan();
        bambooServer.publish(plan);

        final PlanPermissions planPermission = planSpec.planPermission();
        bambooServer.publish(planPermission);
    }
}