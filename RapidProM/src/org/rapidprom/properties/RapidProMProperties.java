package org.rapidprom.properties;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Load RapidProM properties such as location of ProM packages etc.
 *
 * @author svzelst
 */
public class RapidProMProperties {

	public static final String IVY_OPERATING_SYSTEM_REGEX = "defaultconf=\\\"\\*\\\"";
	private static String BUILD_PROPERTIES_FILE_NAME = "/build.properties";
	private static String CONFIG_FILE_NAME = "config.properties";
	private static RapidProMProperties instance = null;
	private final Properties properties;

	private Deployment deployment = null;

	public enum Deployment {
		DEVELOPMENT, LIVE;
	}

	private RapidProMProperties() {
		properties = setup();
	}

	public static RapidProMProperties instance() {
		if (instance == null) {
			instance = new RapidProMProperties();
		}
		return instance;
	}

	private Properties setup() {
		Properties prop = new Properties();
		InputStream rpConfig = RapidProMProperties.class
				.getResourceAsStream(CONFIG_FILE_NAME);
		InputStream rpBuildProp = RapidProMProperties.class
				.getResourceAsStream(BUILD_PROPERTIES_FILE_NAME);
		try {
			prop.load(rpConfig);
			prop.load(rpBuildProp);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return prop;
	}

	public Properties getProperties() {
		return properties;
	}

	public Deployment getDeployment() {
		if (deployment == null) {
			String deploymentProp = properties
					.getProperty("rapidprom_deployment");
			if (deploymentProp != null) {
				if (deploymentProp.equals("live")) {
					deployment = Deployment.LIVE;
				} else {
					deployment = Deployment.DEVELOPMENT;
				}
			}
		}
		return deployment;
	}

	public String getRapidProMPackagesLocationString() {
		return System.getProperty(
				properties.getProperty("prom_packages_location_key"))
				+ File.separator + properties.getProperty("prom_package_dir")
				+ "-" + properties.getProperty("extension.version") + "."
				+ properties.getProperty("extension.revision") + "."
				+ properties.getProperty("extension.update");
	}

	public String getVersionsRevisionUpdate() {
		return properties.getProperty("extension.version") + "."
				+ properties.getProperty("extension.revision") + "."
				+ properties.getProperty("extension.update");
	}

	public Path getRapidProMPackagesLocationPath() {
		return Paths.get(getRapidProMPackagesLocationString());
	}

	public File getProMPackagesLocation() {
		return new File(getRapidProMPackagesLocationString() + File.separator
				+ properties.getProperty("rapidprom_organisation"));
	}

}
