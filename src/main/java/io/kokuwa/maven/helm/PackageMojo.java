package io.kokuwa.maven.helm;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;

import io.kokuwa.maven.helm.pojo.HelmExecutable;
import lombok.Setter;

/**
 * Mojo for executing "helm package".
 *
 * @author Fabian Schlegel
 * @see <a href="https://helm.sh/docs/helm/helm_package">helm package</a>
 * @since 1.0
 */
@Mojo(name = "package", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
@Setter
public class PackageMojo extends AbstractHelmMojo {

	/**
	 * Set this to <code>true</code> to skip invoking package goal.
	 *
	 * @since 3.3
	 */
	@Parameter(property = "helm.package.skip", defaultValue = "false")
	private boolean skipPackage;

	/**
	 * Path to gpg secret keyring for signing.
	 *
	 * @since 5.10
	 */
	@Parameter(property = "helm.package.keyring")
	private String keyring;

	/**
	 * Name of gpg key in keyring.
	 *
	 * @since 5.10
	 */
	@Parameter(property = "helm.package.key")
	private String key;

	/**
	 * Passphrase for gpg key (requires helm 3.4 or newer).
	 *
	 * @since 5.10
	 */
	@Parameter(property = "helm.package.passphrase")
	private String passphrase;

	/**
	 * The version of the app. This needn't be SemVer.
	 *
	 * @since 2.8
	 */
	@Parameter(property = "helm.appVersion")
	private String appVersion;

	/**
	 * If <code>true</code> add timestamps to snapshots.
	 *
	 * @since 5.11
	 */
	@Parameter(property = "helm.chartVersion.timestampOnSnapshot", defaultValue = "false")
	private boolean timestampOnSnapshot;

	/**
	 * If "helm.chartVersion.timestampOnSnapshot" is <code>true</code> then use this format for timestamps.
	 *
	 * @since 5.11
	 */
	@Parameter(property = "helm.chartVersion.timestampFormat", defaultValue = "yyyyMMddHHmmss")
	private String timestampFormat;

	/**
	 * Location of the artifact that will be published for this module.
	 */
	@Parameter(property = "helm.mavenArtifactFile", required = true, defaultValue =
			"${project.basedir}/target/helm.placeholder.txt")
	protected File mavenArtifactFile;

	@Override
	public void execute() throws MojoExecutionException {

		if (skip || skipPackage) {
			getLog().info("Skip package");
			return;
		}

		String chartVersion = getChartVersion();
		if (chartVersion != null) {
			if (timestampOnSnapshot && chartVersion.endsWith("-SNAPSHOT")) {
				String suffix = DateTimeFormatter.ofPattern(timestampFormat).format(getTimestamp());
				chartVersion = chartVersion.replace("SNAPSHOT", suffix);
			}
			getLog().info("Setting chart version to " + chartVersion);
		}

		for (Path chartDirectory : getChartDirectories()) {
			getLog().info("Packaging chart " + chartDirectory + "...");

			HelmExecutable helm = helm()
					.arguments("package", chartDirectory)
					.flag("destination", getOutputDirectory())
					.flag("version", chartVersion)
					.flag("app-version", appVersion);

			if (StringUtils.isNotEmpty(keyring) && StringUtils.isNotEmpty(key)) {
				getLog().info("Enable signing");
				helm.flag("sign").flag("keyring", keyring).flag("key", key);
				if (StringUtils.isNotEmpty(passphrase)) {
					helm.flag("passphrase-file", "-").setStdin(passphrase);
				}
			}

			helm.execute("Unable to package chart at " + chartDirectory);
			setUpPlaceholderFileAsMavenArtifact();
		}
	}

	/**
	 * Writes a dummy file to the maven repository for this artifact, so that it can be managed
	 * as a maven dependency, even though the helm chart is not written to maven's repository
	 */
	protected void setUpPlaceholderFileAsMavenArtifact() {
		mavenArtifactFile.getParentFile().mkdirs();
		try (PrintWriter writer = new PrintWriter(mavenArtifactFile)) {
			writer.println("This is NOT the file you are looking for!");
			writer.println();
			writer.println("To take advantage of the Maven Reactor, we want to publish pom files for this artifact.");
			writer.println("But Maven isn't the right solution for managing Helm dependencies.");
			writer.println();
			writer.println(String.format("Please check your appropriate Helm repository for the %s chart instead!",
					mavenProject.getArtifactId()));

		} catch (FileNotFoundException e) {
			getLog().error("Could not create placeholder artifact file!", e);
		}

		mavenProject.getArtifact().setFile(mavenArtifactFile);
	}

	LocalDateTime getTimestamp() {
		return LocalDateTime.now(Clock.systemDefaultZone());
	}
}
