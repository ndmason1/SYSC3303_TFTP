/*
 * Config.java
 * 
 * Authors: TEAM 1
 * 
 * This file was created specifically for the course SYSC 3303. * 
 */

package tftp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import javax.naming.ConfigurationException;

import tftp.Logger.LogLevel;

public class Config {

	private static Properties prop;

	static {

		prop = new Properties();
		String propFileName = "config.properties";

		InputStream inputStream = Config.class.getClassLoader().getResourceAsStream(propFileName);

		if (inputStream != null) {
			try {
				prop.load(inputStream);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} 

	}

	public static LogLevel getLogLevel() throws ConfigurationException {
		String levelStr = prop.getProperty("log_level").toUpperCase();
		for (LogLevel level : LogLevel.values()) {
			if (level.name().equals(levelStr))
				return level;
		}

		throw new ConfigurationException("bad config value specified for log_level");
	}

	public static String getServerDirectory() {

		String str = prop.getProperty("server_dir");
		return str;
	}
	
	public static boolean getSimulateErrors() {
		return Boolean.parseBoolean(prop.getProperty("simulate_errors"));
	}

	public static void main(String[] args) {

		String dirStr = Config.getServerDirectory();
		System.out.println(dirStr);
		Path dir = Paths.get(dirStr);

		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
			for (Path entry: stream) {
				System.out.println(entry.toString());
			}
		} catch (IOException e) {
			Logger.getInstance().error("IOException in config");
			Logger.getInstance().error(e.getMessage());
		}

	}

}
